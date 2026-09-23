package com.landvoigtit.stackit.resourceexplorer.iam;

import cloud.stackit.sdk.core.exception.ApiException;
import cloud.stackit.sdk.objectstorage.v2api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v2api.model.AccessKey;
import cloud.stackit.sdk.objectstorage.v2api.model.CredentialsGroup;
import cloud.stackit.sdk.objectstorage.v2api.model.ListAccessKeysResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.ListCredentialsGroupsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IamResourceScraperUnitTest {

    private ObjectStorageApi objectStorageApi;
    private StackitResourceRepository repository;
    private StackitSdkConfig sdkConfig;
    private StackitProjectDiscoveryService projectDiscoveryService;
    private OkHttpClient httpClient;
    private IamResourceScraper scraper;
    private Validator validator;

    private static final String PROJECT_ID = "45c62ede-0f24-3670-9181-946b0927720f";
    private static final String REGION = "eu01";

    @BeforeEach
    public void setup() throws Exception {
        objectStorageApi = mock(ObjectStorageApi.class);
        repository = mock(StackitResourceRepository.class);
        sdkConfig = mock(StackitSdkConfig.class);
        projectDiscoveryService = mock(StackitProjectDiscoveryService.class);
        httpClient = mock(OkHttpClient.class);
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        scraper = new IamResourceScraper();
        setField(scraper, "objectStorageApi", objectStorageApi);
        setField(scraper, "repository", repository);
        setField(scraper, "sdkConfig", sdkConfig);
        setField(scraper, "projectDiscoveryService", projectDiscoveryService);
        setField(scraper, "httpClient", httpClient);
        setField(scraper, "validator", validator);
        setField(scraper, "objectMapper", new ObjectMapper());

        when(sdkConfig.getRegions()).thenReturn(List.of(REGION));
    }

    private void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testScrapeProjectS3AccessKeys_Success() throws Exception {
        // Prepare mock credentials group
        final CredentialsGroup group = new CredentialsGroup();
        group.setCredentialsGroupId("cg-backup-123");
        group.setDisplayName("backup-credentials");
        group.setUrn("urn:stackit:objectstorage:eu01:project:credentialsgroup:cg-backup-123");

        final ListCredentialsGroupsResponse groupsResp = new ListCredentialsGroupsResponse();
        groupsResp.setCredentialsGroups(List.of(group));
        when(objectStorageApi.listCredentialsGroups(PROJECT_ID, REGION)).thenReturn(groupsResp);

        // Prepare 2 keys: 1 active, 1 expired
        final AccessKey activeKey = new AccessKey();
        activeKey.setKeyId("KEY_ACTIVE_1");
        activeKey.setDisplayName("app-backup-key");
        activeKey.setExpires(Instant.now().plus(30, ChronoUnit.DAYS).toString());

        final AccessKey expiredKey = new AccessKey();
        expiredKey.setKeyId("KEY_EXPIRED_2");
        expiredKey.setDisplayName("legacy-key");
        expiredKey.setExpires("2021-01-01T00:00:00Z");

        final ListAccessKeysResponse keysResp = new ListAccessKeysResponse();
        keysResp.setAccessKeys(List.of(activeKey, expiredKey));
        when(objectStorageApi.listAccessKeys(PROJECT_ID, REGION, "cg-backup-123")).thenReturn(keysResp);

        final List<String> currentResourceIds = new ArrayList<>();

        // Call private scrapeProjectS3AccessKeys via reflection
        final Method method = IamResourceScraper.class.getDeclaredMethod("scrapeProjectS3AccessKeys", String.class, List.class);
        method.setAccessible(true);
        final boolean result = (boolean) method.invoke(scraper, PROJECT_ID, currentResourceIds);

        assertTrue(result);
        assertEquals(2, currentResourceIds.size());
        assertTrue(currentResourceIds.contains("KEY_ACTIVE_1"));
        assertTrue(currentResourceIds.contains("KEY_EXPIRED_2"));

        final ArgumentCaptor<StackitEntity> captor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repository, times(2)).persistOrUpdate(captor.capture());

        final List<StackitEntity> persisted = captor.getAllValues();

        final StackitEntity activeEntity = persisted.stream()
                .filter(e -> "KEY_ACTIVE_1".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(activeEntity);
        assertEquals("app-backup-key", activeEntity.getName());
        assertEquals("iam", activeEntity.getType());
        assertEquals("ACTIVE", activeEntity.getStatus());
        assertEquals(REGION, activeEntity.getRegion());
        assertEquals("S3 Access Key", activeEntity.getData().get("identityType"));
        assertEquals("S3 HMAC Key", activeEntity.getData().get("authScheme"));
        assertEquals("backup-credentials", activeEntity.getData().get("credentialsGroupName"));
        assertEquals(false, activeEntity.getData().get("expired"));

        final StackitEntity expiredEntity = persisted.stream()
                .filter(e -> "KEY_EXPIRED_2".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(expiredEntity);
        assertEquals("legacy-key", expiredEntity.getName());
        assertEquals("EXPIRED", expiredEntity.getStatus());
        assertEquals(true, expiredEntity.getData().get("expired"));
        assertEquals("true", expiredEntity.getTags().get("expired"));
    }

    @Test
    public void testScrapeProjectS3AccessKeys_SkipsAuditGroup() throws Exception {
        final CredentialsGroup auditGroup = new CredentialsGroup();
        auditGroup.setCredentialsGroupId("cg-audit-999");
        auditGroup.setDisplayName("resource-explorer-audit");

        final ListCredentialsGroupsResponse groupsResp = new ListCredentialsGroupsResponse();
        groupsResp.setCredentialsGroups(List.of(auditGroup));
        when(objectStorageApi.listCredentialsGroups(PROJECT_ID, REGION)).thenReturn(groupsResp);

        final List<String> currentResourceIds = new ArrayList<>();
        final Method method = IamResourceScraper.class.getDeclaredMethod("scrapeProjectS3AccessKeys", String.class, List.class);
        method.setAccessible(true);
        final boolean result = (boolean) method.invoke(scraper, PROJECT_ID, currentResourceIds);

        assertTrue(result);
        assertTrue(currentResourceIds.isEmpty());
        verify(objectStorageApi, never()).listAccessKeys(anyString(), anyString(), anyString());
        verify(repository, never()).persistOrUpdate(any());
    }

    @Test
    public void testScrapeProjectS3AccessKeys_Handles403And404Gracefully() throws Exception {
        when(objectStorageApi.listCredentialsGroups(PROJECT_ID, REGION))
                .thenThrow(new RuntimeException("403 Forbidden: Insufficient permissions for credentials groups"));

        final List<String> currentResourceIds = new ArrayList<>();
        final Method method = IamResourceScraper.class.getDeclaredMethod("scrapeProjectS3AccessKeys", String.class, List.class);
        method.setAccessible(true);
        final boolean result = (boolean) method.invoke(scraper, PROJECT_ID, currentResourceIds);

        assertTrue(result);
        assertTrue(currentResourceIds.isEmpty());
        verify(repository, never()).persistOrUpdate(any());
    }

    @Test
    public void testScrapeProjectS3AccessKeys_NullObjectStorageApi() throws Exception {
        setField(scraper, "objectStorageApi", null);

        final List<String> currentResourceIds = new ArrayList<>();
        final Method method = IamResourceScraper.class.getDeclaredMethod("scrapeProjectS3AccessKeys", String.class, List.class);
        method.setAccessible(true);
        final boolean result = (boolean) method.invoke(scraper, PROJECT_ID, currentResourceIds);

        assertTrue(result);
    }
}
