package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class StackitResourceRepositoryTest {

    @Inject
    StackitResourceRepository repository;

    @Test
    @Transactional
    public final void testSaveAndRetrieve() {
        final StackitEntity resource = new StackitEntity();
        final UUID id = UUID.randomUUID();
        resource.setId(id);
        resource.setResourceId("stackit-vm-12345");
        resource.setName("production-web-server");
        resource.setType("virtual-machine");
        resource.setStatus("RUNNING");
        resource.setRegion("eu-west-1");
        resource.setProjectId("project-abc");
        resource.setCreatedAt(Instant.now());
        resource.setUpdatedAt(Instant.now());
        resource.setTags(Map.of("env", "production", "owner", "devops"));
        resource.setData(Map.of("size", "t3.medium", "os", "linux"));

        repository.persist(resource);

        final StackitEntity retrieved = repository.findById(id);
        assertNotNull(retrieved);
        assertEquals("stackit-vm-12345", retrieved.getResourceId());
        assertEquals("production-web-server", retrieved.getName());
        assertEquals("virtual-machine", retrieved.getType());
        assertEquals("RUNNING", retrieved.getStatus());
        assertEquals("eu-west-1", retrieved.getRegion());
        assertEquals("project-abc", retrieved.getProjectId());
        assertEquals("production", retrieved.getTags().get("env"));
        assertEquals("t3.medium", retrieved.getData().get("size"));
        assertNull(retrieved.getDeletedAt());
    }

    @Test
    @Transactional
    public final void testSoftDeleteAndHardDelete() {
        final StackitEntity resource = new StackitEntity();
        final UUID id = UUID.randomUUID();
        resource.setId(id);
        resource.setResourceId("stackit-vm-999");
        resource.setName("test-delete-server");
        resource.setType("virtual-machine");
        resource.setStatus("RUNNING");
        resource.setRegion("eu-west-1");
        resource.setProjectId("project-abc");
        resource.setCreatedAt(Instant.now());
        resource.setUpdatedAt(Instant.now());

        repository.persist(resource);

        // Retrieve and verify
        final StackitEntity retrieved = repository.findById(id);
        assertNotNull(retrieved);
        assertNull(retrieved.getDeletedAt());

        // Perform Soft Delete
        final Instant softDeletedTime = Instant.now();
        retrieved.setDeletedAt(softDeletedTime);
        repository.persist(retrieved);

        // Retrieve and verify soft deleted
        final StackitEntity softDeleted = repository.findById(id);
        assertNotNull(softDeleted);
        assertEquals(softDeletedTime, softDeleted.getDeletedAt());

        // Perform Hard Delete
        repository.delete(softDeleted);

        // Retrieve and verify hard deleted (not found)
        final StackitEntity hardDeleted = repository.findById(id);
        assertNull(hardDeleted);
    }

    @Test
    @Transactional
    public void testSearchByNameAndResourceId() {
        final StackitEntity e1 = new StackitEntity();
        e1.setId(UUID.randomUUID());
        e1.setResourceId("stackit-k8s-cluster-alpha");
        e1.setName("production-cluster-01");
        e1.setType("ske");
        e1.setStatus("READY");
        e1.setRegion("eu01");
        e1.setProjectId("project-alpha");
        e1.setCreatedAt(Instant.now());
        e1.setUpdatedAt(Instant.now());
        repository.persistAndFlush(e1);

        final List<StackitEntity> resultsByName = repository.search("production cluster");
        assertFalse(resultsByName.isEmpty());
        assertEquals(e1.getId(), resultsByName.get(0).getId());

        final List<StackitEntity> resultsById = repository.search("cluster alpha");
        assertFalse(resultsById.isEmpty());
        assertEquals(e1.getId(), resultsById.get(0).getId());

        // Direct token search
        final List<StackitEntity> resultsByToken = repository.search("alpha");
        assertFalse(resultsByToken.isEmpty());
        assertEquals(e1.getId(), resultsByToken.get(0).getId());
    }

    @Test
    @Transactional
    public void testSearchByTagsAndDataPayload() {
        final StackitEntity e1 = new StackitEntity();
        e1.setId(UUID.randomUUID());
        e1.setResourceId("storage-bucket-zeta");
        e1.setName("backup-store");
        e1.setType("object-storage");
        e1.setStatus("ACTIVE");
        e1.setRegion("eu01");
        e1.setProjectId("proj-zeta");
        e1.setCreatedAt(Instant.now());
        e1.setUpdatedAt(Instant.now());
        e1.setTags(Map.of("compliance", "gdpr", "lifecycle", "archival"));
        e1.setData(Map.of("retentionDays", "365", "tier", "cold-storage"));
        repository.persistAndFlush(e1);

        // Tag search
        final List<StackitEntity> tagResults = repository.search("gdpr");
        assertFalse(tagResults.isEmpty());
        assertEquals(e1.getId(), tagResults.get(0).getId());

        // Data payload search
        final List<StackitEntity> dataResults = repository.search("cold-storage");
        assertFalse(dataResults.isEmpty());
        assertEquals(e1.getId(), dataResults.get(0).getId());
    }

    @Test
    @Transactional
    public void testSearchRankingRelevance() {
        // e1 has term "analytics" in the NAME (Weight A)
        final StackitEntity e1 = new StackitEntity();
        e1.setId(UUID.randomUUID());
        e1.setResourceId("vm-analyt-1");
        e1.setName("analytics-engine");
        e1.setType("compute");
        e1.setStatus("RUNNING");
        e1.setRegion("eu01");
        e1.setProjectId("proj-rank");
        e1.setCreatedAt(Instant.now());
        e1.setUpdatedAt(Instant.now());
        repository.persistAndFlush(e1);

        // e2 has term "analytics" only in data payload (Weight C)
        final StackitEntity e2 = new StackitEntity();
        e2.setId(UUID.randomUUID());
        e2.setResourceId("vm-compute-2");
        e2.setName("batch-worker");
        e2.setType("compute");
        e2.setStatus("RUNNING");
        e2.setRegion("eu01");
        e2.setProjectId("proj-rank");
        e2.setCreatedAt(Instant.now());
        e2.setUpdatedAt(Instant.now());
        e2.setData(Map.of("serviceRole", "analytics"));
        repository.persistAndFlush(e2);

        final List<StackitEntity> ranked = repository.search("analytics");
        assertTrue(ranked.size() >= 2);
        // Weight A should rank before Weight C
        assertEquals(e1.getId(), ranked.get(0).getId());
        assertEquals(e2.getId(), ranked.get(1).getId());
    }

    @Test
    @Transactional
    public void testSearchExcludesSoftDeleted() {
        final StackitEntity deletedEntity = new StackitEntity();
        deletedEntity.setId(UUID.randomUUID());
        deletedEntity.setResourceId("vm-deleted-1");
        deletedEntity.setName("decommissioned-postgres-db");
        deletedEntity.setType("database");
        deletedEntity.setStatus("STOPPED");
        deletedEntity.setRegion("eu01");
        deletedEntity.setProjectId("proj-del");
        deletedEntity.setCreatedAt(Instant.now());
        deletedEntity.setUpdatedAt(Instant.now());
        deletedEntity.setDeletedAt(Instant.now());
        repository.persistAndFlush(deletedEntity);

        final List<StackitEntity> results = repository.search("decommissioned");
        assertTrue(results.stream().noneMatch(e -> e.getId().equals(deletedEntity.getId())));
    }

    @Test
    @Transactional
    public void testSearchFallbackForBlankQuery() {
        final List<StackitEntity> nullResults = repository.search(null);
        assertNotNull(nullResults);

        final List<StackitEntity> blankResults = repository.search("   ");
        assertNotNull(blankResults);
        assertFalse(blankResults.isEmpty());
    }

    @Test
    @Transactional
    public void testSearchPrefixMatching() {
        final StackitEntity entity = new StackitEntity();
        entity.setId(UUID.randomUUID());
        entity.setResourceId("prefix-res-12345");
        entity.setName("telemetry-collector-service");
        entity.setType("monitoring");
        entity.setStatus("RUNNING");
        entity.setRegion("eu01");
        entity.setProjectId("8a784558-b50d-4553-b4e7-19e843d2e279");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.persistAndFlush(entity);

        final List<StackitEntity> prefixProjResults = repository.search("8a7");
        assertTrue(prefixProjResults.stream().anyMatch(e -> e.getId().equals(entity.getId())));

        final List<StackitEntity> hyphenPrefixResults = repository.search("8a784558-b50d");
        assertTrue(hyphenPrefixResults.stream().anyMatch(e -> e.getId().equals(entity.getId())));

        final List<StackitEntity> namePrefixResults = repository.search("telem");
        assertTrue(namePrefixResults.stream().anyMatch(e -> e.getId().equals(entity.getId())));

        final List<StackitEntity> multiPrefixResults = repository.search("telem coll");
        assertTrue(multiPrefixResults.stream().anyMatch(e -> e.getId().equals(entity.getId())));
    }

    @Test
    @Transactional
    public void testAggregateByTypeAndSearchWithLimit() {
        final String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            final StackitEntity vm = new StackitEntity();
            vm.setId(UUID.randomUUID());
            vm.setResourceId("agg-vm-" + i + "-" + uniqueSuffix);
            vm.setName("agg-node-" + uniqueSuffix);
            vm.setType("compute");
            vm.setStatus("ACTIVE");
            vm.setRegion("eu01");
            vm.setProjectId("proj-agg");
            vm.setCreatedAt(Instant.now());
            vm.setUpdatedAt(Instant.now());
            repository.persist(vm);
        }

        for (int i = 0; i < 3; i++) {
            final StackitEntity bucket = new StackitEntity();
            bucket.setId(UUID.randomUUID());
            bucket.setResourceId("agg-bkt-" + i + "-" + uniqueSuffix);
            bucket.setName("agg-storage-" + uniqueSuffix);
            bucket.setType("storage");
            bucket.setStatus("ACTIVE");
            bucket.setRegion("eu01");
            bucket.setProjectId("proj-agg");
            bucket.setCreatedAt(Instant.now());
            bucket.setUpdatedAt(Instant.now());
            repository.persist(bucket);
        }
        repository.flush();

        // Exact query aggregation across all 8 items
        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> typeAggs = repository.aggregateByType("agg-node-" + uniqueSuffix);
        assertEquals(1, typeAggs.size());
        assertEquals("compute", typeAggs.get(0).getKey());
        assertEquals(5L, typeAggs.get(0).getCount());

        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> regionAggs = repository.aggregateByRegion("agg-node-" + uniqueSuffix);
        assertEquals(1, regionAggs.size());
        assertEquals("eu01", regionAggs.get(0).getKey());
        assertEquals(5L, regionAggs.get(0).getCount());

        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> statusAggs = repository.aggregateByStatus("agg-node-" + uniqueSuffix);
        assertEquals(1, statusAggs.size());
        assertEquals("ACTIVE", statusAggs.get(0).getKey());
        assertEquals(5L, statusAggs.get(0).getCount());

        // Limit test: fetch only 2 out of the 5
        final List<StackitEntity> limited = repository.search("agg-node-" + uniqueSuffix, 2);
        assertEquals(2, limited.size());
    }
}
