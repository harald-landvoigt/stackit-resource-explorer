package com.landvoigtit.stackit.resourceexplorer.iam;

import cloud.stackit.sdk.objectstorage.v2api.model.AccessKey;
import cloud.stackit.sdk.objectstorage.v2api.model.CredentialsGroup;
import cloud.stackit.sdk.resourcemanager.v0api.model.Member;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import java.time.Instant;
import java.util.HashMap;
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

    public static StackitEntity mapS3KeyToEntity(
            final String projectId,
            final String region,
            final CredentialsGroup group,
            final AccessKey key) {
        if (key == null || key.getKeyId() == null) {
            return null;
        }
        final StackitEntity entity = new StackitEntity();
        final String keyId = key.getKeyId();
        entity.setId(UUID.nameUUIDFromBytes(("iam:s3-key:" + projectId + ":" + region + ":" + keyId).getBytes()));
        entity.setResourceId(keyId);
        final String displayName = (key.getDisplayName() != null && !key.getDisplayName().isBlank())
                ? key.getDisplayName()
                : keyId;
        entity.setName(displayName);
        entity.setType(StackitConstants.RESOURCE_TYPE_IAM);
        entity.setRegion(region != null ? region : StackitConstants.GLOBAL_REGION);
        entity.setProjectId(projectId != null ? projectId : StackitConstants.UNKNOWN_PROJECT_ID);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        boolean isExpired = false;
        if (key.getExpires() != null && !key.getExpires().isBlank()) {
            try {
                final Instant exp = Instant.parse(key.getExpires());
                if (exp.isBefore(Instant.now())) {
                    isExpired = true;
                }
            } catch (final Exception ignored) {
            }
        }
        entity.setStatus(isExpired ? "EXPIRED" : StackitConstants.STATUS_ACTIVE);

        final Map<String, Object> data = new HashMap<>();
        data.put("identityType", "S3 Access Key");
        data.put("authScheme", "S3 HMAC Key");
        data.put("authFlow", "S3 Data Plane");
        data.put("keyId", keyId);
        if (key.getDisplayName() != null && !key.getDisplayName().isBlank()) {
            data.put("displayName", key.getDisplayName());
        }
        if (group != null) {
            if (group.getCredentialsGroupId() != null) {
                data.put("credentialsGroupId", group.getCredentialsGroupId());
            }
            if (group.getDisplayName() != null && !group.getDisplayName().isBlank()) {
                data.put("credentialsGroupName", group.getDisplayName());
            }
            if (group.getUrn() != null && !group.getUrn().isBlank()) {
                data.put("credentialsGroupUrn", group.getUrn());
            }
        }
        data.put("region", region != null ? region : StackitConstants.GLOBAL_REGION);
        if (key.getExpires() != null && !key.getExpires().isBlank()) {
            data.put("expires", key.getExpires());
        }
        data.put("expired", isExpired);
        entity.setData(data);

        final Map<String, String> tags = new HashMap<>();
        tags.put("identity-type", "s3-access-key");
        tags.put("auth-scheme", "s3-hmac-key");
        if (group != null && group.getDisplayName() != null && !group.getDisplayName().isBlank()) {
            tags.put("credentials-group", group.getDisplayName());
        }
        if (region != null) {
            tags.put("region", region);
        }
        if (isExpired) {
            tags.put("expired", "true");
        }
        entity.setTags(tags);

        return entity;
    }
}
