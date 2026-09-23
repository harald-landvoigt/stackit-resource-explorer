package com.landvoigtit.stackit.resourceexplorer.access;

import com.landvoigtit.stackit.resourceexplorer.compute.ComputeResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.iam.IamResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkVpcResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.storage.StorageResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.storage.VmDiskResourceScraper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ScraperAccessIssueTrackingTest {

    @Inject
    ComputeResourceScraper computeScraper;

    @Inject
    StorageResourceScraper storageScraper;

    @Inject
    VmDiskResourceScraper vmDiskScraper;

    @Inject
    NetworkResourceScraper networkScraper;

    @Inject
    NetworkVpcResourceScraper networkVpcScraper;

    @Inject
    IamResourceScraper iamScraper;

    @Inject
    com.landvoigtit.stackit.resourceexplorer.billing.BillingResourceScraper billingScraper;

    @Inject
    AccessIssueRegistry registry;

    @BeforeEach
    void setUp() {
        registry.clear();
    }

    @Test
    void testScrapersRecordSuccessInAccessRegistry() {
        computeScraper.scrape();
        storageScraper.scrape();
        vmDiskScraper.scrape();
        networkScraper.scrape();
        networkVpcScraper.scrape();
        iamScraper.scrape();
        billingScraper.scrape();

        final AccessIssuesSummaryDto summary = registry.getSummary();
        assertNotNull(summary);
        assertTrue(summary.getTotalProjectsChecked() > 0, "Discovered projects should be recorded in registry");

        // The mock SDKs succeed, so there should be no access issues
        assertEquals(0, summary.getTotalIssues(), "Mock scrapes should succeed with 0 access issues");
        assertEquals(0, summary.getAffectedProjectsCount());

        // Check that matrix rows have ACCESSIBLE for scraped types
        final ProjectAccessMatrixRowDto firstRow = summary.getMatrix().getFirst();
        assertEquals(AccessStatus.ACCESSIBLE, firstRow.getStatuses().get("compute"));
        assertEquals(AccessStatus.ACCESSIBLE, firstRow.getStatuses().get("storage"));
        assertEquals(AccessStatus.ACCESSIBLE, firstRow.getStatuses().get("network"));
        assertEquals(AccessStatus.ACCESSIBLE, firstRow.getStatuses().get("network-vpc"));
        assertEquals(AccessStatus.ACCESSIBLE, firstRow.getStatuses().get("vmdisks"));
        assertEquals(AccessStatus.ACCESSIBLE, firstRow.getStatuses().get("iam"));
        assertEquals(AccessStatus.ACCESSIBLE, firstRow.getStatuses().get("billing"));
    }
}
