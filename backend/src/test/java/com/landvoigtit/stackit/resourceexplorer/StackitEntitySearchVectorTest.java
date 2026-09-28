package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class StackitEntitySearchVectorTest {

    @Inject
    StackitResourceRepository repository;

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    public void testEntityHasSearchVectorPropertyAndPersists() {
        final UUID id = UUID.randomUUID();
        final StackitEntity entity = new StackitEntity();
        entity.setId(id);
        entity.setResourceId("stackit-vm-search-1");
        entity.setName("production-database-node");
        entity.setType("virtual-machine");
        entity.setStatus("RUNNING");
        entity.setRegion("eu01");
        entity.setProjectId("project-fts-test");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setTags(Map.of("environment", "production", "tier", "backend"));
        entity.setData(Map.of("ipAddress", "192.168.1.50", "flavor", "c1.large"));

        repository.persistAndFlush(entity);
        entityManager.clear();

        final StackitEntity retrieved = repository.findById(id);
        assertNotNull(retrieved);

        // Verify search_vector in database via native query
        final Object rawVector = entityManager.createNativeQuery(
                "SELECT search_vector::text FROM stackit_resources WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertNotNull(rawVector, "search_vector column should not be null");
        final String tsvectorStr = rawVector.toString();
        System.out.println("Generated tsvector: " + tsvectorStr);

        System.out.println("retrieved.getSearchVector(): " + retrieved.getSearchVector());

        // Name and resourceId (weight A)
        assertTrue(tsvectorStr.contains("'product':2A"), "Expected token product with weight A");
        assertTrue(tsvectorStr.contains("'databas':3A"), "Expected token databas with weight A");
        assertTrue(tsvectorStr.contains("'search':8A"), "Expected token search with weight A");

        // Type and tags (weight B)
        assertTrue(tsvectorStr.contains("'virtual':"), "Expected token virtual from type");
        assertTrue(tsvectorStr.contains("'environ':"), "Expected token environ from tags");
        assertTrue(tsvectorStr.contains("'backend':"), "Expected token backend from tags");

        // Data payload (weight C)
        assertTrue(tsvectorStr.contains("'192.168.1.50':"), "Expected IP token from data");
        assertTrue(tsvectorStr.contains("'c1.large':"), "Expected c1.large token from data");

        // Verify GIN index definition
        final Object ginIndexDef = entityManager.createNativeQuery(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'stackit_resources' AND indexname = 'idx_stackit_resources_search_vector_gin'")
                .getSingleResult();
        assertNotNull(ginIndexDef, "GIN index should exist");
        assertTrue(ginIndexDef.toString().toLowerCase().contains("using gin"), "Index must use GIN");
    }

    @Test
    @Transactional
    public void testUserBucketDatasetSearch() {
        final UUID id1 = UUID.fromString("7970f389-3f37-3cd8-89b7-e392fc742773");
        final StackitEntity b1 = new StackitEntity();
        b1.setId(id1);
        b1.setResourceId("tstbucket-2-sandbox-1-6gc5dnh7643");
        b1.setName("tstbucket-2-sandbox-1-6gc5dnh7643");
        b1.setType("storage");
        b1.setStatus("AVAILABLE");
        b1.setRegion("eu01");
        b1.setProjectId("f58b4f27-68d7-4bd6-b0f3-2e36a783ad1a");
        b1.setCreatedAt(Instant.now());
        b1.setUpdatedAt(Instant.now());
        b1.setTags(Map.of("is-public", "false", "public-access", "NOT_PUBLIC"));
        b1.setData(Map.of(
                "isPublic", false,
                "storageClass", "standard",
                "urlPathStyle", "https://object.storage.eu01.onstackit.cloud/tstbucket-2-sandbox-1-6gc5dnh7643",
                "publicAccessType", "NOT_PUBLIC",
                "objectLockEnabled", false,
                "urlVirtualHostedStyle", "https://tstbucket-2-sandbox-1-6gc5dnh7643.object.storage.eu01.onstackit.cloud"
        ));
        repository.persistAndFlush(b1);

        final UUID id2 = UUID.fromString("b5ca6a55-1a7b-3e33-acac-137b9d4db74b");
        final StackitEntity b2 = new StackitEntity();
        b2.setId(id2);
        b2.setResourceId("tstbucket-3-sandbox-2-austria-gb64dfgvc");
        b2.setName("tstbucket-3-sandbox-2-austria-gb64dfgvc");
        b2.setType("storage");
        b2.setStatus("AVAILABLE");
        b2.setRegion("eu02");
        b2.setProjectId("c2cc46ae-e432-4e4a-b4df-42a4066c344b");
        b2.setCreatedAt(Instant.now());
        b2.setUpdatedAt(Instant.now());
        b2.setTags(Map.of("is-public", "false", "public-access", "NOT_PUBLIC"));
        b2.setData(Map.of(
                "isPublic", false,
                "storageClass", "standard",
                "urlPathStyle", "https://object.storage.eu02.onstackit.cloud/tstbucket-3-sandbox-2-austria-gb64dfgvc",
                "publicAccessType", "NOT_PUBLIC",
                "objectLockEnabled", false,
                "urlVirtualHostedStyle", "https://tstbucket-3-sandbox-2-austria-gb64dfgvc.object.storage.eu02.onstackit.cloud"
        ));
        repository.persistAndFlush(b2);

        final UUID id3 = UUID.fromString("0cbb2feb-88ef-3cba-ac63-cd8fe555b3a8");
        final StackitEntity b3 = new StackitEntity();
        b3.setId(id3);
        b3.setResourceId("tstbucket-1-sandbox-1-2e36a783ad1a");
        b3.setName("tstbucket-1-sandbox-1-2e36a783ad1a");
        b3.setType("storage");
        b3.setStatus("AVAILABLE");
        b3.setRegion("eu01");
        b3.setProjectId("f58b4f27-68d7-4bd6-b0f3-2e36a783ad1a");
        b3.setCreatedAt(Instant.now());
        b3.setUpdatedAt(Instant.now());
        b3.setTags(Map.of("is-public", "false", "public-access", "NOT_PUBLIC"));
        b3.setData(Map.of(
                "isPublic", false,
                "storageClass", "standard",
                "urlPathStyle", "https://object.storage.eu01.onstackit.cloud/tstbucket-1-sandbox-1-2e36a783ad1a",
                "publicAccessType", "NOT_PUBLIC",
                "objectLockEnabled", false,
                "urlVirtualHostedStyle", "https://tstbucket-1-sandbox-1-2e36a783ad1a.object.storage.eu01.onstackit.cloud"
        ));
        repository.persistAndFlush(b3);

        final Object rawVector = entityManager.createNativeQuery(
                "SELECT search_vector::text FROM stackit_resources WHERE id = :id")
                .setParameter("id", id1)
                .getSingleResult();
        System.out.println("Bucket 1 search_vector: " + rawVector);

        // Search for 'tstbucket'
        final List<StackitEntity> tstbucketResults = repository.search("tstbucket");
        System.out.println("Results for 'tstbucket': " + tstbucketResults.size());
        assertEquals(3, tstbucketResults.stream().filter(e -> e.getName() != null && e.getName().startsWith("tstbucket")).count());

        // Search for 'bucket' - verifies 'bucket' does NOT match 'tstbucket' in FTS
        final List<StackitEntity> bucketResults = repository.search("bucket");
        System.out.println("Results for 'bucket': " + bucketResults.size());
        assertEquals(0, bucketResults.stream().filter(e -> e.getName() != null && e.getName().startsWith("tstbucket")).count());

        // Search for 'sandbox'
        final List<StackitEntity> sandboxResults = repository.search("sandbox");
        System.out.println("Results for 'sandbox': " + sandboxResults.size());
        assertEquals(3, sandboxResults.stream().filter(e -> e.getName() != null && e.getName().startsWith("tstbucket")).count());

        // Search for 'storage'
        final List<StackitEntity> storageResults = repository.search("storage");
        System.out.println("Results for 'storage': " + storageResults.size());
        assertEquals(3, storageResults.stream().filter(e -> e.getName() != null && e.getName().startsWith("tstbucket")).count());

        // Test pg_trgm extension and similarity/wildcard
        entityManager.createNativeQuery("CREATE EXTENSION IF NOT EXISTS pg_trgm").executeUpdate();
        
        // Wildcard / substring query for 'bucket' scoped to this dataset
        final List<?> trgmMatches = entityManager.createNativeQuery(
                "SELECT name, similarity(name, 'bucket'), word_similarity('bucket', name) " +
                "FROM stackit_resources " +
                "WHERE (name ILIKE '%bucket%' OR 'bucket' <% name) AND name LIKE 'tstbucket%'")
                .getResultList();
        System.out.println("pg_trgm / wildcard matches count: " + trgmMatches.size());
        for (final Object obj : trgmMatches) {
            final Object[] row = (Object[]) obj;
            System.out.println("Match: name=" + row[0] + ", sim=" + row[1] + ", word_sim=" + row[2]);
        }
        assertEquals(3, trgmMatches.size());
    }

    @Test
    @Transactional
    public void testSearchUnattachedDisksVsUnattachedPublicIps() {
        final UUID unattachedDiskId = UUID.randomUUID();
        final StackitEntity unattachedDisk = new StackitEntity();
        unattachedDisk.setId(unattachedDiskId);
        unattachedDisk.setResourceId("vol-unattached-101");
        unattachedDisk.setName("data-volume-idle");
        unattachedDisk.setType("vmdisks");
        unattachedDisk.setStatus("AVAILABLE");
        unattachedDisk.setRegion("eu01");
        unattachedDisk.setProjectId("proj-test-1");
        unattachedDisk.setCreatedAt(Instant.now());
        unattachedDisk.setUpdatedAt(Instant.now());
        unattachedDisk.setData(Map.of("attached", false, "attachmentStatus", "UNATTACHED", "sizeGb", 100));
        repository.persistAndFlush(unattachedDisk);

        final UUID attachedDiskId = UUID.randomUUID();
        final StackitEntity attachedDisk = new StackitEntity();
        attachedDisk.setId(attachedDiskId);
        attachedDisk.setResourceId("vol-attached-102");
        attachedDisk.setName("data-volume-bound");
        attachedDisk.setType("vmdisks");
        attachedDisk.setStatus("AVAILABLE");
        attachedDisk.setRegion("eu01");
        attachedDisk.setProjectId("proj-test-1");
        attachedDisk.setCreatedAt(Instant.now());
        attachedDisk.setUpdatedAt(Instant.now());
        attachedDisk.setData(Map.of("attached", true, "attachmentStatus", "ATTACHED", "serverId", "srv-101"));
        repository.persistAndFlush(attachedDisk);

        final UUID unattachedIpId = UUID.randomUUID();
        final StackitEntity unattachedIp = new StackitEntity();
        unattachedIp.setId(unattachedIpId);
        unattachedIp.setResourceId("ip-unattached-201");
        unattachedIp.setName("193.148.160.77");
        unattachedIp.setType("public-ip");
        unattachedIp.setStatus("UNATTACHED");
        unattachedIp.setRegion("eu01");
        unattachedIp.setProjectId("proj-test-1");
        unattachedIp.setCreatedAt(Instant.now());
        unattachedIp.setUpdatedAt(Instant.now());
        unattachedIp.setData(Map.of("attached", false, "attachmentStatus", "UNATTACHED"));
        repository.persistAndFlush(unattachedIp);

        final UUID attachedIpId = UUID.randomUUID();
        final StackitEntity attachedIp = new StackitEntity();
        attachedIp.setId(attachedIpId);
        attachedIp.setResourceId("ip-attached-202");
        attachedIp.setName("193.148.160.88");
        attachedIp.setType("public-ip");
        attachedIp.setStatus("ATTACHED");
        attachedIp.setRegion("eu01");
        attachedIp.setProjectId("proj-test-1");
        attachedIp.setCreatedAt(Instant.now());
        attachedIp.setUpdatedAt(Instant.now());
        attachedIp.setData(Map.of("attached", true, "attachmentStatus", "ATTACHED", "serverId", "srv-102"));
        repository.persistAndFlush(attachedIp);

        // Searching generic 'unattached' matches BOTH unattached disks and unattached public-ips
        final List<StackitEntity> genericUnattached = repository.search("unattached");
        assertTrue(genericUnattached.stream().anyMatch(e -> e.getId().equals(unattachedDiskId)), "generic unattached must match unattached disk");
        assertTrue(genericUnattached.stream().anyMatch(e -> e.getId().equals(unattachedIpId)), "generic unattached must match unattached public IP");

        // Searching 'unattached vmdisks' matches ONLY unattached disks
        final List<StackitEntity> unattachedDisks = repository.search("unattached vmdisks");
        assertTrue(unattachedDisks.stream().anyMatch(e -> e.getId().equals(unattachedDiskId)), "unattached vmdisks must match unattached disk");
        assertFalse(unattachedDisks.stream().anyMatch(e -> e.getId().equals(attachedDiskId)), "unattached vmdisks must not match attached disk");
        assertFalse(unattachedDisks.stream().anyMatch(e -> e.getId().equals(unattachedIpId)), "unattached vmdisks must NOT match unattached public IP");
        assertFalse(unattachedDisks.stream().anyMatch(e -> e.getId().equals(attachedIpId)), "unattached vmdisks must not match attached public IP");

        // Searching 'unattached public-ip' matches ONLY unattached public IPs
        final List<StackitEntity> unattachedIps = repository.search("unattached public-ip");
        assertTrue(unattachedIps.stream().anyMatch(e -> e.getId().equals(unattachedIpId)), "unattached public-ip must match unattached public IP");
        assertFalse(unattachedIps.stream().anyMatch(e -> e.getId().equals(attachedIpId)), "unattached public-ip must not match attached public IP");
        assertFalse(unattachedIps.stream().anyMatch(e -> e.getId().equals(unattachedDiskId)), "unattached public-ip must NOT match unattached disk");
        assertFalse(unattachedIps.stream().anyMatch(e -> e.getId().equals(attachedDiskId)), "unattached public-ip must not match attached disk");
    }
}
