package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.compute.ComputeResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.iam.IamResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkVpcResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.storage.StorageResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.storage.VmDiskResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ResourceScrapersTest {

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
    StackitResourceRepository repository;

    @Test
    public final void testComputeScrape() {
        computeScraper.scrape();
        assertFalse(repository.listAll().isEmpty(), "Should have persisted scraped compute resources");
    }

    @Test
    public final void testStorageScrape() {
        storageScraper.scrape();
        assertFalse(repository.listAll().isEmpty(), "Should have persisted scraped storage resources");
    }

    @Test
    public final void testVmDiskScrape() {
        vmDiskScraper.scrape();
        assertFalse(repository.listAll().isEmpty(), "Should have persisted scraped vmdisk resources");
    }

    @Test
    public final void testNetworkScrape() {
        networkScraper.scrape();
        assertFalse(repository.listAll().isEmpty(), "Should have persisted scraped network resources");
    }

    @Test
    public final void testNetworkVpcScrape() {
        networkVpcScraper.scrape();
        assertFalse(repository.listAll().isEmpty(), "Should have persisted scraped network-vpc resources");
    }

    @Test
    public final void testIamScrape() {
        iamScraper.scrape();
        assertFalse(repository.listAll().isEmpty(), "Should have persisted scraped IAM resources");
    }
}
