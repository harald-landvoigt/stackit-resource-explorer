package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
}
