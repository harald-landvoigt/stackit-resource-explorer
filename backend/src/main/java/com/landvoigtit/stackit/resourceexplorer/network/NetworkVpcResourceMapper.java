package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.iaas.v1api.model.Network;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NetworkVpcResourceMapper {

    private NetworkVpcResourceMapper() {
        // Utility class; prevent instantiation
    }

    public static NetworkVpcResourceDto mapToDto(final Network network) {
        if (network == null) {
            return null;
        }
        final NetworkVpcResourceDto dto = new NetworkVpcResourceDto();
        if (network.getNetworkId() != null) {
            dto.setNetworkId(network.getNetworkId().toString());
        }
        dto.setName(network.getName() != null ? network.getName() : (network.getNetworkId() != null ? network.getNetworkId().toString() : "unnamed-network"));
        dto.setStatus(network.getState() != null ? network.getState() : StackitConstants.STATUS_ACTIVE);
        dto.setPrefixes(network.getPrefixes());
        dto.setGateway(network.getGateway());
        dto.setPublicIp(network.getPublicIp());
        dto.setRouted(network.getRouted());
        dto.setNameservers(network.getNameservers());

        if (network.getLabels() instanceof Map<?, ?> rawMap) {
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

    public static StackitEntity mapToEntity(final NetworkVpcResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getNetworkId() != null) {
            try {
                entity.setId(UUID.fromString(dto.getNetworkId()));
            } catch (final IllegalArgumentException e) {
                entity.setId(UUID.nameUUIDFromBytes(dto.getNetworkId().getBytes()));
            }
            entity.setResourceId(dto.getNetworkId());
        }
        entity.setName(dto.getName());
        entity.setType(StackitConstants.RESOURCE_TYPE_NETWORK_VPC);
        entity.setStatus(dto.getStatus());
        entity.setRegion(StackitConstants.DEFAULT_REGION);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID); // Set by scraper
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
            entity.setTags(dto.getLabels());
        }

        final Map<String, Object> data = new HashMap<>();
        if (dto.getPrefixes() != null) {
            data.put("prefixes", dto.getPrefixes());
        }
        if (dto.getGateway() != null) {
            data.put("gateway", dto.getGateway());
        }
        if (dto.getPublicIp() != null) {
            data.put("publicIp", dto.getPublicIp());
        }
        if (dto.getRouted() != null) {
            data.put("routed", dto.getRouted());
        }
        if (dto.getNameservers() != null) {
            data.put("nameservers", dto.getNameservers());
        }
        entity.setData(data);
        return entity;
    }
}
