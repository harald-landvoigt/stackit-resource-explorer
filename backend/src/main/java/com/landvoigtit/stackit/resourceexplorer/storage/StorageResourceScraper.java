package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v2api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v2api.model.Bucket;
import cloud.stackit.sdk.objectstorage.v2api.model.ComplianceLockResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.DefaultRetentionResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.ListBucketsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockResponse;
import software.amazon.awssdk.services.s3.model.Grant;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Slf4j
public class StorageResourceScraper {

    @Inject
    ObjectStorageApi objectStorageApi;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Inject
    StackitSdkConfig sdkConfig;

    @Inject
    S3JitKeyManager s3JitKeyManager;

    @Scheduled(every = "${stackit.storage.schedule:off}")
    public void scrape() {
        log.info("Starting Storage resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("Storage resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectStorage(projectIdStr, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_STORAGE, projectIdStr, currentResourceIds);
                }
            }
            log.info("Storage resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape Storage resources", e);
        }
    }

    private boolean scrapeProjectStorage(final String projectIdStr, final List<String> currentResourceIds) {
        final List<String> regions = sdkConfig != null ? sdkConfig.getRegions() : StackitConstants.DEFAULT_REGIONS;
        boolean allRegionsSucceeded = true;

        for (final String region : regions) {
            try {
                final ListBucketsResponse bucketsResponse = objectStorageApi.listBuckets(projectIdStr, region);
                if (bucketsResponse == null || bucketsResponse.getBuckets() == null || bucketsResponse.getBuckets().isEmpty()) {
                    continue;
                }

                // Query project-level compliance lock
                Integer projectMaxRetentionDays = null;
                try {
                    final ComplianceLockResponse lockResponse = objectStorageApi.getComplianceLock(projectIdStr, region);
                    if (lockResponse != null && lockResponse.getMaxRetentionDays() != null) {
                        projectMaxRetentionDays = lockResponse.getMaxRetentionDays();
                    }
                } catch (final Exception e) {
                    log.warn("Compliance lock not found or accessible for project {} in region {}: {}", projectIdStr, region, e.getMessage());
                }

                // Prepare initial bucket DTOs and query control-plane retention
                final List<StorageResourceDto> dtos = new ArrayList<>();
                for (final Bucket bucket : bucketsResponse.getBuckets()) {
                    final StorageResourceDto dto = StorageResourceMapper.mapToDto(bucket);
                    enrichRetention(projectIdStr, region, bucket.getName(), projectMaxRetentionDays, dto);
                    dtos.add(dto);
                }

                // S3 data plane enrichment using dynamic JIT credentials
                try {
                    s3JitKeyManager.withEphemeralClient(projectIdStr, region, s3Client -> {
                        for (final StorageResourceDto dto : dtos) {
                            enrichWithS3(s3Client, dto);
                        }
                        return null;
                    });
                } catch (final Exception e) {
                    log.warn("Ephemeral S3 access not permitted or failed for project {} in region {}: {}. Falling back to control-plane metadata.",
                            projectIdStr, region, e.getMessage());
                    for (final StorageResourceDto dto : dtos) {
                        if (dto.getIsPublic() == null) {
                            StorageSecurityEvaluator.evaluateAndEnrich(dto, null);
                        }
                    }
                }

                // Validate and persist enriched entities
                for (final StorageResourceDto dto : dtos) {
                    if (validator.validate(dto).isEmpty()) {
                        final StackitEntity entity = StorageResourceMapper.mapToEntity(dto);
                        entity.setProjectId(projectIdStr);
                        repository.persistOrUpdate(entity);
                        currentResourceIds.add(entity.getResourceId());
                    } else {
                        log.warn("Invalid Storage DTO: {}", dto.getBucketName());
                    }
                }
            } catch (final Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("404") || msg.contains("403") || msg.contains("not_found")) {
                    log.debug("Storage not enabled or accessible for project {} in region {}: {}", projectIdStr, region, msg);
                } else {
                    log.warn("Failed to scrape Storage resources for project {} in region {}: {}", projectIdStr, region, e.getMessage());
                    allRegionsSucceeded = false;
                }
            }
        }
        return allRegionsSucceeded;
    }

    private void enrichRetention(
            final String projectId,
            final String region,
            final String bucketName,
            final Integer projectMaxRetentionDays,
            final StorageResourceDto dto) {
        try {
            final DefaultRetentionResponse retentionResponse = objectStorageApi.getDefaultRetention(projectId, region, bucketName);
            if (retentionResponse != null) {
                final StorageResourceDto.StorageRetentionDto retentionDto = new StorageResourceDto.StorageRetentionDto();
                if (retentionResponse.getDays() != null) {
                    retentionDto.setRetentionDays(retentionResponse.getDays());
                    retentionDto.setDefaultRetentionSet(true);
                }
                if (retentionResponse.getMode() != null) {
                    retentionDto.setMode(retentionResponse.getMode().getValue());
                }
                if (projectMaxRetentionDays != null) {
                    retentionDto.setProjectMaxRetentionDays(projectMaxRetentionDays);
                }
                dto.setRetention(retentionDto);
            }
        } catch (final Exception e) {
            log.warn("Default retention not found or error for bucket {} in project {}: {}", bucketName, projectId, e.getMessage());
        }
    }

    private void enrichWithS3(final S3Client s3Client, final StorageResourceDto dto) {
        final String bucketName = dto.getBucketName();

        // 1. Bucket ACL
        try {
            final GetBucketAclResponse aclResponse = s3Client.getBucketAcl(GetBucketAclRequest.builder().bucket(bucketName).build());
            final StorageResourceDto.StorageAclDto aclDto = new StorageResourceDto.StorageAclDto();
            if (aclResponse.owner() != null) {
                aclDto.setOwner(aclResponse.owner().displayName());
                aclDto.setOwnerId(aclResponse.owner().id());
            }
            if (aclResponse.grants() != null) {
                final List<StorageResourceDto.StorageGrantDto> grants = new ArrayList<>();
                for (final Grant grant : aclResponse.grants()) {
                    final StorageResourceDto.StorageGrantDto grantDto = new StorageResourceDto.StorageGrantDto();
                    if (grant.grantee() != null) {
                        grantDto.setGrantee(grant.grantee().uri() != null ? grant.grantee().uri() : grant.grantee().id());
                        grantDto.setGranteeType(grant.grantee().typeAsString());
                    }
                    grantDto.setPermission(grant.permissionAsString());
                    grants.add(grantDto);
                }
                aclDto.setGrants(grants);
            }
            dto.setAcl(aclDto);
        } catch (final Exception e) {
            log.warn("Could not fetch ACL for bucket {}: {}", bucketName, e.getMessage());
        }

        // 2. Bucket Policy
        try {
            final GetBucketPolicyResponse policyResponse = s3Client.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucketName).build());
            if (policyResponse != null && policyResponse.policy() != null) {
                dto.setBucketPolicy(policyResponse.policy());
            }
        } catch (final S3Exception e) {
            if (e.statusCode() == 404 || (e.awsErrorDetails() != null && "NoSuchBucketPolicy".equals(e.awsErrorDetails().errorCode()))) {
                log.debug("No bucket policy found for bucket {}", bucketName);
            } else {
                log.debug("Could not fetch policy for bucket {}: {}", bucketName, e.getMessage());
            }
        } catch (final Exception e) {
            log.debug("Could not fetch policy for bucket {}: {}", bucketName, e.getMessage());
        }

        // 3. Public Access Block
        Boolean publicAccessBlockEnabled = null;
        try {
            final GetPublicAccessBlockResponse pabResponse = s3Client.getPublicAccessBlock(GetPublicAccessBlockRequest.builder().bucket(bucketName).build());
            if (pabResponse != null && pabResponse.publicAccessBlockConfiguration() != null) {
                final PublicAccessBlockConfiguration pab = pabResponse.publicAccessBlockConfiguration();
                publicAccessBlockEnabled = Boolean.TRUE.equals(pab.blockPublicAcls())
                        || Boolean.TRUE.equals(pab.blockPublicPolicy())
                        || Boolean.TRUE.equals(pab.ignorePublicAcls())
                        || Boolean.TRUE.equals(pab.restrictPublicBuckets());
            }
        } catch (final Exception e) {
            log.debug("PublicAccessBlock not supported or not found for bucket {}: {}", bucketName, e.getMessage());
        }

        // 4. Security Risk Evaluation
        StorageSecurityEvaluator.evaluateAndEnrich(dto, publicAccessBlockEnabled);
    }
}
