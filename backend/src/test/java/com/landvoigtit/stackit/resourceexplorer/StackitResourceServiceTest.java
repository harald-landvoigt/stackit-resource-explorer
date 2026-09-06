package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class StackitResourceServiceTest {

    private StackitResourceService service;

    @BeforeEach
    public final void setUp() {
        final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new StackitResourceService(null, validator, null, null);
    }

    @Test
    public final void testMappingDtoToEntity() {
        final StackitResourceDto dto = new StackitResourceDto();
        final String idStr = UUID.randomUUID().toString();
        dto.setId(idStr);
        dto.setResourceId("stackit-vm-123");
        dto.setName("test-resource");
        dto.setType("virtual-machine");
        dto.setStatus("RUNNING");
        dto.setRegion("eu-west-1");
        dto.setProjectId("project-123");
        dto.setTags(Map.of("key1", "val1"));
        dto.setData(Map.of("key2", "val2"));

        final StackitEntity entity = service.mapToEntity(dto);
        assertNotNull(entity);
        assertEquals(UUID.fromString(idStr), entity.getId());
        assertEquals("stackit-vm-123", entity.getResourceId());
        assertEquals("test-resource", entity.getName());
        assertEquals("virtual-machine", entity.getType());
        assertEquals("RUNNING", entity.getStatus());
        assertEquals("eu-west-1", entity.getRegion());
        assertEquals("project-123", entity.getProjectId());
        assertEquals("val1", entity.getTags().get("key1"));
        assertEquals("val2", entity.getData().get("key2"));
    }

    @Test
    public final void testMappingEntityToDto() {
        final StackitEntity entity = new StackitEntity();
        final UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setResourceId("stackit-vm-123");
        entity.setName("test-resource");
        entity.setType("virtual-machine");
        entity.setStatus("RUNNING");
        entity.setRegion("eu-west-1");
        entity.setProjectId("project-123");
        final Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setTags(Map.of("key1", "val1"));
        entity.setData(Map.of("key2", "val2"));

        final StackitResourceDto dto = service.mapToDto(entity);
        assertNotNull(dto);
        assertEquals(id.toString(), dto.getId());
        assertEquals("stackit-vm-123", dto.getResourceId());
        assertEquals("test-resource", dto.getName());
        assertEquals("virtual-machine", dto.getType());
        assertEquals("RUNNING", dto.getStatus());
        assertEquals("eu-west-1", dto.getRegion());
        assertEquals("project-123", dto.getProjectId());
        assertEquals(now.atOffset(ZoneOffset.UTC), dto.getCreatedAt());
        assertEquals(now.atOffset(ZoneOffset.UTC), dto.getUpdatedAt());
        assertEquals("val1", dto.getTags().get("key1"));
        assertEquals("val2", dto.getData().get("key2"));
    }

    @Test
    public final void testValidationSuccess() {
        final StackitResourceDto dto = new StackitResourceDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setResourceId("stackit-vm-123");
        dto.setName("test-resource");
        dto.setType("virtual-machine");
        dto.setStatus("RUNNING");
        dto.setRegion("eu-west-1");
        dto.setProjectId("project-123");

        assertDoesNotThrow(() -> service.validate(dto));
    }

    @Test
    public final void testValidationInvalidUuid() {
        final StackitResourceDto dto = new StackitResourceDto();
        dto.setId("not-a-uuid");
        dto.setResourceId("stackit-vm-123");
        dto.setName("test-resource");
        dto.setType("virtual-machine");
        dto.setStatus("RUNNING");
        dto.setRegion("eu-west-1");
        dto.setProjectId("project-123");

        final Exception exception = assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        assertTrue(exception.getMessage().contains("UUID"));
    }

    @Test
    public final void testValidationEmptyFields() {
        final StackitResourceDto dto = new StackitResourceDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setResourceId("stackit-vm-123");
        dto.setName("");
        dto.setType("virtual-machine");
        dto.setStatus("RUNNING");
        dto.setRegion("eu-west-1");
        dto.setProjectId("project-123");

        final Exception exception = assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    public final void testValidationNameTooLong() {
        final StackitResourceDto dto = new StackitResourceDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setResourceId("stackit-vm-123");
        dto.setName("a".repeat(256));
        dto.setType("virtual-machine");
        dto.setStatus("RUNNING");
        dto.setRegion("eu-west-1");
        dto.setProjectId("project-123");

        final Exception exception = assertThrows(IllegalArgumentException.class, () -> service.validate(dto));
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    public void testSearchResourcesWithAggregations() {
        final boolean[] searchCalled = new boolean[]{false};
        final StackitEntity sample = new StackitEntity();
        sample.setId(UUID.randomUUID());
        sample.setName("sample-res");
        sample.setResourceId("sample-id");
        sample.setType("compute");
        sample.setStatus("READY");
        sample.setRegion("eu01");
        sample.setProjectId("proj-1");

        final StackitResourceRepository repoFake = new StackitResourceRepository() {
            @Override
            public java.util.List<StackitEntity> search(String query, int limit) {
                searchCalled[0] = true;
                return java.util.List.of(sample);
            }

            @Override
            public java.util.List<AggregationItemDto> aggregateByType(String query) {
                return java.util.List.of(
                        new AggregationItemDto("compute", 120L),
                        new AggregationItemDto("storage", 80L)
                );
            }

            @Override
            public java.util.List<AggregationItemDto> aggregateByRegion(String query) {
                return java.util.List.of(new AggregationItemDto("eu01", 200L));
            }

            @Override
            public java.util.List<AggregationItemDto> aggregateByStatus(String query) {
                return java.util.List.of(new AggregationItemDto("ACTIVE", 195L), new AggregationItemDto("DELETED", 5L));
            }
        };

        final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        final StackitResourceService svc = new StackitResourceService(repoFake, validator, null, null);

        final ResourceSearchResultDto result = svc.searchResources("sample");
        assertTrue(searchCalled[0]);
        assertEquals(1, result.getResources().size());
        assertEquals(200L, result.getTotalCount());
        assertEquals(2, result.getTypeAggregations().size());
        assertEquals("VMs", result.getTypeAggregations().get(0).getKey());
        assertEquals(120L, result.getTypeAggregations().get(0).getCount());
        assertEquals("Buckets", result.getTypeAggregations().get(1).getKey());
        assertEquals(80L, result.getTypeAggregations().get(1).getCount());

        assertEquals(1, result.getRegionAggregations().size());
        assertEquals("eu01", result.getRegionAggregations().get(0).getKey());
        assertEquals(200L, result.getRegionAggregations().get(0).getCount());

        assertEquals(2, result.getStatusAggregations().size());
        assertEquals("ACTIVE", result.getStatusAggregations().get(0).getKey());
        assertEquals(195L, result.getStatusAggregations().get(0).getCount());
        assertEquals("DELETED", result.getStatusAggregations().get(1).getKey());
        assertEquals(5L, result.getStatusAggregations().get(1).getCount());
    }

    @Test
    public void testListAllWithSearchQuery() {
        final StackitEntity sample = new StackitEntity();
        sample.setId(UUID.randomUUID());
        sample.setName("sample-res");
        sample.setResourceId("sample-id");
        sample.setType("vm");
        sample.setStatus("READY");
        sample.setRegion("eu01");
        sample.setProjectId("proj-1");

        final StackitResourceRepository repoFake = new StackitResourceRepository() {
            @Override
            public java.util.List<StackitEntity> search(String query, int limit) {
                return java.util.List.of(sample);
            }

            @Override
            public java.util.List<AggregationItemDto> aggregateByType(String query) {
                return java.util.List.of(new AggregationItemDto("vm", 1L));
            }

            @Override
            public java.util.List<AggregationItemDto> aggregateByRegion(String query) {
                return java.util.List.of(new AggregationItemDto("eu01", 1L));
            }

            @Override
            public java.util.List<AggregationItemDto> aggregateByStatus(String query) {
                return java.util.List.of(new AggregationItemDto("READY", 1L));
            }
        };

        final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        final StackitResourceService svc = new StackitResourceService(repoFake, validator, null, null);

        final java.util.List<StackitResourceDto> searchResults = svc.listAll("some-query");
        assertEquals(1, searchResults.size());
        assertEquals("sample-res", searchResults.get(0).getName());

        final java.util.List<StackitResourceDto> allResults = svc.listAll(null);
        assertEquals(1, allResults.size());
    }

    @Test
    public void testFormatTypeLabel() {
        assertEquals("VMs", service.formatTypeLabel("compute"));
        assertEquals("VMs", service.formatTypeLabel("vm"));
        assertEquals("Buckets", service.formatTypeLabel("storage"));
        assertEquals("VM Disks", service.formatTypeLabel("vmdisks"));
        assertEquals("VM Disks", service.formatTypeLabel("disk"));
        assertEquals("VM Disks", service.formatTypeLabel("volume"));
        assertEquals("VPCs", service.formatTypeLabel("network-vpc"));
        assertEquals("Load Balancers", service.formatTypeLabel("network"));
        assertEquals("Invoices", service.formatTypeLabel("billing"));
        assertEquals("Invoices", service.formatTypeLabel("billing-org"));
        assertEquals("IAM Policies", service.formatTypeLabel("iam"));
        assertEquals("Unknown", service.formatTypeLabel(null));
        assertEquals("Unknown", service.formatTypeLabel("  "));
        assertEquals("Databases", service.formatTypeLabel("database"));
    }
}
