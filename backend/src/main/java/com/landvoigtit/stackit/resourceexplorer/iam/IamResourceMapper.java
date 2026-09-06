package com.landvoigtit.stackit.resourceexplorer.iam;

import cloud.stackit.sdk.resourcemanager.v0api.model.Member;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class IamResourceMapper {

    public static IamResourceDto mapToDto(final Member member) {
        if (member == null) {
            return null;
        }
        final IamResourceDto dto = new IamResourceDto();
        dto.setMemberId(member.getSubject());
        dto.setRole(member.getRole());
        return dto;
    }

    public static StackitEntity mapToEntity(final IamResourceDto dto) {
        if (dto == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        if (dto.getMemberId() != null) {
            entity.setId(UUID.nameUUIDFromBytes(dto.getMemberId().getBytes()));
            entity.setResourceId(dto.getMemberId());
        }
        entity.setName(dto.getMemberId());
        entity.setType(StackitConstants.RESOURCE_TYPE_IAM);
        entity.setStatus(StackitConstants.STATUS_ACTIVE);
        entity.setRegion(StackitConstants.GLOBAL_REGION);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID); // Set by scraper
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setData(Map.of(
            "role", dto.getRole() != null ? dto.getRole() : ""
        ));
        return entity;
    }
}
