package com.landvoigtit.stackit.resourceexplorer.database;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseResourceDtoTest {

    private Validator validator;

    @BeforeEach
    public void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testValidDto() {
        final DatabaseResourceDto dto = new DatabaseResourceDto();
        dto.setInstanceId("inst-12345");
        dto.setName("prod-postgres-db");
        dto.setEngine("postgresql");
        dto.setStatus("Ready");
        dto.setVersion("16");
        dto.setFlavor("c1.2");
        dto.setFlavorDescription("2 vCPU, 4GB RAM");
        dto.setStorage(50);
        dto.setReplicas(3);
        dto.setBackupSchedule("0 2 * * *");
        dto.setAcls(List.of("192.168.1.0/24"));
        dto.setCreated("2026-09-01T10:00:00Z");
        dto.setUpdated("2026-09-29T12:00:00Z");
        dto.setLabels(Map.of("environment", "production"));
        dto.setOptions(Map.of("sgw_access", "disabled"));

        assertTrue(validator.validate(dto).isEmpty());
        assertEquals("inst-12345", dto.getInstanceId());
        assertEquals("prod-postgres-db", dto.getName());
        assertEquals("postgresql", dto.getEngine());
        assertEquals("Ready", dto.getStatus());
        assertEquals("16", dto.getVersion());
        assertEquals("c1.2", dto.getFlavor());
        assertEquals("2 vCPU, 4GB RAM", dto.getFlavorDescription());
        assertEquals(50, dto.getStorage());
        assertEquals(3, dto.getReplicas());
        assertEquals("0 2 * * *", dto.getBackupSchedule());
        assertEquals(List.of("192.168.1.0/24"), dto.getAcls());
        assertEquals("2026-09-01T10:00:00Z", dto.getCreated());
        assertEquals("2026-09-29T12:00:00Z", dto.getUpdated());
        assertEquals("production", dto.getLabels().get("environment"));
        assertEquals("disabled", dto.getOptions().get("sgw_access"));
    }

    @Test
    public void testValidationFailsWhenRequiredFieldsBlank() {
        final DatabaseResourceDto dto = new DatabaseResourceDto();
        assertFalse(validator.validate(dto).isEmpty());
    }
}
