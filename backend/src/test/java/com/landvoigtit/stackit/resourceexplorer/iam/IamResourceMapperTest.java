package com.landvoigtit.stackit.resourceexplorer.iam;

import cloud.stackit.sdk.objectstorage.v2api.model.AccessKey;
import cloud.stackit.sdk.objectstorage.v2api.model.CredentialsGroup;
import cloud.stackit.sdk.resourcemanager.v0api.model.Member;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

public class IamResourceMapperTest {

    @Test
    public void testMapToDto() {
        final Member member = new Member();
        member.setSubject("user@example.com");
        member.setRole("project.owner");

        final IamResourceDto dto = IamResourceMapper.mapToDto(member);
        assertNotNull(dto);
        assertEquals("user@example.com", dto.getMemberId());
        assertEquals("project.owner", dto.getRole());

        assertNull(IamResourceMapper.mapToDto(null));
    }

    @Test
    public void testMapToEntity() {
        final IamResourceDto dto = new IamResourceDto();
        dto.setMemberId("service-account@sa.stackit.cloud");
        dto.setRole("reader");

        final StackitEntity entity = IamResourceMapper.mapToEntity(dto);
        assertNotNull(entity);
        assertEquals("service-account@sa.stackit.cloud", entity.getResourceId());
        assertEquals("service-account@sa.stackit.cloud", entity.getName());
        assertEquals("iam", entity.getType());
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals("global", entity.getRegion());
        assertEquals("reader", entity.getData().get("role"));

        assertNull(IamResourceMapper.mapToEntity(null));
    }

    @Test
    public void testMapS3KeyToEntity_ActiveKey() {
        final CredentialsGroup group = new CredentialsGroup();
        group.setCredentialsGroupId("group-uuid-123");
        group.setDisplayName("backup-credentials");
        group.setUrn("urn:stackit:objectstorage:eu01:project-uuid:credentialsgroup:group-uuid-123");

        final AccessKey key = new AccessKey();
        key.setKeyId("SGKHAv6mpJOnGfwhwvyGyzA7fJFUWevP2pyFzoB9Fw==");
        key.setDisplayName("backup-pipeline-s3");
        // Expires in 30 days
        final String futureExpiration = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        key.setExpires(futureExpiration);

        final StackitEntity entity = IamResourceMapper.mapS3KeyToEntity("test-project-id", "eu01", group, key);
        assertNotNull(entity);
        assertEquals("SGKHAv6mpJOnGfwhwvyGyzA7fJFUWevP2pyFzoB9Fw==", entity.getResourceId());
        assertEquals("backup-pipeline-s3", entity.getName());
        assertEquals("iam", entity.getType());
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals("eu01", entity.getRegion());
        assertEquals("test-project-id", entity.getProjectId());

        // Data verification
        assertEquals("S3 Access Key", entity.getData().get("identityType"));
        assertEquals("S3 HMAC Key", entity.getData().get("authScheme"));
        assertEquals("S3 Data Plane", entity.getData().get("authFlow"));
        assertEquals("SGKHAv6mpJOnGfwhwvyGyzA7fJFUWevP2pyFzoB9Fw==", entity.getData().get("keyId"));
        assertEquals("backup-pipeline-s3", entity.getData().get("displayName"));
        assertEquals("group-uuid-123", entity.getData().get("credentialsGroupId"));
        assertEquals("backup-credentials", entity.getData().get("credentialsGroupName"));
        assertEquals("urn:stackit:objectstorage:eu01:project-uuid:credentialsgroup:group-uuid-123", entity.getData().get("credentialsGroupUrn"));
        assertEquals("eu01", entity.getData().get("region"));
        assertEquals(futureExpiration, entity.getData().get("expires"));
        assertEquals(false, entity.getData().get("expired"));

        // Tags verification
        assertEquals("s3-access-key", entity.getTags().get("identity-type"));
        assertEquals("s3-hmac-key", entity.getTags().get("auth-scheme"));
        assertEquals("backup-credentials", entity.getTags().get("credentials-group"));
        assertEquals("eu01", entity.getTags().get("region"));
        assertNull(entity.getTags().get("expired"));
    }

    @Test
    public void testMapS3KeyToEntity_ExpiredKey() {
        final CredentialsGroup group = new CredentialsGroup();
        group.setCredentialsGroupId("group-uuid-456");
        group.setDisplayName("legacy-credentials");

        final AccessKey key = new AccessKey();
        key.setKeyId("EXPIRED_KEY_123");
        key.setDisplayName("old-key");
        key.setExpires("2020-01-01T00:00:00Z");

        final StackitEntity entity = IamResourceMapper.mapS3KeyToEntity("test-project-id", "eu02", group, key);
        assertNotNull(entity);
        assertEquals("EXPIRED", entity.getStatus());
        assertEquals(true, entity.getData().get("expired"));
        assertEquals("true", entity.getTags().get("expired"));
    }

    @Test
    public void testMapS3KeyToEntity_DisplayNameFallback() {
        final CredentialsGroup group = new CredentialsGroup();
        group.setCredentialsGroupId("group-uuid-789");

        final AccessKey key = new AccessKey();
        key.setKeyId("SGKH_NO_NAME_KEY");
        key.setDisplayName(""); // blank display name

        final StackitEntity entity = IamResourceMapper.mapS3KeyToEntity("test-project-id", "eu01", group, key);
        assertNotNull(entity);
        assertEquals("SGKH_NO_NAME_KEY", entity.getName());
    }

    @Test
    public void testMapS3KeyToEntity_NullChecks() {
        assertNull(IamResourceMapper.mapS3KeyToEntity("proj", "eu01", null, null));
        final AccessKey keyWithoutId = new AccessKey();
        assertNull(IamResourceMapper.mapS3KeyToEntity("proj", "eu01", null, keyWithoutId));
    }

    @Test
    public void testMapS3KeyToEntity_NeverExpires() {
        final CredentialsGroup group = new CredentialsGroup();
        group.setCredentialsGroupId("group-uuid-never");
        group.setDisplayName("credgroup-sbx1");

        final AccessKey key = new AccessKey();
        key.setKeyId("POPF");
        key.setDisplayName("my-never-expiring-key");
        key.setExpires(null);

        final StackitEntity entity = IamResourceMapper.mapS3KeyToEntity("test-project-id", "eu01", group, key);
        assertNotNull(entity);
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals("Never", entity.getData().get("expires"));
        assertEquals(false, entity.getData().get("expired"));
        assertNull(entity.getTags().get("expired"));
    }

    @Test
    public void testDeserializeAccessKey_WithNullExpires() throws Exception {
        final String json = "{\"keyId\":\"POPF\",\"displayName\":\"my-key\",\"expires\":null}";
        final AccessKey key = AccessKey.fromJson(json);
        assertNotNull(key);
        assertEquals("POPF", key.getKeyId());
        assertEquals("my-key", key.getDisplayName());
        assertNull(key.getExpires());
    }
}
