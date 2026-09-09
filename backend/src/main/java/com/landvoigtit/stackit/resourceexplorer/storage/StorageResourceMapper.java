package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v2api.model.Bucket;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StorageResourceMapper {

    public static StorageResourceDto mapToDto(final Bucket bucket) {
        if (bucket == null) {
            return null;
        }
        final StorageResourceDto dto = new StorageResourceDto();
        dto.setBucketName(bucket.getName());
        dto.setRegion(bucket.getRegion());
        dto.setStorageClass(StackitConstants.STORAGE_CLASS_STANDARD);
        dto.setObjectLockEnabled(bucket.getObjectLockEnabled());
        dto.setUrlPathStyle(bucket.getUrlPathStyle());
        dto.setUrlVirtualHostedStyle(bucket.getUrlVirtualHostedStyle());
        return dto;
    }

    public static StackitEntity mapToEntity(final StorageResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        final String region = dto.getRegion() != null ? dto.getRegion() : StackitConstants.DEFAULT_REGION;
        if (dto.getBucketName() != null) {
            // Namespace ID with region to prevent collisions across regions
            entity.setId(UUID.nameUUIDFromBytes((region + "/" + dto.getBucketName()).getBytes()));
            entity.setResourceId(dto.getBucketName());
        }
        entity.setName(dto.getBucketName());
        entity.setType(StackitConstants.RESOURCE_TYPE_STORAGE);
        entity.setStatus(StackitConstants.STATUS_AVAILABLE);
        entity.setRegion(region);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID); // Set by scraper
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        final Map<String, Object> data = new HashMap<>();
        data.put("storageClass", dto.getStorageClass() != null ? dto.getStorageClass() : StackitConstants.STORAGE_CLASS_STANDARD);
        if (dto.getObjectLockEnabled() != null) {
            data.put("objectLockEnabled", dto.getObjectLockEnabled());
        }
        if (dto.getUrlPathStyle() != null) {
            data.put("urlPathStyle", dto.getUrlPathStyle());
        }
        if (dto.getUrlVirtualHostedStyle() != null) {
            data.put("urlVirtualHostedStyle", dto.getUrlVirtualHostedStyle());
        }
        if (dto.getIsPublic() != null) {
            data.put("isPublic", dto.getIsPublic());
        }
        if (dto.getPublicAccessType() != null) {
            data.put("publicAccessType", dto.getPublicAccessType());
        }
        if (dto.getBucketPolicy() != null) {
            data.put("bucketPolicy", dto.getBucketPolicy());
        }
        if (dto.getAcl() != null) {
            data.put("acl", dto.getAcl());
        }
        if (dto.getRetention() != null) {
            data.put("retention", dto.getRetention());
        }
        if (dto.getSecurityFindings() != null) {
            data.put("securityFindings", dto.getSecurityFindings());
        }
        entity.setData(data);

        final Map<String, String> tags = new HashMap<>();
        if (dto.getIsPublic() != null) {
            tags.put("is-public", String.valueOf(dto.getIsPublic()));
        } else if ("UNKNOWN".equalsIgnoreCase(dto.getPublicAccessType())) {
            tags.put("is-public", "unknown");
        }
        if (dto.getPublicAccessType() != null) {
            tags.put("public-access", dto.getPublicAccessType());
        }
        if (dto.getRetention() != null && dto.getRetention().getMode() != null) {
            tags.put("retention-mode", dto.getRetention().getMode().toLowerCase());
        }
        if (!tags.isEmpty()) {
            entity.setTags(tags);
        }
        return entity;
    }
}
