package com.landvoigtit.stackit.resourceexplorer.storage;

import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StorageResourceMapperTest {

    @Test
    public void testMapToEntityEnrichedMetadataAndPolicyPersistence() {
        final StorageResourceDto dto = new StorageResourceDto();
        dto.setBucketName("my-secure-bucket");
        dto.setRegion("eu01");
        dto.setStorageClass("standard");
        dto.setObjectLockEnabled(true);
        dto.setUrlPathStyle("https://object.storage.eu01.stackit.cloud/my-secure-bucket");
        dto.setUrlVirtualHostedStyle("https://my-secure-bucket.object.storage.eu01.stackit.cloud");

        dto.setIsPublic(true);
        dto.setPublicAccessType("PUBLIC_READ");

        final String rawPolicyJson = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::my-secure-bucket/*\"}]}";
        dto.setBucketPolicy(rawPolicyJson);

        final StorageResourceDto.StorageAclDto acl = new StorageResourceDto.StorageAclDto();
        acl.setOwner("owner-uuid");
        final StorageResourceDto.StorageGrantDto grant = new StorageResourceDto.StorageGrantDto();
        grant.setGrantee("http://acs.amazonaws.com/groups/global/AllUsers");
        grant.setPermission("READ");
        acl.setGrants(List.of(grant));
        dto.setAcl(acl);

        final StorageResourceDto.StorageRetentionDto retention = new StorageResourceDto.StorageRetentionDto();
        retention.setMode("COMPLIANCE");
        retention.setRetentionDays(90);
        retention.setDefaultRetentionSet(true);
        retention.setProjectMaxRetentionDays(365);
        dto.setRetention(retention);

        dto.setSecurityFindings(List.of("PUBLIC_READ_VIA_POLICY", "MISSING_TLS_ENFORCEMENT"));

        // Map to entity
        final StackitEntity entity = StorageResourceMapper.mapToEntity(dto);

        assertNotNull(entity);
        assertEquals("my-secure-bucket", entity.getName());
        assertEquals("storage", entity.getType());
        assertEquals("eu01", entity.getRegion());

        // Verify data JSON map contains policy and enriched attributes
        assertNotNull(entity.getData());
        assertEquals(rawPolicyJson, entity.getData().get("bucketPolicy"));
        assertEquals(true, entity.getData().get("isPublic"));
        assertEquals("PUBLIC_READ", entity.getData().get("publicAccessType"));
        assertNotNull(entity.getData().get("acl"));
        assertNotNull(entity.getData().get("retention"));
        assertNotNull(entity.getData().get("securityFindings"));

        // Verify searchable tags
        assertNotNull(entity.getTags());
        assertEquals("true", entity.getTags().get("is-public"));
        assertEquals("PUBLIC_READ", entity.getTags().get("public-access"));
        assertEquals("compliance", entity.getTags().get("retention-mode"));
    }
}
