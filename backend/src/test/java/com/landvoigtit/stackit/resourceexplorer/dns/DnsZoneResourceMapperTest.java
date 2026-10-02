package com.landvoigtit.stackit.resourceexplorer.dns;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DnsZoneResourceMapperTest {

    @Test
    public void testMapToDtoFromRawJson() {
        final Map<String, Object> zoneJson = new java.util.HashMap<>();
        zoneJson.put("id", "zone-uuid-1234");
        zoneJson.put("name", "my-zone");
        zoneJson.put("dnsName", "example.com.");
        zoneJson.put("description", "My test zone");
        zoneJson.put("type", "primary");
        zoneJson.put("state", "CREATE_SUCCEEDED");
        zoneJson.put("active", true);
        zoneJson.put("isReverseZone", false);
        zoneJson.put("visibility", "public");
        zoneJson.put("primaryNameServer", "ns1.stackit.cloud.");
        zoneJson.put("contactEmail", "admin@example.com");
        zoneJson.put("defaultTTL", 3600);
        zoneJson.put("recordCount", 2);
        zoneJson.put("acl", "0.0.0.0/0");
        zoneJson.put("labelsMap", Map.of("env", "staging"));

        final Map<String, Object> recordSet1 = Map.of(
                "id", "rs-1",
                "name", "api.example.com.",
                "type", "A",
                "ttl", 300,
                "active", true,
                "state", "CREATE_SUCCEEDED",
                "records", List.of(Map.of("content", "198.51.100.1"))
        );

        final Map<String, Object> recordSet2 = Map.of(
                "id", "rs-2",
                "name", "mail.example.com.",
                "type", "MX",
                "ttl", 3600,
                "active", true,
                "records", List.of("10 mail.example.com.")
        );

        final DnsZoneResourceDto dto = DnsZoneResourceMapper.mapToDto(zoneJson, List.of(recordSet1, recordSet2));

        assertNotNull(dto);
        assertEquals("zone-uuid-1234", dto.getZoneId());
        assertEquals("my-zone", dto.getName());
        assertEquals("example.com.", dto.getDnsName());
        assertEquals("primary", dto.getZoneType());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("public", dto.getVisibility());
        assertTrue(dto.getActive());
        assertFalse(dto.getIsReverseZone());
        assertEquals("ns1.stackit.cloud.", dto.getPrimaryNameServer());
        assertEquals("admin@example.com", dto.getContactEmail());
        assertEquals(3600, dto.getDefaultTTL());
        assertEquals(2, dto.getRecordCount());
        assertEquals("0.0.0.0/0", dto.getAcl());
        assertEquals("staging", dto.getLabels().get("env"));

        assertNotNull(dto.getRecordSetsSummary());
        assertEquals(1, dto.getRecordSetsSummary().get("A"));
        assertEquals(1, dto.getRecordSetsSummary().get("MX"));

        assertNotNull(dto.getRecordSets());
        assertEquals(2, dto.getRecordSets().size());

        final DnsRecordSetDto rs1 = dto.getRecordSets().get(0);
        assertEquals("rs-1", rs1.getId());
        assertEquals("api.example.com.", rs1.getName());
        assertEquals("A", rs1.getType());
        assertEquals(300, rs1.getTtl());
        assertEquals(List.of("198.51.100.1"), rs1.getRecords());

        final DnsRecordSetDto rs2 = dto.getRecordSets().get(1);
        assertEquals("rs-2", rs2.getId());
        assertEquals("MX", rs2.getType());
        assertEquals(List.of("10 mail.example.com."), rs2.getRecords());
    }

    @Test
    public void testMapToEntity() {
        final DnsRecordSetDto recordSet = new DnsRecordSetDto();
        recordSet.setId("rs-1");
        recordSet.setName("api.example.com.");
        recordSet.setType("A");
        recordSet.setTtl(300);
        recordSet.setRecords(List.of("198.51.100.1"));
        recordSet.setActive(true);
        recordSet.setMatchedServerName("web-server-01");

        final DnsZoneResourceDto dto = new DnsZoneResourceDto();
        dto.setZoneId("11111111-2222-3333-4444-555555555555");
        dto.setName("my-zone");
        dto.setDnsName("example.com.");
        dto.setStatus("ACTIVE");
        dto.setZoneType("primary");
        dto.setVisibility("public");
        dto.setActive(true);
        dto.setIsReverseZone(false);
        dto.setRecordCount(1);
        dto.setLabels(Map.of("project", "core"));
        dto.setRecordSetsSummary(Map.of("A", 1));
        dto.setRecordSets(List.of(recordSet));

        final StackitEntity entity = DnsZoneResourceMapper.mapToEntity(dto);

        assertNotNull(entity);
        assertEquals("11111111-2222-3333-4444-555555555555", entity.getId().toString());
        assertEquals("11111111-2222-3333-4444-555555555555", entity.getResourceId());
        assertEquals("example.com.", entity.getName());
        assertEquals(StackitConstants.RESOURCE_TYPE_DNS_ZONE, entity.getType());
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals(StackitConstants.GLOBAL_REGION, entity.getRegion());

        assertEquals("example.com.", entity.getTags().get("dnsName"));
        assertEquals("primary", entity.getTags().get("zone-type"));
        assertEquals("public", entity.getTags().get("visibility"));
        assertEquals("true", entity.getTags().get("active"));
        assertEquals("core", entity.getTags().get("project"));

        assertNotNull(entity.getData());
        assertEquals("example.com.", entity.getData().get("dnsName"));
        assertEquals("primary", entity.getData().get("zoneType"));
        assertTrue(entity.getData().containsKey("recordSets"));
    }

    @Test
    public void testMapRecordSetToEntity() {
        final DnsRecordSetDto rs = new DnsRecordSetDto();
        rs.setId("22222222-3333-4444-5555-666666666666");
        rs.setName("api.example.com.");
        rs.setType("A");
        rs.setTtl(300);
        rs.setRecords(List.of("198.51.100.1"));
        rs.setComment("API gateway");
        rs.setActive(true);
        rs.setState("CREATE_SUCCEEDED");
        rs.setMatchedServerId("srv-1");
        rs.setMatchedServerName("api-srv");
        rs.setMatchedPublicIpId("ip-1");

        final StackitEntity entity = DnsZoneResourceMapper.mapRecordSetToEntity(
                rs, "proj-1", "zone-123", "example.com"
        );

        assertNotNull(entity);
        assertEquals("22222222-3333-4444-5555-666666666666", entity.getId().toString());
        assertEquals("22222222-3333-4444-5555-666666666666", entity.getResourceId());
        assertEquals("api.example.com.", entity.getName());
        assertEquals(StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET, entity.getType());
        assertEquals("CREATE_SUCCEEDED", entity.getStatus());
        assertEquals(StackitConstants.GLOBAL_REGION, entity.getRegion());
        assertEquals("proj-1", entity.getProjectId());

        assertEquals("A", entity.getTags().get("recordType"));
        assertEquals("example.com", entity.getTags().get("zoneName"));
        assertEquals("zone-123", entity.getTags().get("zoneId"));

        assertEquals("22222222-3333-4444-5555-666666666666", entity.getData().get("recordSetId"));
        assertEquals("zone-123", entity.getData().get("zoneId"));
        assertEquals("example.com", entity.getData().get("zoneName"));
        assertEquals("A", entity.getData().get("recordType"));
        assertEquals(300, entity.getData().get("ttl"));
        assertEquals(List.of("198.51.100.1"), entity.getData().get("records"));
        assertEquals("api-srv", entity.getData().get("matchedServerName"));
        assertEquals("ip-1", entity.getData().get("matchedPublicIpId"));
    }

    @Test
    public void testNullHandling() {
        assertNull(DnsZoneResourceMapper.mapToDto(null, null));
        assertNull(DnsZoneResourceMapper.mapRecordSetToDto(null));
        assertNull(DnsZoneResourceMapper.mapToEntity(null));
        assertNull(DnsZoneResourceMapper.mapRecordSetToEntity(null, null, null, null));
    }
}
