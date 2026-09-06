package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.alb.v2api.model.LoadBalancer;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class NetworkResourceMapper {

    public static NetworkResourceDto mapToDto(final LoadBalancer loadBalancer) {
        if (loadBalancer == null) {
            return null;
        }
        final NetworkResourceDto dto = new NetworkResourceDto();
        dto.setLoadBalancerId(loadBalancer.getName());
        dto.setName(loadBalancer.getName());
        dto.setIpAddress(loadBalancer.getExternalAddress());
        dto.setRegion(loadBalancer.getRegion());
        dto.setLabels(loadBalancer.getLabels());
        return dto;
    }

    public static StackitEntity mapToEntity(final NetworkResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getLoadBalancerId() != null) {
            entity.setId(UUID.nameUUIDFromBytes(dto.getLoadBalancerId().getBytes()));
            entity.setResourceId(dto.getLoadBalancerId());
        }
        entity.setName(dto.getName());
        entity.setType(StackitConstants.RESOURCE_TYPE_NETWORK);
        entity.setStatus(StackitConstants.STATUS_ACTIVE);
        entity.setRegion(dto.getRegion() != null && !dto.getRegion().isBlank() ? dto.getRegion() : StackitConstants.ALB_DEFAULT_REGION);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID); // Set by scraper
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
            entity.setTags(dto.getLabels());
        }

        entity.setData(Map.of(
            "ipAddress", dto.getIpAddress() != null ? dto.getIpAddress() : ""
        ));
        return entity;
    }
}
