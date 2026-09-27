package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.iaas.v2api.model.PublicIp;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PublicIpResourceMapper {

    private PublicIpResourceMapper() {
        // Utility class
    }

    public static PublicIpResourceDto mapToDto(final PublicIp publicIp) {
        if (publicIp == null) {
            return null;
        }
        final PublicIpResourceDto dto = new PublicIpResourceDto();
        if (publicIp.getId() != null) {
            dto.setPublicIpId(publicIp.getId().toString());
        }
        dto.setIp(publicIp.getIp());
        dto.setName(publicIp.getIp());

        if (publicIp.getNetworkInterface() != null) {
            dto.setNetworkInterfaceId(publicIp.getNetworkInterface().toString());
            dto.setAttached(true);
            dto.setStatus("ATTACHED");
        } else {
            dto.setAttached(false);
            dto.setStatus("UNATTACHED");
        }

        if (publicIp.getLabels() instanceof Map<?, ?> rawMap) {
            final Map<String, String> labels = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    labels.put(k.toString(), v.toString());
                }
            });
            dto.setLabels(labels);
        }

        return dto;
    }

    public static StackitEntity mapToEntity(final PublicIpResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        final String region = (dto.getRegion() != null && !dto.getRegion().isBlank())
                ? dto.getRegion()
                : StackitConstants.DEFAULT_REGION;

        if (dto.getPublicIpId() != null) {
            try {
                entity.setId(UUID.fromString(dto.getPublicIpId()));
            } catch (final IllegalArgumentException e) {
                entity.setId(UUID.nameUUIDFromBytes((region + "/" + dto.getPublicIpId()).getBytes()));
            }
            entity.setResourceId(dto.getPublicIpId());
        }

        entity.setName(dto.getName() != null && !dto.getName().isBlank() ? dto.getName() : dto.getIp());
        entity.setType(StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : (Boolean.TRUE.equals(dto.getAttached()) ? "ATTACHED" : "UNATTACHED"));
        entity.setRegion(region);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        final Map<String, String> tags = new HashMap<>();
        if (dto.getLabels() != null) {
            tags.putAll(dto.getLabels());
        }
        tags.put("attached", String.valueOf(Boolean.TRUE.equals(dto.getAttached())));
        if (dto.getIp() != null) {
            tags.put("ip", dto.getIp());
        }
        entity.setTags(tags);

        final boolean isAttached = Boolean.TRUE.equals(dto.getAttached());
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("ip", dto.getIp());
        data.put("attached", isAttached);
        data.put("attachmentStatus", isAttached ? "ATTACHED" : "UNATTACHED");
        if (dto.getNetworkInterfaceId() != null) {
            data.put("networkInterfaceId", dto.getNetworkInterfaceId());
        }
        if (dto.getServerId() != null) {
            data.put("serverId", dto.getServerId());
        }
        if (dto.getServerName() != null) {
            data.put("serverName", dto.getServerName());
        }
        entity.setData(data);

        return entity;
    }
}
