package com.landvoigtit.stackit.resourceexplorer.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StorageSecurityEvaluatorTest {

    private static final String ALL_USERS_URI = "http://acs.amazonaws.com/groups/global/AllUsers";
    private static final String AUTH_USERS_URI = "http://acs.amazonaws.com/groups/global/AuthenticatedUsers";

    private static final String TLS_ENFORCING_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Sid": "EnforceTLS",
                  "Effect": "Deny",
                  "Principal": "*",
                  "Action": "s3:*",
                  "Resource": "arn:aws:s3:::my-bucket/*",
                  "Condition": {
                    "Bool": {
                      "aws:SecureTransport": "false"
                    }
                  }
                }
              ]
            }
            """;

    @Test
    public void testPrivateBucketWithNoPublicGrantsAndTlsEnforced() {
        final StorageResourceDto.StorageAclDto acl = new StorageResourceDto.StorageAclDto();
        acl.setOwner("owner-uuid");
        final StorageResourceDto.StorageGrantDto ownerGrant = new StorageResourceDto.StorageGrantDto();
        ownerGrant.setGrantee("owner-uuid");
        ownerGrant.setPermission("FULL_CONTROL");
        acl.setGrants(List.of(ownerGrant));

        final StorageResourceDto.StorageRetentionDto retention = new StorageResourceDto.StorageRetentionDto();
        retention.setMode("COMPLIANCE");
        retention.setDefaultRetentionSet(true);
        retention.setRetentionDays(30);

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(acl, TLS_ENFORCING_POLICY, false, retention);

        assertNotNull(result);
        assertFalse(result.isPublic());
        assertEquals("NOT_PUBLIC", result.publicAccessType());
        assertTrue(result.securityFindings().isEmpty());
    }

    @Test
    public void testPublicReadViaAclAllUsers() {
        final StorageResourceDto.StorageAclDto acl = new StorageResourceDto.StorageAclDto();
        final StorageResourceDto.StorageGrantDto publicGrant = new StorageResourceDto.StorageGrantDto();
        publicGrant.setGrantee(ALL_USERS_URI);
        publicGrant.setPermission("READ");
        acl.setGrants(List.of(publicGrant));

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(acl, null, false, null);

        assertNotNull(result);
        assertTrue(result.isPublic());
        assertEquals("PUBLIC_READ", result.publicAccessType());
        assertTrue(result.securityFindings().contains("PUBLIC_READ_VIA_ACL"));
        assertTrue(result.securityFindings().contains("MISSING_TLS_ENFORCEMENT"));
        assertTrue(result.securityFindings().contains("NO_DEFAULT_RETENTION"));
    }

    @Test
    public void testPublicWriteViaAclAuthenticatedUsers() {
        final StorageResourceDto.StorageAclDto acl = new StorageResourceDto.StorageAclDto();
        final StorageResourceDto.StorageGrantDto authGrant = new StorageResourceDto.StorageGrantDto();
        authGrant.setGrantee(AUTH_USERS_URI);
        authGrant.setPermission("WRITE");
        acl.setGrants(List.of(authGrant));

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(acl, TLS_ENFORCING_POLICY, false, null);

        assertNotNull(result);
        assertTrue(result.isPublic());
        assertEquals("PUBLIC_WRITE", result.publicAccessType());
        assertTrue(result.securityFindings().contains("PUBLIC_WRITE_VIA_ACL"));
        assertFalse(result.securityFindings().contains("MISSING_TLS_ENFORCEMENT"));
    }

    @Test
    public void testPublicReadWriteViaAclFullControl() {
        final StorageResourceDto.StorageAclDto acl = new StorageResourceDto.StorageAclDto();
        final StorageResourceDto.StorageGrantDto grant = new StorageResourceDto.StorageGrantDto();
        grant.setGrantee(ALL_USERS_URI);
        grant.setPermission("FULL_CONTROL");
        acl.setGrants(List.of(grant));

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(acl, TLS_ENFORCING_POLICY, false, null);

        assertNotNull(result);
        assertTrue(result.isPublic());
        assertEquals("PUBLIC_READ_WRITE", result.publicAccessType());
        assertTrue(result.securityFindings().contains("PUBLIC_READ_VIA_ACL"));
        assertTrue(result.securityFindings().contains("PUBLIC_WRITE_VIA_ACL"));
    }

    @Test
    public void testPublicReadViaBucketPolicy() {
        final String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": "s3:GetObject",
                      "Resource": "arn:aws:s3:::my-bucket/*"
                    }
                  ]
                }
                """;

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(null, policy, false, null);

        assertNotNull(result);
        assertTrue(result.isPublic());
        assertEquals("PUBLIC_READ", result.publicAccessType());
        assertTrue(result.securityFindings().contains("PUBLIC_READ_VIA_POLICY"));
        assertTrue(result.securityFindings().contains("MISSING_TLS_ENFORCEMENT"));
    }

    @Test
    public void testPublicWriteViaBucketPolicy() {
        final String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": "*"},
                      "Action": ["s3:PutObject", "s3:DeleteObject"],
                      "Resource": "arn:aws:s3:::my-bucket/*"
                    }
                  ]
                }
                """;

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(null, policy, false, null);

        assertNotNull(result);
        assertTrue(result.isPublic());
        assertEquals("PUBLIC_WRITE", result.publicAccessType());
        assertTrue(result.securityFindings().contains("PUBLIC_WRITE_VIA_POLICY"));
    }

    @Test
    public void testPublicReadWriteCombinedViaPolicy() {
        final String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": ["s3:GetObject", "s3:PutObject"],
                      "Resource": "arn:aws:s3:::my-bucket/*"
                    }
                  ]
                }
                """;

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(null, policy, false, null);

        assertNotNull(result);
        assertTrue(result.isPublic());
        assertEquals("PUBLIC_READ_WRITE", result.publicAccessType());
        assertTrue(result.securityFindings().contains("PUBLIC_READ_VIA_POLICY"));
        assertTrue(result.securityFindings().contains("PUBLIC_WRITE_VIA_POLICY"));
    }

    @Test
    public void testPublicAccessBlockOverridesPublicGrants() {
        final StorageResourceDto.StorageAclDto acl = new StorageResourceDto.StorageAclDto();
        final StorageResourceDto.StorageGrantDto grant = new StorageResourceDto.StorageGrantDto();
        grant.setGrantee(ALL_USERS_URI);
        grant.setPermission("READ");
        acl.setGrants(List.of(grant));

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(acl, null, true, null);

        assertNotNull(result);
        assertFalse(result.isPublic());
        assertEquals("NOT_PUBLIC", result.publicAccessType());
        assertTrue(result.securityFindings().contains("PUBLIC_ACCESS_BLOCKED_BY_CONFIGURATION"));
    }

    @Test
    public void testEvaluateAndEnrichDto() {
        final StorageResourceDto dto = new StorageResourceDto();
        dto.setBucketName("test-bucket");
        final StorageResourceDto.StorageAclDto acl = new StorageResourceDto.StorageAclDto();
        final StorageResourceDto.StorageGrantDto grant = new StorageResourceDto.StorageGrantDto();
        grant.setGrantee(ALL_USERS_URI);
        grant.setPermission("READ");
        acl.setGrants(List.of(grant));
        dto.setAcl(acl);

        StorageSecurityEvaluator.evaluateAndEnrich(dto, false);

        assertTrue(dto.getIsPublic());
        assertEquals("PUBLIC_READ", dto.getPublicAccessType());
        assertNotNull(dto.getSecurityFindings());
        assertTrue(dto.getSecurityFindings().contains("PUBLIC_READ_VIA_ACL"));
    }

    @Test
    public void testUnreadableAclWithNoPolicyYieldsUnknownStatusAndFinding() {
        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(null, null, false, null);

        assertNotNull(result);
        assertNull(result.isPublic());
        assertEquals("UNKNOWN", result.publicAccessType());
        assertTrue(result.securityFindings().contains("ACL_NOT_ACCESSIBLE"));
        assertTrue(result.securityFindings().contains("MISSING_TLS_ENFORCEMENT"));
        assertTrue(result.securityFindings().contains("NO_DEFAULT_RETENTION"));
    }

    @Test
    public void testUnreadableAclWithPublicPolicyYieldsPublicStatusAndUnknownAclFinding() {
        final String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": "s3:GetObject",
                      "Resource": "arn:aws:s3:::my-bucket/*"
                    }
                  ]
                }
                """;

        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(null, policy, false, null);

        assertNotNull(result);
        assertTrue(result.isPublic());
        assertEquals("PUBLIC_READ", result.publicAccessType());
        assertTrue(result.securityFindings().contains("ACL_NOT_ACCESSIBLE"));
        assertTrue(result.securityFindings().contains("PUBLIC_READ_VIA_POLICY"));
    }

    @Test
    public void testUnreadableAclWithPublicAccessBlockYieldsNotPublic() {
        final StorageSecurityEvaluator.SecurityEvaluationResult result =
                StorageSecurityEvaluator.evaluate(null, null, true, null);

        assertNotNull(result);
        assertFalse(result.isPublic());
        assertEquals("NOT_PUBLIC", result.publicAccessType());
    }
}
