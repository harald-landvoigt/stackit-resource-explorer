package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v2api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateAccessKeyPayload;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateAccessKeyResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateCredentialsGroupPayload;
import cloud.stackit.sdk.objectstorage.v2api.model.CreateCredentialsGroupResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.CredentialsGroup;
import cloud.stackit.sdk.objectstorage.v2api.model.DeleteAccessKeyResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.ListCredentialsGroupsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class S3JitKeyManagerTest {

    private ObjectStorageApi objectStorageApi;
    private S3JitKeyManager jitKeyManager;

    private static final String PROJECT_ID = "test-project-uuid";
    private static final String REGION = "eu01";
    private static final String GROUP_ID = "cg-test-123";
    private static final String KEY_ID = "key-test-456";
    private static final String ACCESS_KEY = "A3KTESTTESTTEST";
    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYTESTKEY";

    @BeforeEach
    public void setup() throws Exception {
        objectStorageApi = mock(ObjectStorageApi.class);
        jitKeyManager = new S3JitKeyManager(objectStorageApi, "https://object.storage.%s.onstackit.cloud");

        // Mock Credentials Group lookup / creation
        final ListCredentialsGroupsResponse listGroupsResp = new ListCredentialsGroupsResponse();
        listGroupsResp.setCredentialsGroups(List.of());
        when(objectStorageApi.listCredentialsGroups(PROJECT_ID, REGION)).thenReturn(listGroupsResp);

        final CreateCredentialsGroupResponse createGroupResp = new CreateCredentialsGroupResponse();
        final CredentialsGroup createdGroup = new CredentialsGroup();
        createdGroup.setCredentialsGroupId(GROUP_ID);
        createdGroup.setDisplayName(S3JitKeyManager.AUDIT_CREDENTIALS_GROUP_NAME);
        createGroupResp.setCredentialsGroup(createdGroup);
        when(objectStorageApi.createCredentialsGroup(eq(PROJECT_ID), eq(REGION), any(CreateCredentialsGroupPayload.class)))
                .thenReturn(createGroupResp);

        // Mock S3 Access Key creation
        final CreateAccessKeyResponse createKeyResp = new CreateAccessKeyResponse();
        createKeyResp.setKeyId(KEY_ID);
        createKeyResp.setAccessKey(ACCESS_KEY);
        createKeyResp.setSecretAccessKey(SECRET_KEY);
        when(objectStorageApi.createAccessKey(eq(PROJECT_ID), eq(REGION), any(CreateAccessKeyPayload.class), eq(GROUP_ID)))
                .thenReturn(createKeyResp);

        // Mock S3 Access Key deletion
        when(objectStorageApi.deleteAccessKey(eq(PROJECT_ID), eq(REGION), eq(KEY_ID), eq(GROUP_ID)))
                .thenReturn(new DeleteAccessKeyResponse());
    }

    @Test
    public void testEphemeralSessionLifecycleSuccess() throws Exception {
        try (final S3JitKeyManager.EphemeralS3Session session = jitKeyManager.createEphemeralSession(PROJECT_ID, REGION)) {
            assertNotNull(session, "Session should not be null");
            assertEquals(KEY_ID, session.getKeyId());
            assertEquals(GROUP_ID, session.getCredentialsGroupId());

            final S3Client s3Client = session.getS3Client();
            assertNotNull(s3Client, "S3Client should be instantiated");
        }

        // Verify that deleteAccessKey was called upon session.close()
        verify(objectStorageApi, times(1)).deleteAccessKey(PROJECT_ID, REGION, KEY_ID, GROUP_ID);
    }

    @Test
    public void testWithEphemeralClientExecutesAndGuaranteesCleanupOnException() throws Exception {
        final RuntimeException simulatedError = new RuntimeException("Simulated S3 failure");

        assertThrows(RuntimeException.class, () -> {
            jitKeyManager.withEphemeralClient(PROJECT_ID, REGION, client -> {
                assertNotNull(client);
                throw simulatedError;
            });
        });

        // Verify cleanup still occurred even though an exception was thrown
        verify(objectStorageApi, times(1)).deleteAccessKey(PROJECT_ID, REGION, KEY_ID, GROUP_ID);
    }

    @Test
    public void testReusesExistingAuditCredentialsGroupIfPresent() throws Exception {
        final ListCredentialsGroupsResponse listGroupsResp = new ListCredentialsGroupsResponse();
        final CredentialsGroup existingGroup = new CredentialsGroup();
        existingGroup.setCredentialsGroupId("existing-cg-789");
        existingGroup.setDisplayName(S3JitKeyManager.AUDIT_CREDENTIALS_GROUP_NAME);
        listGroupsResp.setCredentialsGroups(List.of(existingGroup));

        when(objectStorageApi.listCredentialsGroups(PROJECT_ID, REGION)).thenReturn(listGroupsResp);

        final CreateAccessKeyResponse createKeyResp = new CreateAccessKeyResponse();
        createKeyResp.setKeyId("key-999");
        createKeyResp.setAccessKey("AKIA999");
        createKeyResp.setSecretAccessKey("SECRET999");
        when(objectStorageApi.createAccessKey(eq(PROJECT_ID), eq(REGION), any(CreateAccessKeyPayload.class), eq("existing-cg-789")))
                .thenReturn(createKeyResp);

        try (final S3JitKeyManager.EphemeralS3Session session = jitKeyManager.createEphemeralSession(PROJECT_ID, REGION)) {
            assertEquals("existing-cg-789", session.getCredentialsGroupId());
            assertEquals("key-999", session.getKeyId());
        }

        // Verify createCredentialsGroup was NOT called
        verify(objectStorageApi, never()).createCredentialsGroup(anyString(), anyString(), any());
        // Verify key was deleted from the existing group
        verify(objectStorageApi, times(1)).deleteAccessKey(PROJECT_ID, REGION, "key-999", "existing-cg-789");
    }
}
