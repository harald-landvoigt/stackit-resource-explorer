package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v2api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateAccessKeyPayload;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateAccessKeyResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateCredentialsGroupPayload;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateCredentialsGroupResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.CredentialsGroup;
import cloud.stackit.sdk.objectstorage.v2api.model.ListCredentialsGroupsResponse;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
@Slf4j
public class S3JitKeyManager {

    public static final String AUDIT_CREDENTIALS_GROUP_NAME = "resource-explorer-audit";
    public static final int DEFAULT_EXPIRY_MINUTES = 15;
    public static final String DEFAULT_ENDPOINT_TEMPLATE = "https://object.storage.%s.onstackit.cloud";

    private final ObjectStorageApi objectStorageApi;
    private final String endpointTemplate;

    @Inject
    public S3JitKeyManager(
            final ObjectStorageApi objectStorageApi,
            @ConfigProperty(name = "stackit.storage.s3.endpoint-template", defaultValue = DEFAULT_ENDPOINT_TEMPLATE)
            final String endpointTemplate) {
        this.objectStorageApi = objectStorageApi;
        this.endpointTemplate = (endpointTemplate != null && !endpointTemplate.isBlank())
                ? endpointTemplate
                : DEFAULT_ENDPOINT_TEMPLATE;
    }

    public EphemeralS3Session createEphemeralSession(final String projectId, final String region) throws Exception {
        final String effectiveRegion = (region != null && !region.isBlank()) ? region.trim().toLowerCase() : StackitConstants.DEFAULT_REGION;
        log.info("Creating ephemeral S3 session for project {} in region {}", projectId, effectiveRegion);

        // 1. Discover or create audit credentials group
        final String groupId = getOrCreateAuditCredentialsGroup(projectId, effectiveRegion);

        // 2. Mint ephemeral access key
        final CreateAccessKeyPayload keyPayload = new CreateAccessKeyPayload();
        keyPayload.setExpires(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(DEFAULT_EXPIRY_MINUTES));
        final CreateAccessKeyResponse keyResponse = objectStorageApi.createAccessKey(projectId, effectiveRegion, keyPayload, groupId);

        final String accessKey = keyResponse.getAccessKey();
        final String secretKey = keyResponse.getSecretAccessKey();
        final String keyId = keyResponse.getKeyId() != null ? keyResponse.getKeyId() : accessKey;

        // 3. Build regional S3Client
        final String endpointUrl = String.format(endpointTemplate, effectiveRegion);
        final S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpointUrl))
                .region(Region.of(effectiveRegion))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();

        log.info("Ephemeral S3Client initialized for endpoint {}", endpointUrl);
        return new EphemeralS3Session(s3Client, projectId, effectiveRegion, groupId, keyId, objectStorageApi);
    }

    public <T> T withEphemeralClient(final String projectId, final String region, final S3ClientFunction<T> function) throws Exception {
        try (final EphemeralS3Session session = createEphemeralSession(projectId, region)) {
            return function.apply(session.getS3Client());
        }
    }

    private String getOrCreateAuditCredentialsGroup(final String projectId, final String region) throws Exception {
        final ListCredentialsGroupsResponse groupsResponse = objectStorageApi.listCredentialsGroups(projectId, region);
        if (groupsResponse != null && groupsResponse.getCredentialsGroups() != null) {
            final List<CredentialsGroup> groups = groupsResponse.getCredentialsGroups();
            for (final CredentialsGroup group : groups) {
                if (AUDIT_CREDENTIALS_GROUP_NAME.equalsIgnoreCase(group.getDisplayName())) {
                    log.info("Found existing audit credentials group {} in project {}", group.getCredentialsGroupId(), projectId);
                    return group.getCredentialsGroupId();
                }
            }
        }

        log.info("Creating dedicated audit credentials group '{}' in project {} region {}", AUDIT_CREDENTIALS_GROUP_NAME, projectId, region);
        final CreateCredentialsGroupPayload groupPayload = new CreateCredentialsGroupPayload();
        groupPayload.setDisplayName(AUDIT_CREDENTIALS_GROUP_NAME);
        final CreateCredentialsGroupResponse createdGroupResp = objectStorageApi.createCredentialsGroup(projectId, region, groupPayload);
        if (createdGroupResp != null && createdGroupResp.getCredentialsGroup() != null) {
            return createdGroupResp.getCredentialsGroup().getCredentialsGroupId();
        }
        throw new IllegalStateException("Failed to create audit credentials group for project " + projectId);
    }

    @FunctionalInterface
    public interface S3ClientFunction<T> {
        T apply(S3Client s3Client) throws Exception;
    }

    @Getter
    public static class EphemeralS3Session implements AutoCloseable {
        private final S3Client s3Client;
        private final String projectId;
        private final String region;
        private final String credentialsGroupId;
        private final String keyId;
        private final ObjectStorageApi objectStorageApi;

        public EphemeralS3Session(
                final S3Client s3Client,
                final String projectId,
                final String region,
                final String credentialsGroupId,
                final String keyId,
                final ObjectStorageApi objectStorageApi) {
            this.s3Client = s3Client;
            this.projectId = projectId;
            this.region = region;
            this.credentialsGroupId = credentialsGroupId;
            this.keyId = keyId;
            this.objectStorageApi = objectStorageApi;
        }

        @Override
        public void close() {
            try {
                if (s3Client != null) {
                    s3Client.close();
                }
            } catch (final Exception e) {
                log.warn("Error closing S3Client: {}", e.getMessage());
            }

            try {
                if (objectStorageApi != null && keyId != null) {
                    objectStorageApi.deleteAccessKey(projectId, region, keyId, credentialsGroupId);
                    log.info("Successfully deleted ephemeral S3 key {} from group {}", keyId, credentialsGroupId);
                }
            } catch (final Exception e) {
                log.warn("Failed to delete ephemeral S3 access key {} from project {} in region {}: {}",
                        keyId, projectId, region, e.getMessage());
            }
        }
    }
}
