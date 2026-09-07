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
        entity.setData(data);
        return entity;
    }
}
