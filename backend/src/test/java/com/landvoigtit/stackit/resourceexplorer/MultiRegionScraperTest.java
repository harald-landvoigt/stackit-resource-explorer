package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.compute.ComputeResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkVpcResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import com.landvoigtit.stackit.resourceexplorer.storage.StorageResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.storage.VmDiskResourceScraper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class MultiRegionScraperTest {

    @Inject
    StorageResourceScraper storageScraper;

    @Inject
    NetworkResourceScraper albScraper;

    @Inject
    ComputeResourceScraper computeScraper;

    @Inject
    VmDiskResourceScraper diskScraper;

    @Inject
    NetworkVpcResourceScraper vpcScraper;

    @Inject
    StackitResourceRepository repository;

    @Inject
    StackitSdkConfig sdkConfig;

    private static final String MOCK_PROJECT_ID = StackitSdkMockProducer.MOCK_PROJECT_ID.toString();

    @BeforeEach
    @Transactional
    public void cleanUp() {
        repository.deleteAll();
    }

    @Test
    public void testConfiguredRegions() {
        final List<String> regions = sdkConfig.getRegions();
        assertNotNull(regions);
        assertTrue(regions.contains(StackitConstants.REGION_EU01));
        assertTrue(regions.contains(StackitConstants.REGION_EU02));
    }

    @Test
    public void testStorageScraperMultiRegion() {
        storageScraper.scrape();

        final List<StackitEntity> buckets = repository.find("type = ?1 and projectId = ?2",
                StackitConstants.RESOURCE_TYPE_STORAGE, MOCK_PROJECT_ID).list();

        assertFalse(buckets.isEmpty(), "Buckets should be scraped");
        assertEquals(2, buckets.size(), "Should have scraped buckets across 2 regions");

        final StackitEntity eu01Bucket = buckets.stream()
                .filter(b -> StackitConstants.REGION_EU01.equalsIgnoreCase(b.getRegion()))
                .findFirst()
                .orElse(null);
        final StackitEntity eu02Bucket = buckets.stream()
                .filter(b -> StackitConstants.REGION_EU02.equalsIgnoreCase(b.getRegion()))
                .findFirst()
                .orElse(null);

        assertNotNull(eu01Bucket, "Bucket in eu01 should exist");
        assertNotNull(eu02Bucket, "Bucket in eu02 should exist");
        assertNotEquals(eu01Bucket.getId(), eu02Bucket.getId(), "Entity UUIDs must be distinct across regions");
        assertNull(eu01Bucket.getDeletedAt(), "eu01 bucket should not be soft-deleted");
        assertNull(eu02Bucket.getDeletedAt(), "eu02 bucket should not be soft-deleted");
    }

    @Test
    public void testAlbScraperMultiRegion() {
        albScraper.scrape();

        final List<StackitEntity> lbs = repository.find("type = ?1 and projectId = ?2",
                StackitConstants.RESOURCE_TYPE_NETWORK, MOCK_PROJECT_ID).list();

        assertFalse(lbs.isEmpty(), "Load balancers should be scraped");
        assertEquals(2, lbs.size(), "Should have scraped load balancers across 2 regions");

        final StackitEntity eu01Lb = lbs.stream()
                .filter(l -> StackitConstants.REGION_EU01.equalsIgnoreCase(l.getRegion()))
                .findFirst()
                .orElse(null);
        final StackitEntity eu02Lb = lbs.stream()
                .filter(l -> StackitConstants.REGION_EU02.equalsIgnoreCase(l.getRegion()))
                .findFirst()
                .orElse(null);

        assertNotNull(eu01Lb, "LB in eu01 should exist");
        assertNotNull(eu02Lb, "LB in eu02 should exist");
        assertNotEquals(eu01Lb.getId(), eu02Lb.getId(), "Entity UUIDs must be distinct across regions");
    }

    @Test
    public void testComputeScraperMultiRegion() {
        computeScraper.scrape();

        final List<StackitEntity> vms = repository.find("type = ?1 and projectId = ?2",
                StackitConstants.RESOURCE_TYPE_COMPUTE, MOCK_PROJECT_ID).list();

        assertFalse(vms.isEmpty(), "Virtual machines should be scraped");
        for (final StackitEntity vm : vms) {
            assertNull(vm.getDeletedAt(), "VM should not be soft-deleted");
        }
    }

    @Test
    public void testVmDiskScraperMultiRegion() {
        diskScraper.scrape();

        final List<StackitEntity> disks = repository.find("type = ?1 and projectId = ?2",
                StackitConstants.RESOURCE_TYPE_VMDISKS, MOCK_PROJECT_ID).list();

        assertFalse(disks.isEmpty(), "Disks should be scraped");
        for (final StackitEntity disk : disks) {
            assertNull(disk.getDeletedAt(), "Disk should not be soft-deleted");
        }
    }

    @Test
    public void testNetworkVpcScraperMultiRegion() {
        vpcScraper.scrape();

        final List<StackitEntity> networks = repository.find("type = ?1 and projectId = ?2",
                StackitConstants.RESOURCE_TYPE_NETWORK_VPC, MOCK_PROJECT_ID).list();

        assertFalse(networks.isEmpty(), "VPCs should be scraped");
        for (final StackitEntity net : networks) {
            assertNull(net.getDeletedAt(), "Network should not be soft-deleted");
        }
    }

    @Test
    @Transactional
    public void testCrossRegionSafeSoftDelete() {
        // Pre-insert an old bucket that no longer exists remotely
        final StackitEntity staleBucket = new StackitEntity();
        staleBucket.setId(java.util.UUID.randomUUID());
        staleBucket.setResourceId("stale-old-bucket");
        staleBucket.setName("stale-old-bucket");
        staleBucket.setType(StackitConstants.RESOURCE_TYPE_STORAGE);
        staleBucket.setStatus(StackitConstants.STATUS_ACTIVE);
        staleBucket.setRegion(StackitConstants.REGION_EU01);
        staleBucket.setProjectId(MOCK_PROJECT_ID);
        staleBucket.setCreatedAt(java.time.Instant.now());
        staleBucket.setUpdatedAt(java.time.Instant.now());
        repository.persist(staleBucket);

        // Run scraper which discovers mock-bucket-eu01 and mock-bucket-eu02
        storageScraper.scrape();

        final StackitEntity updatedStale = repository.findById(staleBucket.getId());
        assertNotNull(updatedStale);
        assertNotNull(updatedStale.getDeletedAt(), "Stale bucket should be soft-deleted");

        final List<StackitEntity> activeBuckets = repository.find("type = ?1 and projectId = ?2 and deletedAt is null",
                StackitConstants.RESOURCE_TYPE_STORAGE, MOCK_PROJECT_ID).list();
        assertEquals(2, activeBuckets.size(), "Both eu01 and eu02 active buckets must remain active");
    }
}

