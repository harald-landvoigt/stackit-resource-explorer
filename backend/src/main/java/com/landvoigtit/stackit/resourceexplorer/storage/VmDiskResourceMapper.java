package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.iaas.v1api.model.Volume;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VmDiskResourceMapper {

    private VmDiskResourceMapper() {
        // Utility class; prevent instantiation
    }

    public static VmDiskResourceDto mapToDto(final Volume volume) {
        if (volume == null) {
            return null;
        }
        final VmDiskResourceDto dto = new VmDiskResourceDto();
        if (volume.getId() != null) {
            dto.setVolumeId(volume.getId().toString());
        }
        dto.setName(volume.getName() != null ? volume.getName() : (volume.getId() != null ? volume.getId().toString() : "unnamed-volume"));
        dto.setStatus(volume.getStatus() != null ? volume.getStatus() : StackitConstants.STATUS_AVAILABLE);
        dto.setSizeGb(volume.getSize());
        dto.setPerformanceClass(volume.getPerformanceClass());
        dto.setAvailabilityZone(volume.getAvailabilityZone());
        dto.setBootable(volume.getBootable());
        dto.setEncrypted(volume.getEncrypted());
        if (volume.getServerId() != null) {
            dto.setServerId(volume.getServerId().toString());
        }

        if (volume.getLabels() instanceof Map<?, ?> rawMap) {
            final Map<String, String> labelsMap = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    labelsMap.put(k.toString(), v.toString());
                }
            });
            dto.setLabels(labelsMap);
        }

        return dto;
    }

    public static StackitEntity mapToEntity(final VmDiskResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getVolumeId() != null) {
            try {
                entity.setId(UUID.fromString(dto.getVolumeId()));
            } catch (final IllegalArgumentException e) {
                entity.setId(UUID.nameUUIDFromBytes(dto.getVolumeId().getBytes()));
            }
            entity.setResourceId(dto.getVolumeId());
        }
        entity.setName(dto.getName());
        entity.setType(StackitConstants.RESOURCE_TYPE_VMDISKS);
        entity.setStatus(dto.getStatus());
        entity.setRegion(dto.getAvailabilityZone() != null ? dto.getAvailabilityZone() : StackitConstants.DEFAULT_REGION);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID); // Set by scraper
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
            entity.setTags(dto.getLabels());
        }

        final Map<String, Object> data = new HashMap<>();
        if (dto.getSizeGb() != null) {
            data.put("sizeGb", dto.getSizeGb());
        }
        if (dto.getPerformanceClass() != null) {
            data.put("performanceClass", dto.getPerformanceClass());
        }
        if (dto.getAvailabilityZone() != null) {
            data.put("availabilityZone", dto.getAvailabilityZone());
        }
        if (dto.getBootable() != null) {
            data.put("bootable", dto.getBootable());
        }
        if (dto.getEncrypted() != null) {
            data.put("encrypted", dto.getEncrypted());
        }
        if (dto.getServerId() != null) {
            data.put("serverId", dto.getServerId());
        }
        entity.setData(data);
        return entity;
    }
}
