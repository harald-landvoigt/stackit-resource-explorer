package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v1api.model.Bucket;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
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
        return dto;
    }

    public static StackitEntity mapToEntity(final StorageResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getBucketName() != null) {
            entity.setId(UUID.nameUUIDFromBytes(dto.getBucketName().getBytes()));
            entity.setResourceId(dto.getBucketName());
        }
        entity.setName(dto.getBucketName());
        entity.setType(StackitConstants.RESOURCE_TYPE_STORAGE);
        entity.setStatus(StackitConstants.STATUS_AVAILABLE);
        entity.setRegion(dto.getRegion() != null ? dto.getRegion() : StackitConstants.DEFAULT_REGION);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID); // Set by scraper
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setData(Map.of(
            "storageClass", dto.getStorageClass() != null ? dto.getStorageClass() : ""
        ));
        return entity;
    }
}
