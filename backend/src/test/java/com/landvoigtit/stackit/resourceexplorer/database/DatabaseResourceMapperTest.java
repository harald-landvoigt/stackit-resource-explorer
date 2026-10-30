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

    @Test
    public void testMapToEntityPostgresqlNetworkSettingsAndCustomLabels() {
        final Map<String, Object> json = Map.of(
                "id", "e81e1e10-27b2-47df-9654-dc1388461787",
                "name", "postgres-dvqco116br3j",
                "state", "READY",
                "version", "17",
                "flavorId", "2.4",
                "storage", Map.of("size", 5, "class", "premium-perf2-stackit"),
                "backupSchedule", "15 10 * * *",
                "connectionInfo", Map.of(
                        "write", Map.of(
                                "host", "e81e1e10-27b2-47df-9654-dc1388461787.postgresql.eu02.onstackit.cloud",
                                "port", 5432
                        )
                ),
                "network", Map.of(
                        "accessScope", "PUBLIC",
                        "acl", List.of("193.148.160.0/19", "45.129.40.0/21")
                ),
                "labels", Map.of(
                        "cost_center", "4711",
                        "owner", "harald.landvoigt"
                )
        );

        final StackitEntity entity = DatabaseResourceMapper.mapToEntity(
                json,
                "postgresql",
                "c2cc46ae-e432-4e4a-b4df-42a4066c344b",
                "eu02"
        );

        assertNotNull(entity);
        assertEquals("e81e1e10-27b2-47df-9654-dc1388461787", entity.getResourceId());
        assertEquals("READY", entity.getStatus());
        assertEquals("2.4", entity.getData().get("flavor"));
        assertEquals(5, entity.getData().get("storage"));
        assertEquals("premium-perf2-stackit", entity.getData().get("storageClass"));
        assertEquals(
                "e81e1e10-27b2-47df-9654-dc1388461787.postgresql.eu02.onstackit.cloud",
                entity.getData().get("host")
        );
        assertEquals(5432, entity.getData().get("port"));
        assertEquals("PUBLIC", entity.getData().get("accessScope"));
        assertEquals(List.of("193.148.160.0/19", "45.129.40.0/21"), entity.getData().get("acls"));

        // Verify custom labels in both tags and data
        assertEquals("4711", entity.getTags().get("cost_center"));
        assertEquals("harald.landvoigt", entity.getTags().get("owner"));
        assertEquals(
                "e81e1e10-27b2-47df-9654-dc1388461787.postgresql.eu02.onstackit.cloud",
                entity.getTags().get("host")
        );
        assertEquals("5432", entity.getTags().get("port"));
        assertEquals(Map.of("cost_center", "4711", "owner", "harald.landvoigt"), entity.getData().get("labels"));
    }

    @Test
    public void testMapToEntityMariaDbInstanceFields() {
        final Map<String, Object> json = Map.ofEntries(
                Map.entry("instanceId", "mariadb-inst-789"),
                Map.entry("name", "prod-mariadb"),
                Map.entry("planName", "stackit-mariadb-1.2.10-single"),
                Map.entry("planId", "plan-uuid-001"),
                Map.entry("version", "10.11"),
                Map.entry("lastOperation", Map.of("state", "succeeded", "type", "create")),
                Map.entry("cfGuid", "cf-guid-123"),
                Map.entry("cfSpaceGuid", "cf-space-456"),
                Map.entry("cfOrganizationGuid", "cf-org-789"),
                Map.entry("dashboardUrl", "https://dashboard.mariadb.stackit.cloud/inst-789"),
                Map.entry("parameters", Map.of(
                        "sgw_acl", "193.148.160.0/19, 45.129.40.0/21",
                        "max_disk_threshold", 80,
                        "enable_monitoring", true
                ))
        );

        final StackitEntity entity = DatabaseResourceMapper.mapToEntity(json, "mariadb", "proj-maria", "eu01");
        assertNotNull(entity);
        assertEquals("mariadb-inst-789", entity.getResourceId());
        assertEquals("prod-mariadb", entity.getName());
        assertEquals("succeeded", entity.getStatus());
        assertEquals("stackit-mariadb-1.2.10-single", entity.getData().get("flavor"));
        assertEquals("plan-uuid-001", entity.getData().get("planId"));
        assertEquals(80, entity.getData().get("storage"));
        assertEquals("cf-guid-123", entity.getData().get("cfGuid"));
        assertEquals("cf-space-456", entity.getData().get("cfSpaceGuid"));
        assertEquals("cf-org-789", entity.getData().get("cfOrganizationGuid"));
        assertEquals("https://dashboard.mariadb.stackit.cloud/inst-789", entity.getData().get("dashboardUrl"));
        assertEquals(List.of("193.148.160.0/19", "45.129.40.0/21"), entity.getData().get("acls"));
        assertEquals("plan-uuid-001", entity.getTags().get("plan-id"));
    }

    @Test
    public void testMapToEntityMongoDbCpuAndRamExtraction() {
        final Map<String, Object> json = Map.of(
                "id", "mongo-inst-999",
                "name", "mongo-replica-set",
                "status", "Ready",
                "version", "7.0",
                "flavor", Map.of("cpu", 4, "ram", 16, "description", "Standard Replica"),
                "storage", Map.of("size", 100, "class", "premium-perf2-mongodb"),
                "replicas", 3,
                "acl", Map.of("items", List.of("10.0.0.0/8")),
                "options", Map.of("type", "Replica", "pointInTimeWindowHours", 30)
        );

        final StackitEntity entity = DatabaseResourceMapper.mapToEntity(json, "mongodb", "proj-mongo", "eu02");
        assertNotNull(entity);
        assertEquals("mongo-inst-999", entity.getResourceId());
        assertEquals("4 vCPU / 16 GB", entity.getData().get("flavor"));
        assertEquals(4, entity.getData().get("cpu"));
        assertEquals(16, entity.getData().get("memory"));
        assertEquals("4", entity.getTags().get("cpu"));
        assertEquals("16", entity.getTags().get("memory-gb"));
        assertEquals("premium-perf2-mongodb", entity.getData().get("storageClass"));
        assertEquals(List.of("10.0.0.0/8"), entity.getData().get("acls"));
    }
}
