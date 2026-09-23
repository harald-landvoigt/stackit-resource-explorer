package com.landvoigtit.stackit.resourceexplorer.iam;

import cloud.stackit.sdk.objectstorage.v2api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v2api.model.AccessKey;
import cloud.stackit.sdk.objectstorage.v2api.model.CredentialsGroup;
import cloud.stackit.sdk.objectstorage.v2api.model.ListAccessKeysResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.ListCredentialsGroupsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Member;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class IamResourceScraper {

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Inject
    OkHttpClient httpClient;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    StackitSdkConfig sdkConfig;

    @Inject
    ObjectStorageApi objectStorageApi;

    @Scheduled(every = "${stackit.iam.schedule:off}")
    public void scrape() {
        log.info("Starting IAM resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("IAM resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectIam(projectIdStr, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_IAM, projectIdStr, currentResourceIds);
                }
            }
            log.info("Finished IAM resource scrape successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape IAM resources", e);
        }
    }

    private boolean scrapeProjectIam(final String projectIdStr, final List<String> currentResourceIds) {
        log.info("Scraping IAM resources for project {}", projectIdStr);
        final Map<String, ServiceAccountAuthInfo> saAuthMap = new HashMap<>();
        final boolean sasSuccess = scrapeProjectServiceAccounts(projectIdStr, currentResourceIds, saAuthMap);
        final boolean membersSuccess = scrapeProjectMembers(projectIdStr, currentResourceIds, saAuthMap);
        final boolean s3KeysSuccess = scrapeProjectS3AccessKeys(projectIdStr, currentResourceIds);
        return sasSuccess && membersSuccess && s3KeysSuccess;
    }

    private boolean scrapeProjectS3AccessKeys(final String projectIdStr, final List<String> currentResourceIds) {
        if (objectStorageApi == null) {
            return true;
        }
        final List<String> regions = sdkConfig != null && sdkConfig.getRegions() != null && !sdkConfig.getRegions().isEmpty()
                ? sdkConfig.getRegions()
                : StackitConstants.DEFAULT_REGIONS;

        log.info("Scraping S3 access keys for project {} across regions: {}", projectIdStr, regions);

        boolean allSucceeded = true;
        for (final String region : regions) {
            if (!scrapeRegionS3AccessKeys(projectIdStr, region, currentResourceIds)) {
                allSucceeded = false;
            }
        }
        return allSucceeded;
    }

    private boolean scrapeRegionS3AccessKeys(
            final String projectIdStr,
            final String region,
            final List<String> currentResourceIds) {
        log.info("Scraping S3 credentials groups and access keys for project {} in region {}", projectIdStr, region);
        try {
            final ListCredentialsGroupsResponse groupsResp = objectStorageApi.listCredentialsGroups(projectIdStr, region);
            if (groupsResp == null || groupsResp.getCredentialsGroups() == null || groupsResp.getCredentialsGroups().isEmpty()) {
                return true;
            }
            for (final CredentialsGroup group : groupsResp.getCredentialsGroups()) {
                processCredentialsGroupS3Keys(projectIdStr, region, group, currentResourceIds);
            }
            return true;
        } catch (final Exception e) {
            return handleS3CredentialsGroupScrapeError(projectIdStr, region, e);
        }
    }

    private void processCredentialsGroupS3Keys(
            final String projectIdStr,
            final String region,
            final CredentialsGroup group,
            final List<String> currentResourceIds) {
        final String groupName = group.getDisplayName() != null ? group.getDisplayName() : "";
        if (isAuditCredentialsGroup(groupName)) {
            return;
        }
        final String groupId = group.getCredentialsGroupId();
        if (groupId == null || groupId.isBlank()) {
            return;
        }

        try {
            final ListAccessKeysResponse keysResp = objectStorageApi.listAccessKeys(projectIdStr, region, groupId);
            if (keysResp == null || keysResp.getAccessKeys() == null || keysResp.getAccessKeys().isEmpty()) {
                return;
            }
            log.info("Discovered {} S3 access key(s) in project {} region {} for credentials group '{}'", keysResp.getAccessKeys().size(), projectIdStr, region, groupName);
            for (final AccessKey key : keysResp.getAccessKeys()) {
                processSingleS3AccessKey(projectIdStr, region, group, key, currentResourceIds);
            }
        } catch (final Exception e) {
            final String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isPermissionIssue(msg)) {
                log.warn("Permission denied listing S3 access keys for group {} in project {} region {}: {}", groupId, projectIdStr, region, msg);
            } else {
                log.info("Could not list access keys for group {} in project {} region {}: {}", groupId, projectIdStr, region, msg);
            }
        }
    }

    private boolean isAuditCredentialsGroup(final String groupName) {
        return groupName.equalsIgnoreCase("resource-explorer-audit") || groupName.contains("resource-explorer-audit");
    }

    private void processSingleS3AccessKey(
            final String projectIdStr,
            final String region,
            final CredentialsGroup group,
            final AccessKey key,
            final List<String> currentResourceIds) {
        if (key.getKeyId() == null || key.getKeyId().isBlank()) {
            return;
        }
        final StackitEntity entity = IamResourceMapper.mapS3KeyToEntity(projectIdStr, region, group, key);
        if (entity != null) {
            repository.persistOrUpdate(entity);
            currentResourceIds.add(entity.getResourceId());
        }
    }

    private boolean handleS3CredentialsGroupScrapeError(
            final String projectIdStr,
            final String region,
            final Exception e) {
        final String msg = e.getMessage() != null ? e.getMessage() : "";
        if (isPermissionIssue(msg)) {
            log.warn("Permission denied accessing S3 credentials groups for project {} in region {}: {}", projectIdStr, region, msg);
            return true;
        } else if (msg.contains("404") || msg.contains("not_found")) {
            log.info("Object storage not enabled for project {} in region {}: {}", projectIdStr, region, msg);
            return true;
        } else {
            log.warn("Failed to scrape S3 credentials groups for project {} in region {}: {}", projectIdStr, region, e.getMessage());
            return false;
        }
    }

    private boolean scrapeProjectMembers(
            final String projectIdStr,
            final List<String> currentResourceIds,
            final Map<String, ServiceAccountAuthInfo> saAuthMap) {
        log.info("Scraping IAM members for project {}", projectIdStr);
        final List<MemberJsonDto> members = fetchProjectMembers(projectIdStr);
        if (members == null) {
            return false;
        }

        for (final MemberJsonDto jsonDto : members) {
            processMember(projectIdStr, jsonDto, saAuthMap, currentResourceIds);
        }
        return true;
    }

    private List<MemberJsonDto> fetchProjectMembers(final String projectIdStr) {
        final String url = sdkConfig != null
                ? sdkConfig.getMembersUrl(projectIdStr)
                : StackitConstants.formatMembersUrl(projectIdStr);
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (final Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String errorBody = response.body() != null ? response.body().string() : "null";
                log.warn("Failed to fetch IAM members for project {}: HTTP {} - {}", projectIdStr, response.code(), errorBody);
                return null;
            }

            if (response.body() == null) {
                log.warn("Empty response body from IAM API for project {}", projectIdStr);
                return null;
            }

            final String bodyString = response.body().string();
            final MembersResponse membersResponse = objectMapper.readValue(bodyString, MembersResponse.class);
            return membersResponse != null && membersResponse.members != null
                    ? membersResponse.members
                    : Collections.emptyList();
        } catch (final Exception e) {
            log.warn("Failed to scrape IAM resources for project {}: {}", projectIdStr, e.getMessage());
            return null;
        }
    }

    private void processMember(
            final String projectIdStr,
            final MemberJsonDto jsonDto,
            final Map<String, ServiceAccountAuthInfo> saAuthMap,
            final List<String> currentResourceIds) {
        if (jsonDto.subject == null) {
            return;
        }
        final Member member = new Member();
        member.setSubject(jsonDto.subject);
        member.setRole(jsonDto.role);

        final IamResourceDto dto = IamResourceMapper.mapToDto(member);
        if (!validator.validate(dto).isEmpty()) {
            log.warn("Invalid IAM resource DTO: {}", dto.getMemberId());
            return;
        }

        final StackitEntity entity = IamResourceMapper.mapToEntity(dto);
        entity.setProjectId(projectIdStr);

        enrichMemberEntity(entity, jsonDto, saAuthMap);

        repository.persistOrUpdate(entity);
        currentResourceIds.add(entity.getResourceId());
    }

    private void enrichMemberEntity(
            final StackitEntity entity,
            final MemberJsonDto jsonDto,
            final Map<String, ServiceAccountAuthInfo> saAuthMap) {
        final Map<String, Object> memberData = new HashMap<>();
        memberData.put("role", jsonDto.role != null ? jsonDto.role : "");
        final Map<String, String> memberTags = new HashMap<>();

        if (saAuthMap.containsKey(jsonDto.subject)) {
            applyKnownServiceAccountAuth(saAuthMap.get(jsonDto.subject), memberData, memberTags);
        } else {
            classifyUnmappedSubjectAuth(jsonDto.subject, memberData, memberTags);
        }

        entity.setData(memberData);
        if (!memberTags.isEmpty()) {
            entity.setTags(memberTags);
        }
    }

    private void applyKnownServiceAccountAuth(
            final ServiceAccountAuthInfo saInfo,
            final Map<String, Object> memberData,
            final Map<String, String> memberTags) {
        memberData.put("identityType", saInfo.identityType);
        memberData.put("authScheme", saInfo.authScheme);
        memberData.put("authFlow", saInfo.authFlow);
        if (saInfo.deprecated) {
            memberData.put("deprecated", true);
            if (saInfo.legacyModel != null) {
                memberData.put("legacyModel", saInfo.legacyModel);
            }
            memberData.put("staticTokenCount", saInfo.tokenCount);
            memberTags.put("deprecated", "true");
            memberTags.put("auth-flow", "token-flow-deprecated");
        }
        memberTags.put("auth-scheme", saInfo.authScheme);
    }

    private void classifyUnmappedSubjectAuth(
            final String subject,
            final Map<String, Object> memberData,
            final Map<String, String> memberTags) {
        if (isServiceAccountSubject(subject)) {
            memberData.put("identityType", "Service Account");
            memberData.put("authScheme", StackitConstants.AUTH_FLOW_KEY_FLOW);
            memberData.put("authFlow", StackitConstants.AUTH_FLOW_KEY_FLOW);
            memberTags.put("auth-scheme", StackitConstants.AUTH_FLOW_KEY_FLOW);
        } else if (subject.contains("@")) {
            memberData.put("identityType", "User (Human)");
            memberData.put("authScheme", StackitConstants.AUTH_FLOW_OIDC);
            memberData.put("authFlow", "OIDC / SSO");
            final String domain = subject.substring(subject.indexOf('@') + 1);
            memberData.put("idpDomain", domain);
            memberTags.put("auth-scheme", StackitConstants.AUTH_FLOW_OIDC);
        } else if (subject.startsWith("group:")) {
            memberData.put("identityType", "Group");
            memberData.put("authScheme", "IdP Group Claim");
            memberData.put("authFlow", "IdP Group");
            memberTags.put("auth-scheme", "IdP Group");
        } else {
            memberData.put("identityType", "Identity");
            memberData.put("authScheme", "Standard");
        }
    }

    private boolean isServiceAccountSubject(final String subject) {
        return subject.endsWith("@service-account.stackit.cloud") || subject.contains("service-account");
    }

    private boolean scrapeProjectServiceAccounts(
            final String projectIdStr,
            final List<String> currentResourceIds,
            final Map<String, ServiceAccountAuthInfo> saAuthMap) {
        log.info("Scraping service accounts for project {}", projectIdStr);
        final List<ServiceAccountJsonDto> serviceAccounts = fetchProjectServiceAccounts(projectIdStr);
        if (serviceAccounts == null) {
            return false;
        }

        for (final ServiceAccountJsonDto saDto : serviceAccounts) {
            processServiceAccount(projectIdStr, saDto, saAuthMap, currentResourceIds);
        }
        return true;
    }

    private List<ServiceAccountJsonDto> fetchProjectServiceAccounts(final String projectIdStr) {
        final String url = sdkConfig != null
                ? sdkConfig.getServiceAccountsUrl(projectIdStr)
                : StackitConstants.formatServiceAccountsUrl(projectIdStr);
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (final Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String errorBody = response.body() != null ? response.body().string() : "null";
                log.warn("Failed to fetch service accounts for project {}: HTTP {} - {}", projectIdStr, response.code(), errorBody);
                return null;
            }

            if (response.body() == null) {
                log.warn("Empty response body from service accounts API for project {}", projectIdStr);
                return null;
            }

            final String bodyString = response.body().string();
            final ServiceAccountsResponse sasResponse = objectMapper.readValue(bodyString, ServiceAccountsResponse.class);
            return sasResponse != null && sasResponse.items != null
                    ? sasResponse.items
                    : Collections.emptyList();
        } catch (final Exception e) {
            log.warn("Failed to scrape service accounts for project {}: {}", projectIdStr, e.getMessage());
            return null;
        }
    }

    private void processServiceAccount(
            final String projectIdStr,
            final ServiceAccountJsonDto saDto,
            final Map<String, ServiceAccountAuthInfo> saAuthMap,
            final List<String> currentResourceIds) {
        if (saDto.email == null) {
            return;
        }
        final Member member = new Member();
        member.setSubject(saDto.email);
        member.setRole(StackitConstants.ROLE_SERVICE_ACCOUNT);

        final IamResourceDto dto = IamResourceMapper.mapToDto(member);
        if (!validator.validate(dto).isEmpty()) {
            log.warn("Invalid Service Account DTO: {}", dto.getMemberId());
            return;
        }

        final StackitEntity entity = IamResourceMapper.mapToEntity(dto);
        entity.setProjectId(projectIdStr);

        final ServiceAccountAuthInfo authInfo = resolveServiceAccountAuth(projectIdStr, saDto);
        enrichServiceAccountEntity(entity, saDto, authInfo);

        repository.persistOrUpdate(entity);
        currentResourceIds.add(entity.getResourceId());
        saAuthMap.put(saDto.email, authInfo);
    }

    private ServiceAccountAuthInfo resolveServiceAccountAuth(
            final String projectIdStr,
            final ServiceAccountJsonDto saDto) {
        if (saDto.internal) {
            return new ServiceAccountAuthInfo(
                    StackitConstants.AUTH_FLOW_PLATFORM_MANAGED,
                    "Platform Managed",
                    false,
                    null,
                    "Managed Service Identity",
                    0,
                    0,
                    null,
                    null
            );
        }
        return inspectExternalServiceAccountAuth(projectIdStr, saDto);
    }

    private ServiceAccountAuthInfo inspectExternalServiceAccountAuth(
            final String projectIdStr,
            final ServiceAccountJsonDto saDto) {
        final int tokenCount = saDto.id != null ? fetchServiceAccountTokenCount(projectIdStr, saDto.id) : 0;
        final KeyInspectionResult keyResult = fetchServiceAccountKeyInfo(projectIdStr, saDto);

        if (tokenCount > 0) {
            final String authScheme = keyResult.count > 0
                    ? "Hybrid (Key Flow & Token Flow Deprecated)"
                    : StackitConstants.AUTH_FLOW_TOKEN_DEPRECATED;
            return new ServiceAccountAuthInfo(
                    authScheme,
                    StackitConstants.AUTH_FLOW_TOKEN_DEPRECATED,
                    true,
                    StackitConstants.TOKEN_FLOW_DEPRECATED_DESCRIPTION,
                    "Service Account",
                    tokenCount,
                    keyResult.count,
                    keyResult.algorithm,
                    keyResult.validUntil
            );
        }

        final String authScheme = keyResult.count > 0 && keyResult.algorithm != null
                ? "Key Flow (" + keyResult.algorithm + ")"
                : StackitConstants.AUTH_FLOW_KEY_FLOW;
        return new ServiceAccountAuthInfo(
                authScheme,
                StackitConstants.AUTH_FLOW_KEY_FLOW,
                false,
                null,
                "Service Account",
                0,
                keyResult.count,
                keyResult.algorithm,
                keyResult.validUntil
        );
    }

    private int fetchServiceAccountTokenCount(final String projectIdStr, final String saId) {
        final String tokensUrl = sdkConfig != null
                ? sdkConfig.getServiceAccountTokensUrl(projectIdStr, saId)
                : StackitConstants.formatServiceAccountTokensUrl(StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL, projectIdStr, saId);
        final Request tokensReq = new Request.Builder().url(tokensUrl).get().build();

        try (final Response tokensResp = httpClient.newCall(tokensReq).execute()) {
            if (tokensResp.isSuccessful() && tokensResp.body() != null) {
                final String tokensBody = tokensResp.body().string();
                final TokensResponse tokensObj = objectMapper.readValue(tokensBody, TokensResponse.class);
                if (tokensObj != null && tokensObj.items != null) {
                    return tokensObj.items.size();
                }
            }
        } catch (final Exception e) {
            final String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isPermissionIssue(msg)) {
                log.warn("Permission denied checking tokens for SA {}: {}", saId, msg);
            } else {
                log.info("Tokens check skipped or failed for SA {}: {}", saId, msg);
            }
        }
        return 0;
    }

    private KeyInspectionResult fetchServiceAccountKeyInfo(final String projectIdStr, final ServiceAccountJsonDto saDto) {
        if (saDto.id == null) {
            return new KeyInspectionResult(0, null, null);
        }

        final KeyInspectionResult remoteResult = queryRemoteServiceAccountKeys(projectIdStr, saDto);
        if (remoteResult.count > 0) {
            return remoteResult;
        }

        return checkLocalScraperKeyFallback(saDto);
    }

    private KeyInspectionResult queryRemoteServiceAccountKeys(final String projectIdStr, final ServiceAccountJsonDto saDto) {
        final String targetKeyId = saDto.email != null ? saDto.email : saDto.id;
        final String keysUrl = sdkConfig != null
                ? sdkConfig.getServiceAccountKeysUrl(projectIdStr, targetKeyId)
                : StackitConstants.formatServiceAccountKeysUrl(StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL, projectIdStr, targetKeyId);
        final Request keysReq = new Request.Builder().url(keysUrl).get().build();

        try (final Response keysResp = httpClient.newCall(keysReq).execute()) {
            if (!keysResp.isSuccessful() || keysResp.body() == null) {
                if (keysResp.code() == 403 || keysResp.code() == 401) {
                    log.warn("Permission denied checking keys for SA {} (HTTP {}). Key auditing permission may be needed.", saDto.email, keysResp.code());
                } else {
                    log.info("Keys check returned HTTP {} for SA {}.", keysResp.code(), saDto.email);
                }
                return new KeyInspectionResult(0, null, null);
            }

            final String keysBody = keysResp.body().string();
            final KeysResponse keysObj = objectMapper.readValue(keysBody, KeysResponse.class);
            if (keysObj != null && keysObj.items != null && !keysObj.items.isEmpty()) {
                final KeyJsonDto firstKey = keysObj.items.get(0);
                return new KeyInspectionResult(keysObj.items.size(), firstKey.keyAlgorithm, firstKey.validUntil);
            }
        } catch (final Exception e) {
            final String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isPermissionIssue(msg)) {
                log.warn("Permission denied checking keys for SA {}: {}", saDto.id, msg);
            } else {
                log.info("Keys check skipped or failed for SA {}: {}", saDto.id, msg);
            }
        }
        return new KeyInspectionResult(0, null, null);
    }

    private KeyInspectionResult checkLocalScraperKeyFallback(final ServiceAccountJsonDto saDto) {
        if (sdkConfig == null) {
            return new KeyInspectionResult(0, null, null);
        }
        final String localEmail = sdkConfig.getServiceAccountEmail();
        final String localSub = sdkConfig.getServiceAccountId();
        final boolean matchesEmail = localEmail != null && localEmail.equalsIgnoreCase(saDto.email);
        final boolean matchesId = localSub != null && localSub.equals(saDto.id);

        if (matchesEmail || matchesId) {
            final String algorithm = sdkConfig.getServiceAccountKeyAlgorithm() != null
                    ? sdkConfig.getServiceAccountKeyAlgorithm()
                    : "RSA_2048";
            return new KeyInspectionResult(1, algorithm, null);
        }
        return new KeyInspectionResult(0, null, null);
    }

    private void enrichServiceAccountEntity(
            final StackitEntity entity,
            final ServiceAccountJsonDto saDto,
            final ServiceAccountAuthInfo authInfo) {
        final Map<String, Object> enrichedData = new HashMap<>();
        enrichedData.put("role", StackitConstants.ROLE_SERVICE_ACCOUNT);
        if (saDto.id != null) {
            enrichedData.put("serviceAccountId", saDto.id);
        }
        enrichedData.put("internal", saDto.internal);
        enrichedData.put("identityType", authInfo.identityType);
        enrichedData.put("authScheme", authInfo.authScheme);
        enrichedData.put("authFlow", authInfo.authFlow);
        if (authInfo.deprecated) {
            enrichedData.put("deprecated", true);
            enrichedData.put("legacyModel", authInfo.legacyModel);
            enrichedData.put("staticTokenCount", authInfo.tokenCount);
        }
        if (authInfo.keyCount > 0) {
            enrichedData.put("activeKeys", authInfo.keyCount);
            if (authInfo.keyAlgorithm != null) {
                enrichedData.put("keyAlgorithm", authInfo.keyAlgorithm);
            }
            if (authInfo.keyValidUntil != null) {
                enrichedData.put("validUntil", authInfo.keyValidUntil);
            }
        }
        entity.setData(enrichedData);

        final Map<String, String> tags = new HashMap<>();
        tags.put("auth-scheme", authInfo.authScheme);
        if (authInfo.deprecated) {
            tags.put("deprecated", "true");
            tags.put("auth-flow", "token-flow-deprecated");
        }
        entity.setTags(tags);
    }

    private static boolean isPermissionIssue(final String msg) {
        return StackitConstants.isPermissionIssue(msg);
    }

    public static class MembersResponse {
        public String resourceId;
        public String resourceType;
        public List<MemberJsonDto> members;
    }

    public static class MemberJsonDto {
        public String subject;
        public String role;
    }

    public static class ServiceAccountsResponse {
        public List<ServiceAccountJsonDto> items;
    }

    public static class ServiceAccountJsonDto {
        public String email;
        public String id;
        public boolean internal;
        public String projectId;
    }

    public static class TokensResponse {
        public List<TokenJsonDto> items;
    }

    public static class TokenJsonDto {
        public String id;
        public String validUntil;
        public String createdAt;
    }

    public static class KeysResponse {
        public List<KeyJsonDto> items;
    }

    public static class KeyJsonDto {
        public String id;
        public String keyAlgorithm;
        public String keyType;
        public String validUntil;
        public String createdAt;
    }

    public static class ServiceAccountAuthInfo {
        public final String authScheme;
        public final String authFlow;
        public final boolean deprecated;
        public final String legacyModel;
        public final String identityType;
        public final int tokenCount;
        public final int keyCount;
        public final String keyAlgorithm;
        public final String keyValidUntil;

        public ServiceAccountAuthInfo(
                final String authScheme,
                final String authFlow,
                final boolean deprecated,
                final String legacyModel,
                final String identityType,
                final int tokenCount,
                final int keyCount,
                final String keyAlgorithm) {
            this(authScheme, authFlow, deprecated, legacyModel, identityType, tokenCount, keyCount, keyAlgorithm, null);
        }

        public ServiceAccountAuthInfo(
                final String authScheme,
                final String authFlow,
                final boolean deprecated,
                final String legacyModel,
                final String identityType,
                final int tokenCount,
                final int keyCount,
                final String keyAlgorithm,
                final String keyValidUntil) {
            this.authScheme = authScheme;
            this.authFlow = authFlow;
            this.deprecated = deprecated;
            this.legacyModel = legacyModel;
            this.identityType = identityType;
            this.tokenCount = tokenCount;
            this.keyCount = keyCount;
            this.keyAlgorithm = keyAlgorithm;
            this.keyValidUntil = keyValidUntil;
        }
    }

    static class KeyInspectionResult {
        final int count;
        final String algorithm;
        final String validUntil;

        KeyInspectionResult(final int count, final String algorithm, final String validUntil) {
            this.count = count;
            this.algorithm = algorithm;
            this.validUntil = validUntil;
        }
    }
}
