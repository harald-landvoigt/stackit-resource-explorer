package com.landvoigtit.stackit.resourceexplorer.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AccessIssueRegistryTest {

    private AccessIssueRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AccessIssueRegistry();
    }

    @Test
    void testRecordSuccessAndFailure() {
        registry.recordSuccess("p-1", "Project Alpha", "compute", "eu01");
        registry.recordFailure("p-1", "Project Alpha", "storage", "eu01", 403, "Forbidden bucket access");

        final AccessIssuesSummaryDto summary = registry.getSummary();
        assertNotNull(summary);
        assertEquals(1, summary.getTotalIssues());
        assertEquals(1, summary.getAffectedProjectsCount());
        assertEquals(1, summary.getTotalProjectsChecked());

        assertEquals(1, summary.getMatrix().size());
        final ProjectAccessMatrixRowDto row = summary.getMatrix().getFirst();
        assertEquals("p-1", row.getProjectId());
        assertEquals("Project Alpha", row.getProjectName());
        assertTrue(row.isHasAccessIssues());
        assertEquals(AccessStatus.ACCESSIBLE, row.getStatuses().get("compute"));
        assertEquals(AccessStatus.ACCESS_DENIED, row.getStatuses().get("storage"));
        assertEquals(AccessStatus.NOT_CHECKED, row.getStatuses().get("iam"));

        assertEquals(1, summary.getIssues().size());
        final AccessIssueRecordDto issue = summary.getIssues().getFirst();
        assertEquals("p-1", issue.getProjectId());
        assertEquals("storage", issue.getResourceType());
        assertEquals(403, issue.getStatusCode());
        assertEquals("Forbidden bucket access", issue.getErrorMessage());
    }

    @Test
    void testRecoveryOverwritesFailure() {
        registry.recordFailure("p-1", "Project Alpha", "network", "eu01", 401, "Unauthorized");
        assertEquals(1, registry.getSummary().getTotalIssues());

        // Now subsequent successful scrape recovers access
        registry.recordSuccess("p-1", "Project Alpha", "network", "eu01");
        final AccessIssuesSummaryDto summary = registry.getSummary();
        assertEquals(0, summary.getTotalIssues());
        assertEquals(0, summary.getAffectedProjectsCount());
        assertFalse(summary.getMatrix().getFirst().isHasAccessIssues());
        assertEquals(AccessStatus.ACCESSIBLE, summary.getMatrix().getFirst().getStatuses().get("network"));
        assertTrue(summary.getIssues().isEmpty());
    }

    @Test
    void testMultipleProjectsAndResourceTypes() {
        registry.recordSuccess("p-1", "Project 1", "compute", "eu01");
        registry.recordSuccess("p-1", "Project 1", "storage", "eu01");
        registry.recordFailure("p-2", "Project 2", "iam", "global", 403, "Service accounts forbidden");
        registry.recordSuccess("p-3", "Project 3", "billing", "global");

        final AccessIssuesSummaryDto summary = registry.getSummary();
        assertEquals(3, summary.getTotalProjectsChecked());
        assertEquals(1, summary.getAffectedProjectsCount());
        assertEquals(1, summary.getTotalIssues());
    }

    @Test
    void testConcurrentWrites() throws InterruptedException {
        final int threads = 10;
        final int iterations = 100;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        final String projectId = "p-" + (threadId % 3);
                        if (j % 2 == 0) {
                            registry.recordSuccess(projectId, "Project " + projectId, "compute", "eu01");
                        } else {
                            registry.recordFailure(projectId, "Project " + projectId, "storage", "eu01", 403, "Access denied");
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        final AccessIssuesSummaryDto summary = registry.getSummary();
        assertNotNull(summary);
        assertTrue(summary.getTotalProjectsChecked() <= 3);
    }
}
