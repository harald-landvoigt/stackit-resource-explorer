package com.landvoigtit.stackit.resourceexplorer.dns;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DnsZoneResourceDtoTest {

    private Validator validator;

    @BeforeEach
    public void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testValidDto() {
        final DnsRecordSetDto recordSet = new DnsRecordSetDto();
        recordSet.setId("rs-1");
        recordSet.setName("api.example.com.");
        recordSet.setType("A");
        recordSet.setTtl(300);
        recordSet.setRecords(List.of("192.0.2.1"));
        recordSet.setActive(true);
        recordSet.setState("CREATE_SUCCEEDED");
        recordSet.setMatchedServerName("web-01");

        final DnsZoneResourceDto dto = new DnsZoneResourceDto();
        dto.setZoneId("zone-123");
        dto.setName("example-zone");
        dto.setDnsName("example.com.");
        dto.setStatus("ACTIVE");
        dto.setZoneType("primary");
        dto.setVisibility("public");
        dto.setActive(true);
        dto.setIsReverseZone(false);
        dto.setPrimaryNameServer("ns1.stackit.cloud.");
        dto.setContactEmail("admin@example.com");
        dto.setDefaultTTL(3600);
        dto.setRecordCount(1);
        dto.setAcl("0.0.0.0/0");
        dto.setDescription("Production DNS Zone");
        dto.setLabels(Map.of("env", "prod"));
        dto.setRecordSetsSummary(Map.of("A", 1));
        dto.setRecordSets(List.of(recordSet));

        assertTrue(validator.validate(dto).isEmpty());
        assertEquals("zone-123", dto.getZoneId());
        assertEquals("example.com.", dto.getDnsName());
        assertEquals(1, dto.getRecordSets().size());
        assertEquals("api.example.com.", dto.getRecordSets().get(0).getName());
        assertEquals("web-01", dto.getRecordSets().get(0).getMatchedServerName());
    }

    @Test
    public void testValidationFailsWhenRequiredFieldsBlank() {
        final DnsZoneResourceDto dto = new DnsZoneResourceDto();
        assertFalse(validator.validate(dto).isEmpty());
    }
}
