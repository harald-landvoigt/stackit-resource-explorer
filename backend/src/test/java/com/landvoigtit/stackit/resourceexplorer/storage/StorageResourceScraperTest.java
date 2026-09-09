package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v2api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v2api.model.Bucket;
import cloud.stackit.sdk.objectstorage.v2api.model.ComplianceLockResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.DefaultRetentionResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.ListBucketsResponse;
import cloud.stackit.sdk.objectstorage.v2api.model.RetentionMode;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StorageResourceScraperTest {

    private ObjectStorageApi objectStorageApi;
    private StackitProjectDiscoveryService projectDiscoveryService;
    private StackitResourceRepository repository;
    private StackitSdkConfig sdkConfig;
    private S3JitKeyManager s3JitKeyManager;
    private StorageResourceScraper scraper;
    private Validator validator;

    private static final String PROJECT_ID = "p-12345";
    private static final String REGION = "eu01";
    private static final String BUCKET_NAME = "secure-data-bucket";

    @BeforeEach
    public void setup() throws Exception {
        objectStorageApi = mock(ObjectStorageApi.class);
        projectDiscoveryService = mock(StackitProjectDiscoveryService.class);
        repository = mock(StackitResourceRepository.class);
        sdkConfig = mock(StackitSdkConfig.class);
        s3JitKeyManager = mock(S3JitKeyManager.class);
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        scraper = new StorageResourceScraper();
        setField(scraper, "objectStorageApi", objectStorageApi);
        setField(scraper, "projectDiscoveryService", projectDiscoveryService);
        setField(scraper, "repository", repository);
        setField(scraper, "sdkConfig", sdkConfig);
        setField(scraper, "s3JitKeyManager", s3JitKeyManager);
        setField(scraper, "validator", validator);

        final Project project = new Project();
        project.setProjectId(UUID.nameUUIDFromBytes(PROJECT_ID.getBytes()));
        when(projectDiscoveryService.discoverProjects()).thenReturn(List.of(project));
        when(sdkConfig.getRegions()).thenReturn(List.of(REGION));

        final ListBucketsResponse bucketsResponse = new ListBucketsResponse();
        final Bucket bucket = new Bucket();
        bucket.setName(BUCKET_NAME);
        bucket.setRegion(REGION);
        bucket.setObjectLockEnabled(true);
        bucket.setUrlPathStyle("https://object.storage.eu01.stackit.cloud/" + BUCKET_NAME);
        bucket.setUrlVirtualHostedStyle("https://" + BUCKET_NAME + ".object.storage.eu01.stackit.cloud");
        bucketsResponse.setBuckets(List.of(bucket));

        when(objectStorageApi.listBuckets(anyString(), eq(REGION))).thenReturn(bucketsResponse);
    }

    private void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testScrapeProjectStorageWithS3EnrichmentSuccess() throws Exception {
        // Mock retention
        final DefaultRetentionResponse retentionResponse = new DefaultRetentionResponse();
        retentionResponse.setBucket(BUCKET_NAME);
        retentionResponse.setDays(90);
        retentionResponse.setMode(RetentionMode.COMPLIANCE);
        when(objectStorageApi.getDefaultRetention(anyString(), eq(REGION), eq(BUCKET_NAME))).thenReturn(retentionResponse);

        final ComplianceLockResponse lockResponse = new ComplianceLockResponse();
        lockResponse.setMaxRetentionDays(365);
        when(objectStorageApi.getComplianceLock(anyString(), eq(REGION))).thenReturn(lockResponse);

        // Mock S3 client and JIT manager
        final S3Client s3Client = mock(S3Client.class);
        final String rawPolicy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"*\"}]}";
        final GetBucketPolicyResponse policyResponse = GetBucketPolicyResponse.builder().policy(rawPolicy).build();
        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class))).thenReturn(policyResponse);

        final Grant grant = Grant.builder()
                .grantee(Grantee.builder().uri("http://acs.amazonaws.com/groups/global/AllUsers").type(Type.GROUP).build())
                .permission(Permission.READ)
                .build();
        final GetBucketAclResponse aclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder().displayName("owner-1").id("id-1").build())
                .grants(List.of(grant))
                .build();
        when(s3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(aclResponse);

        when(s3JitKeyManager.withEphemeralClient(anyString(), eq(REGION), any()))
                .thenAnswer(invocation -> {
                    final S3JitKeyManager.S3ClientFunction<?> fn = invocation.getArgument(2);
                    return fn.apply(s3Client);
                });

        scraper.scrape();

        final ArgumentCaptor<StackitEntity> entityCaptor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repository, atLeastOnce()).persistOrUpdate(entityCaptor.capture());

        final StackitEntity entity = entityCaptor.getValue();
        assertNotNull(entity);
        assertEquals(BUCKET_NAME, entity.getName());
        assertEquals("storage", entity.getType());

        // Verify enriched data
        assertNotNull(entity.getData());
        assertEquals(rawPolicy, entity.getData().get("bucketPolicy"));
        assertEquals(true, entity.getData().get("isPublic"));
        assertEquals("PUBLIC_READ", entity.getData().get("publicAccessType"));

        // Verify tags
        assertNotNull(entity.getTags());
        assertEquals("true", entity.getTags().get("is-public"));
        assertEquals("PUBLIC_READ", entity.getTags().get("public-access"));
        assertEquals("compliance", entity.getTags().get("retention-mode"));
    }

    @Test
    public void testScrapeProjectStorageHandlesS3PermissionForbiddenGracefully() throws Exception {
        when(s3JitKeyManager.withEphemeralClient(anyString(), eq(REGION), any()))
                .thenThrow(new RuntimeException("403 Forbidden: Insufficient permissions for JIT credentials group"));

        scraper.scrape();

        final ArgumentCaptor<StackitEntity> entityCaptor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repository, atLeastOnce()).persistOrUpdate(entityCaptor.capture());

        final StackitEntity entity = entityCaptor.getValue();
        assertNotNull(entity);
        assertEquals(BUCKET_NAME, entity.getName());
        assertNull(entity.getData().get("bucketPolicy"));
        assertNull(entity.getData().get("isPublic"));
        assertEquals("UNKNOWN", entity.getData().get("publicAccessType"));
        assertNotNull(entity.getTags());
        assertEquals("unknown", entity.getTags().get("is-public"));
        assertEquals("UNKNOWN", entity.getTags().get("public-access"));
        
        @SuppressWarnings("unchecked")
        final List<String> findings = (List<String>) entity.getData().get("securityFindings");
        assertNotNull(findings);
        assertTrue(findings.contains("ACL_NOT_ACCESSIBLE"));
    }

    @Test
    public void testScrapeProjectStorageHandlesAclAccessDeniedGracefully() throws Exception {
        final S3Client s3Client = mock(S3Client.class);
        final S3Exception accessDenied = (S3Exception) S3Exception.builder()
                .statusCode(403)
                .message("Access Denied")
                .build();
        when(s3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenThrow(accessDenied);

        when(s3JitKeyManager.withEphemeralClient(anyString(), eq(REGION), any()))
                .thenAnswer(invocation -> {
                    final S3JitKeyManager.S3ClientFunction<?> fn = invocation.getArgument(2);
                    return fn.apply(s3Client);
                });

        scraper.scrape();

        final ArgumentCaptor<StackitEntity> entityCaptor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repository, atLeastOnce()).persistOrUpdate(entityCaptor.capture());

        final StackitEntity entity = entityCaptor.getValue();
        assertNotNull(entity);
        assertNull(entity.getData().get("isPublic"));
        assertEquals("UNKNOWN", entity.getData().get("publicAccessType"));
        assertEquals("unknown", entity.getTags().get("is-public"));
        
        @SuppressWarnings("unchecked")
        final List<String> findings = (List<String>) entity.getData().get("securityFindings");
        assertNotNull(findings);
        assertTrue(findings.contains("ACL_NOT_ACCESSIBLE"));
    }

    @Test
    public void testScrapeProjectStorageHandlesNoSuchBucketPolicyGracefully() throws Exception {
        final S3Client s3Client = mock(S3Client.class);
        final S3Exception noSuchPolicyException = (S3Exception) S3Exception.builder()
                .statusCode(404)
                .message("The bucket policy does not exist")
                .build();
        when(s3Client.getBucketPolicy(any(GetBucketPolicyRequest.class)))
                .thenThrow(noSuchPolicyException);

        final GetBucketAclResponse aclResponse = GetBucketAclResponse.builder()
                .owner(Owner.builder().displayName("owner-1").id("id-1").build())
                .grants(List.of())
                .build();
        when(s3Client.getBucketAcl(any(GetBucketAclRequest.class))).thenReturn(aclResponse);

        when(s3JitKeyManager.withEphemeralClient(anyString(), eq(REGION), any()))
                .thenAnswer(invocation -> {
                    final S3JitKeyManager.S3ClientFunction<?> fn = invocation.getArgument(2);
                    return fn.apply(s3Client);
                });

        scraper.scrape();

        final ArgumentCaptor<StackitEntity> entityCaptor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repository, atLeastOnce()).persistOrUpdate(entityCaptor.capture());

        final StackitEntity entity = entityCaptor.getValue();
        assertNotNull(entity);
        assertNull(entity.getData().get("bucketPolicy"));
    }
}
