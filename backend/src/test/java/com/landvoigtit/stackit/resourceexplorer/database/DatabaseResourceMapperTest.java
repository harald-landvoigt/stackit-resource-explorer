package com.landvoigtit.stackit.resourceexplorer.database;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseResourceMapperTest {

    @Test
    public void testMapToDtoWithNestedStructures() {
        final Map<String, Object> json = Map.of(
                "id", "db-inst-001",
                "name", "prod-pg-cluster",
                "status", "Ready",
                "version", "16",
                "flavor", Map.of("id", "c1.2", "description", "2 vCPU, 4GB RAM"),
                "storage", Map.of("size", 64),
                "replicas", 3,
                "backupSchedule", "0 3 * * *",
                "acl", Map.of("items", List.of("10.0.0.0/16", "192.168.1.0/24")),
                "created", "2026-09-01T08:00:00Z"
        );

        final DatabaseResourceDto dto = DatabaseResourceMapper.mapToDto(json, "postgresql");
        assertNotNull(dto);
        assertEquals("db-inst-001", dto.getInstanceId());
        assertEquals("prod-pg-cluster", dto.getName());
        assertEquals("postgresql", dto.getEngine());
        assertEquals("Ready", dto.getStatus());
        assertEquals("16", dto.getVersion());
        assertEquals("c1.2", dto.getFlavor());
        assertEquals("2 vCPU, 4GB RAM", dto.getFlavorDescription());
        assertEquals(64, dto.getStorage());
        assertEquals(3, dto.getReplicas());
        assertEquals("0 3 * * *", dto.getBackupSchedule());
        assertEquals(List.of("10.0.0.0/16", "192.168.1.0/24"), dto.getAcls());
        assertEquals("2026-09-01T08:00:00Z", dto.getCreated());
    }

    @Test
    public void testMapToDtoWithFlatStructures() {
        final Map<String, Object> json = Map.of(
                "id", "redis-001",
                "name", "cache-redis",
                "status", "Ready",
                "version", "7.0",
                "flavor", "cache-small",
                "storage", 10,
                "replicaCount", 1,
                "acls", List.of("0.0.0.0/0")
        );

        final DatabaseResourceDto dto = DatabaseResourceMapper.mapToDto(json, "redis");
        assertNotNull(dto);
        assertEquals("redis-001", dto.getInstanceId());
        assertEquals("cache-redis", dto.getName());
        assertEquals("redis", dto.getEngine());
        assertEquals("cache-small", dto.getFlavor());
        assertEquals(10, dto.getStorage());
        assertEquals(1, dto.getReplicas());
        assertEquals(List.of("0.0.0.0/0"), dto.getAcls());
    }

    @Test
    public void testMapToDtoNull() {
        assertNull(DatabaseResourceMapper.mapToDto(null, "postgresql"));
    }

    @Test
    public void testMapToEntityFromDto() {
        final DatabaseResourceDto dto = new DatabaseResourceDto();
        dto.setInstanceId("db-uuid-1234");
        dto.setName("analytics-db");
        dto.setEngine("opensearch");
        dto.setStatus("Ready");
        dto.setVersion("2.11");
        dto.setFlavor("os-med");
        dto.setStorage(100);
        dto.setReplicas(2);

        final StackitEntity entity = DatabaseResourceMapper.mapToEntity(dto, "proj-abc", "eu01");
        assertNotNull(entity);
        assertNotNull(entity.getId());
        assertEquals("db-uuid-1234", entity.getResourceId());
        assertEquals("analytics-db", entity.getName());
        assertEquals(StackitConstants.RESOURCE_TYPE_DATABASE, entity.getType());
        assertEquals("Ready", entity.getStatus());
        assertEquals("proj-abc", entity.getProjectId());
        assertEquals("eu01", entity.getRegion());

        // Verify tags
        assertNotNull(entity.getTags());
        assertEquals("opensearch", entity.getTags().get("engine"));
        assertEquals("2.11", entity.getTags().get("version"));
        assertEquals("os-med", entity.getTags().get("flavor"));
        assertEquals("100", entity.getTags().get("storage-gb"));
        assertEquals("2", entity.getTags().get("replicas"));

        // Verify data payload
        assertNotNull(entity.getData());
        assertEquals("analytics-db", entity.getData().get("name"));
        assertEquals("opensearch", entity.getData().get("engine"));
        assertEquals(100, entity.getData().get("storage"));
    }

    @Test
    public void testMapToEntityDirectFromJson() {
        final Map<String, Object> json = Map.of(
                "id", "mongo-inst-456",
                "name", "document-store",
                "status", "Ready",
                "version", "6.0",
                "flavor", "m1.2",
                "storage", Map.of("size", 200),
                "replicas", 3
        );

        final StackitEntity entity = DatabaseResourceMapper.mapToEntity(json, "mongodb", "proj-xyz", "eu02");
        assertNotNull(entity);
        assertEquals("mongo-inst-456", entity.getResourceId());
        assertEquals("document-store", entity.getName());
        assertEquals("mongodb", entity.getTags().get("engine"));
        assertEquals("200", entity.getTags().get("storage-gb"));
        assertEquals("eu02", entity.getRegion());
        assertEquals("proj-xyz", entity.getProjectId());
    }

    @Test
    public void testMapToEntityNull() {
        assertNull(DatabaseResourceMapper.mapToEntity((DatabaseResourceDto) null, "proj", "eu01"));
        assertNull(DatabaseResourceMapper.mapToEntity((Map<String, Object>) null, "engine", "proj", "eu01"));
    }
}
