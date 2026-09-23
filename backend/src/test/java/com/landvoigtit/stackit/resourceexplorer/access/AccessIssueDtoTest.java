package com.landvoigtit.stackit.resourceexplorer.access;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccessIssueDtoTest {

    @Test
    void testAccessStatusEnum() {
        assertEquals(3, AccessStatus.values().length);
        assertEquals(AccessStatus.ACCESSIBLE, AccessStatus.valueOf("ACCESSIBLE"));
        assertEquals(AccessStatus.ACCESS_DENIED, AccessStatus.valueOf("ACCESS_DENIED"));
        assertEquals(AccessStatus.NOT_CHECKED, AccessStatus.valueOf("NOT_CHECKED"));
    }

    @Test
    void testAccessIssueRecordDto() {
        final Instant now = Instant.now();
        final AccessIssueRecordDto record = AccessIssueRecordDto.builder()
                .projectId("p-123")
                .projectName("Test Project")
                .resourceType("compute")
                .region("eu01")
                .status(AccessStatus.ACCESS_DENIED)
                .statusCode(403)
                .errorMessage("Forbidden: insufficient permissions")
                .lastChecked(now)
                .build();

        assertEquals("p-123", record.getProjectId());
        assertEquals("Test Project", record.getProjectName());
        assertEquals("compute", record.getResourceType());
        assertEquals("eu01", record.getRegion());
        assertEquals(AccessStatus.ACCESS_DENIED, record.getStatus());
        assertEquals(403, record.getStatusCode());
        assertEquals("Forbidden: insufficient permissions", record.getErrorMessage());
        assertEquals(now, record.getLastChecked());
    }

    @Test
    void testProjectAccessMatrixRowDto() {
        final ProjectAccessMatrixRowDto row = ProjectAccessMatrixRowDto.builder()
                .projectId("p-123")
                .projectName("Test Project")
                .statuses(Map.of("compute", AccessStatus.ACCESSIBLE, "storage", AccessStatus.ACCESS_DENIED))
                .hasAccessIssues(true)
                .build();

        assertEquals("p-123", row.getProjectId());
        assertEquals("Test Project", row.getProjectName());
        assertTrue(row.isHasAccessIssues());
        assertEquals(AccessStatus.ACCESSIBLE, row.getStatuses().get("compute"));
        assertEquals(AccessStatus.ACCESS_DENIED, row.getStatuses().get("storage"));
    }

    @Test
    void testAccessIssuesSummaryDto() {
        final AccessIssueRecordDto record = AccessIssueRecordDto.builder()
                .projectId("p-123")
                .resourceType("storage")
                .status(AccessStatus.ACCESS_DENIED)
                .build();

        final ProjectAccessMatrixRowDto row = ProjectAccessMatrixRowDto.builder()
                .projectId("p-123")
                .projectName("Test Project")
                .statuses(Map.of("storage", AccessStatus.ACCESS_DENIED))
                .hasAccessIssues(true)
                .build();

        final AccessIssuesSummaryDto summary = AccessIssuesSummaryDto.builder()
                .totalIssues(1L)
                .totalProjectsChecked(1L)
                .affectedProjectsCount(1L)
                .matrix(List.of(row))
                .issues(List.of(record))
                .build();

        assertEquals(1L, summary.getTotalIssues());
        assertEquals(1L, summary.getTotalProjectsChecked());
        assertEquals(1L, summary.getAffectedProjectsCount());
        assertEquals(1, summary.getMatrix().size());
        assertEquals(1, summary.getIssues().size());
    }
}
