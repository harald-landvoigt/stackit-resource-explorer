package com.landvoigtit.stackit.resourceexplorer.compute;

import cloud.stackit.sdk.iaas.v1api.model.Server;
import cloud.stackit.sdk.iaas.v1api.model.ServerNetwork;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ComputeResourceMapper {

    private ComputeResourceMapper() {
        // Utility class
    }

    public static ComputeResourceDto mapToDto(final Server server) {
        if (server == null) {
            return null;
        }
        final ComputeResourceDto dto = new ComputeResourceDto();
        if (server.getId() != null) {
            dto.setServerId(server.getId().toString());
        }
        dto.setName(server.getName());
        dto.setStatus(server.getStatus());
        dto.setPowerStatus(server.getPowerStatus());
        dto.setMachineType(server.getMachineType());
        dto.setAvailabilityZone(server.getAvailabilityZone());
        if (server.getImageId() != null) {
            dto.setImageId(server.getImageId().toString());
        }
        if (server.getBootVolume() != null) {
            if (server.getBootVolume().getId() != null) {
                dto.setBootVolumeId(server.getBootVolume().getId().toString());
            }
            dto.setBootVolumeDeleteOnTermination(server.getBootVolume().getDeleteOnTermination());
        }
        if (server.getVolumes() != null) {
            dto.setAttachedVolumes(server.getVolumes().stream().map(UUID::toString).toList());
        }
        dto.setKeypairName(server.getKeypairName());
        dto.setSecurityGroups(server.getSecurityGroups());

        if (server.getNics() != null) {
            final List<String> ips = new ArrayList<>();
            for (final ServerNetwork nic : server.getNics()) {
                if (nic.getIpv4() != null && !nic.getIpv4().isBlank()) {
                    ips.add(nic.getIpv4());
                }
                if (nic.getPublicIp() != null && !nic.getPublicIp().isBlank()) {
                    ips.add(nic.getPublicIp());
                }
            }
            if (!ips.isEmpty()) {
                dto.setIpAddresses(ips);
            }
        }

        if (server.getLabels() instanceof Map<?, ?> rawMap) {
            final Map<String, String> labelsMap = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    labelsMap.put(k.toString(), v.toString());
                }
            });
            dto.setLabels(labelsMap);
        }

        dto.setLaunchedAt(server.getLaunchedAt());
        dto.setCreatedAt(server.getCreatedAt());
        dto.setUpdatedAt(server.getUpdatedAt());
        return dto;
    }

    public static StackitEntity mapToEntity(final ComputeResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getServerId() != null) {
            try {
                entity.setId(UUID.fromString(dto.getServerId()));
            } catch (final IllegalArgumentException e) {
                entity.setId(UUID.nameUUIDFromBytes(dto.getServerId().getBytes()));
            }
            entity.setResourceId(dto.getServerId());
        }
        entity.setName(dto.getName());
        entity.setType(StackitConstants.RESOURCE_TYPE_COMPUTE);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : StackitConstants.STATUS_ACTIVE);

        final String region = (dto.getAvailabilityZone() != null && !dto.getAvailabilityZone().isBlank())
                ? dto.getAvailabilityZone()
                : StackitConstants.DEFAULT_REGION;
        entity.setRegion(region);

        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID); // Set by scraper
        entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt().toInstant() : Instant.now());
        entity.setUpdatedAt(dto.getUpdatedAt() != null ? dto.getUpdatedAt().toInstant() : Instant.now());

        if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
            entity.setTags(dto.getLabels());
        }

        final Map<String, Object> data = new LinkedHashMap<>();
        if (dto.getMachineType() != null && !dto.getMachineType().isBlank()) {
            data.put("machineType", dto.getMachineType());
            data.put("size", dto.getMachineType());
        }
        if (dto.getPowerStatus() != null && !dto.getPowerStatus().isBlank()) {
            data.put("powerStatus", dto.getPowerStatus());
        }
        if (dto.getAvailabilityZone() != null && !dto.getAvailabilityZone().isBlank()) {
            data.put("availabilityZone", dto.getAvailabilityZone());
        }
        if (dto.getBootVolumeId() != null && !dto.getBootVolumeId().isBlank()) {
            data.put("bootVolumeId", dto.getBootVolumeId());
        }
        if (dto.getBootVolumeDeleteOnTermination() != null) {
            data.put("bootVolumeDeleteOnTermination", dto.getBootVolumeDeleteOnTermination());
        }
        if (dto.getAttachedVolumes() != null && !dto.getAttachedVolumes().isEmpty()) {
            data.put("attachedVolumes", dto.getAttachedVolumes());
        }
        if (dto.getImageId() != null && !dto.getImageId().isBlank()) {
            data.put("imageId", dto.getImageId());
        }
        if (dto.getKeypairName() != null && !dto.getKeypairName().isBlank()) {
            data.put("keypairName", dto.getKeypairName());
        }
        if (dto.getSecurityGroups() != null && !dto.getSecurityGroups().isEmpty()) {
            data.put("securityGroups", dto.getSecurityGroups());
        }
        if (dto.getIpAddresses() != null && !dto.getIpAddresses().isEmpty()) {
            data.put("ipAddresses", dto.getIpAddresses());
        }
        if (dto.getLaunchedAt() != null) {
            data.put("launchedAt", dto.getLaunchedAt().toString());
        }

        entity.setData(data);
        return entity;
    }
}
