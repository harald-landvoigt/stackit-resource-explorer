package com.landvoigtit.stackit.resourceexplorer.iam;

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
        final Map<String, ServiceAccountAuthInfo> saAuthMap = new HashMap<>();
        final boolean sasSuccess = scrapeProjectServiceAccounts(projectIdStr, currentResourceIds, saAuthMap);
        final boolean membersSuccess = scrapeProjectMembers(projectIdStr, currentResourceIds, saAuthMap);
        return sasSuccess && membersSuccess;
    }

    private boolean scrapeProjectMembers(
            final String projectIdStr,
            final List<String> currentResourceIds,
            final Map<String, ServiceAccountAuthInfo> saAuthMap) {
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
                return false;
            }

            if (response.body() == null) {
                log.warn("Empty response body from IAM API for project {}", projectIdStr);
                return false;
            }

            final String bodyString = response.body().string();
            final MembersResponse membersResponse = objectMapper.readValue(bodyString, MembersResponse.class);
            if (membersResponse == null || membersResponse.members == null) {
                return true;
            }

            for (final MemberJsonDto jsonDto : membersResponse.members) {
                if (jsonDto.subject == null) {
                    continue;
                }
                final Member member = new Member();
                member.setSubject(jsonDto.subject);
                member.setRole(jsonDto.role);

                final IamResourceDto dto = IamResourceMapper.mapToDto(member);
                if (validator.validate(dto).isEmpty()) {
                    final StackitEntity entity = IamResourceMapper.mapToEntity(dto);
                    entity.setProjectId(projectIdStr);

                    final Map<String, Object> memberData = new HashMap<>();
                    memberData.put("role", jsonDto.role != null ? jsonDto.role : "");

                    final Map<String, String> memberTags = new HashMap<>();

                    if (saAuthMap.containsKey(jsonDto.subject)) {
                        final ServiceAccountAuthInfo saInfo = saAuthMap.get(jsonDto.subject);
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
                    } else if (jsonDto.subject.endsWith("@service-account.stackit.cloud") || jsonDto.subject.contains("service-account")) {
                        memberData.put("identityType", "Service Account");
                        memberData.put("authScheme", StackitConstants.AUTH_FLOW_KEY_FLOW);
                        memberData.put("authFlow", StackitConstants.AUTH_FLOW_KEY_FLOW);
                        memberTags.put("auth-scheme", StackitConstants.AUTH_FLOW_KEY_FLOW);
                    } else if (jsonDto.subject.contains("@")) {
                        memberData.put("identityType", "User (Human)");
                        memberData.put("authScheme", StackitConstants.AUTH_FLOW_OIDC);
                        memberData.put("authFlow", "OIDC / SSO");
                        final String domain = jsonDto.subject.substring(jsonDto.subject.indexOf('@') + 1);
                        memberData.put("idpDomain", domain);
                        memberTags.put("auth-scheme", StackitConstants.AUTH_FLOW_OIDC);
                    } else if (jsonDto.subject.startsWith("group:")) {
                        memberData.put("identityType", "Group");
                        memberData.put("authScheme", "IdP Group Claim");
                        memberData.put("authFlow", "IdP Group");
                        memberTags.put("auth-scheme", "IdP Group");
                    } else {
                        memberData.put("identityType", "Identity");
                        memberData.put("authScheme", "Standard");
                    }

                    entity.setData(memberData);
                    if (!memberTags.isEmpty()) {
                        entity.setTags(memberTags);
                    }

                    repository.persistOrUpdate(entity);
                    currentResourceIds.add(entity.getResourceId());
                } else {
                    log.warn("Invalid IAM resource DTO: {}", dto.getMemberId());
                }
            }
            return true;
        } catch (final Exception e) {
            log.warn("Failed to scrape IAM resources for project {}: {}", projectIdStr, e.getMessage());
            return false;
        }
    }

    private boolean scrapeProjectServiceAccounts(
            final String projectIdStr,
            final List<String> currentResourceIds,
            final Map<String, ServiceAccountAuthInfo> saAuthMap) {
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
                return false;
            }

            if (response.body() == null) {
                log.warn("Empty response body from service accounts API for project {}", projectIdStr);
                return false;
            }

            final String bodyString = response.body().string();
            final ServiceAccountsResponse sasResponse = objectMapper.readValue(bodyString, ServiceAccountsResponse.class);
            if (sasResponse == null || sasResponse.items == null) {
                return true;
            }

            for (final ServiceAccountJsonDto saDto : sasResponse.items) {
                if (saDto.email == null) {
                    continue;
                }
                final Member member = new Member();
                member.setSubject(saDto.email);
                member.setRole(StackitConstants.ROLE_SERVICE_ACCOUNT);

                final IamResourceDto dto = IamResourceMapper.mapToDto(member);
                if (validator.validate(dto).isEmpty()) {
                    final StackitEntity entity = IamResourceMapper.mapToEntity(dto);
                    entity.setProjectId(projectIdStr);

                    final String identityType;
                    final String authScheme;
                    final String authFlow;
                    final boolean deprecated;
                    final String legacyModel;
                    int tokenCount = 0;
                    int keyCount = 0;
                    String keyAlgorithm = null;
                    String keyValidUntil = null;

                    if (saDto.internal) {
                        identityType = "Managed Service Identity";
                        authScheme = StackitConstants.AUTH_FLOW_PLATFORM_MANAGED;
                        authFlow = "Platform Managed";
                        deprecated = false;
                        legacyModel = null;
                    } else {
                        identityType = "Service Account";
                        // Check for tokens (Token Flow)
                        if (saDto.id != null) {
                            final String tokensUrl = sdkConfig != null
                                    ? sdkConfig.getServiceAccountTokensUrl(projectIdStr, saDto.id)
                                    : StackitConstants.formatServiceAccountTokensUrl(StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL, projectIdStr, saDto.id);
                            final Request tokensReq = new Request.Builder().url(tokensUrl).get().build();
                            try (final Response tokensResp = httpClient.newCall(tokensReq).execute()) {
                                if (tokensResp.isSuccessful() && tokensResp.body() != null) {
                                    final String tokensBody = tokensResp.body().string();
                                    final TokensResponse tokensObj = objectMapper.readValue(tokensBody, TokensResponse.class);
                                    if (tokensObj != null && tokensObj.items != null) {
                                        tokenCount = tokensObj.items.size();
                                    }
                                }
                            } catch (final Exception e) {
                                log.debug("Tokens check failed for SA {}: {}", saDto.id, e.getMessage());
                            }

                            // Check for keys (Key Flow)
                            final String targetKeyId = saDto.email != null ? saDto.email : saDto.id;
                            final String keysUrl = sdkConfig != null
                                    ? sdkConfig.getServiceAccountKeysUrl(projectIdStr, targetKeyId)
                                    : StackitConstants.formatServiceAccountKeysUrl(StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL, projectIdStr, targetKeyId);
                            final Request keysReq = new Request.Builder().url(keysUrl).get().build();
                            try (final Response keysResp = httpClient.newCall(keysReq).execute()) {
                                if (keysResp.isSuccessful() && keysResp.body() != null) {
                                    final String keysBody = keysResp.body().string();
                                    final KeysResponse keysObj = objectMapper.readValue(keysBody, KeysResponse.class);
                                    if (keysObj != null && keysObj.items != null) {
                                        keyCount = keysObj.items.size();
                                        if (!keysObj.items.isEmpty()) {
                                            final KeyJsonDto firstKey = keysObj.items.get(0);
                                            keyAlgorithm = firstKey.keyAlgorithm;
                                            keyValidUntil = firstKey.validUntil;
                                        }
                                    }
                                } else {
                                    log.debug("Keys check returned HTTP {} for SA {}. Key auditing permission may be needed.", keysResp.code(), saDto.email);
                                }
                            } catch (final Exception e) {
                                log.debug("Keys check failed for SA {}: {}", saDto.id, e.getMessage());
                            }

                            // Fallback: If remote call was blocked (e.g. 403 Forbidden) but this SA matches
                            // the active local scraper service account key credentials, populate key info directly
                            if (keyCount == 0 && sdkConfig != null) {
                                final String localEmail = sdkConfig.getServiceAccountEmail();
                                final String localSub = sdkConfig.getServiceAccountId();
                                if ((localEmail != null && localEmail.equalsIgnoreCase(saDto.email)) ||
                                        (localSub != null && localSub.equals(saDto.id))) {
                                    keyCount = 1;
                                    keyAlgorithm = sdkConfig.getServiceAccountKeyAlgorithm() != null
                                            ? sdkConfig.getServiceAccountKeyAlgorithm()
                                            : "RSA_2048";
                                }
                            }
                        }

                        if (tokenCount > 0) {
                            if (keyCount > 0) {
                                authScheme = "Hybrid (Key Flow & Token Flow Deprecated)";
                            } else {
                                authScheme = StackitConstants.AUTH_FLOW_TOKEN_DEPRECATED;
                            }
                            authFlow = StackitConstants.AUTH_FLOW_TOKEN_DEPRECATED;
                            deprecated = true;
                            legacyModel = StackitConstants.TOKEN_FLOW_DEPRECATED_DESCRIPTION;
                        } else if (keyCount > 0) {
                            authScheme = keyAlgorithm != null ? "Key Flow (" + keyAlgorithm + ")" : StackitConstants.AUTH_FLOW_KEY_FLOW;
                            authFlow = StackitConstants.AUTH_FLOW_KEY_FLOW;
                            deprecated = false;
                            legacyModel = null;
                        } else {
                            authScheme = StackitConstants.AUTH_FLOW_KEY_FLOW;
                            authFlow = StackitConstants.AUTH_FLOW_KEY_FLOW;
                            deprecated = false;
                            legacyModel = null;
                        }
                    }

                    // Enrich entity metadata with specific service account properties
                    final Map<String, Object> enrichedData = new HashMap<>();
                    enrichedData.put("role", StackitConstants.ROLE_SERVICE_ACCOUNT);
                    if (saDto.id != null) {
                        enrichedData.put("serviceAccountId", saDto.id);
                    }
                    enrichedData.put("internal", saDto.internal);
                    enrichedData.put("identityType", identityType);
                    enrichedData.put("authScheme", authScheme);
                    enrichedData.put("authFlow", authFlow);
                    if (deprecated) {
                        enrichedData.put("deprecated", true);
                        enrichedData.put("legacyModel", legacyModel);
                        enrichedData.put("staticTokenCount", tokenCount);
                    }
                    if (keyCount > 0) {
                        enrichedData.put("activeKeys", keyCount);
                        if (keyAlgorithm != null) {
                            enrichedData.put("keyAlgorithm", keyAlgorithm);
                        }
                        if (keyValidUntil != null) {
                            enrichedData.put("validUntil", keyValidUntil);
                        }
                    }
                    entity.setData(enrichedData);

                    final Map<String, String> tags = new HashMap<>();
                    tags.put("auth-scheme", authScheme);
                    if (deprecated) {
                        tags.put("deprecated", "true");
                        tags.put("auth-flow", "token-flow-deprecated");
                    }
                    entity.setTags(tags);

                    repository.persistOrUpdate(entity);
                    currentResourceIds.add(entity.getResourceId());

                    saAuthMap.put(saDto.email, new ServiceAccountAuthInfo(
                            authScheme, authFlow, deprecated, legacyModel, identityType, tokenCount, keyCount, keyAlgorithm
                    ));
                } else {
                    log.warn("Invalid Service Account DTO: {}", dto.getMemberId());
                }
            }
            return true;
        } catch (final Exception e) {
            log.warn("Failed to scrape service accounts for project {}: {}", projectIdStr, e.getMessage());
            return false;
        }
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

        public ServiceAccountAuthInfo(
                final String authScheme,
                final String authFlow,
                final boolean deprecated,
                final String legacyModel,
                final String identityType,
                final int tokenCount,
                final int keyCount,
                final String keyAlgorithm) {
            this.authScheme = authScheme;
            this.authFlow = authFlow;
            this.deprecated = deprecated;
            this.legacyModel = legacyModel;
            this.identityType = identityType;
            this.tokenCount = tokenCount;
            this.keyCount = keyCount;
            this.keyAlgorithm = keyAlgorithm;
        }
    }
}
