package com.landvoigtit.stackit.resourceexplorer.network;

import com.landvoigtit.stackit.resourceexplorer.StackitSdkMockProducer;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class PublicIpResourceScraperTest {

    @Inject
    PublicIpResourceScraper scraper;

    @Inject
    StackitResourceRepository repository;

    @BeforeEach
    @Transactional
    public void cleanUp() {
        repository.delete("type = ?1", StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        repository.delete("type = ?1", StackitConstants.RESOURCE_TYPE_COMPUTE);
    }

    @Test
    @Transactional
    public void testScrapeAttachedPublicIp() {
        // Pre-create a compute instance that holds the mock public IP 193.148.160.10
        final StackitEntity server = new StackitEntity();
        final UUID serverId = UUID.randomUUID();
        server.setId(serverId);
        server.setResourceId(serverId.toString());
        server.setName("edge-gateway-vm");
        server.setType(StackitConstants.RESOURCE_TYPE_COMPUTE);
        server.setStatus("RUNNING");
        server.setRegion("eu01");
        server.setProjectId(StackitSdkMockProducer.MOCK_PROJECT_ID.toString());
        server.setCreatedAt(Instant.now());
        server.setUpdatedAt(Instant.now());
        server.setData(Map.of("publicIps", List.of("193.148.160.10")));
        repository.persistAndFlush(server);

        // Run scraper
        scraper.scrape();

        final List<StackitEntity> ips = repository.list("type = ?1 and deletedAt is null", StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        assertFalse(ips.isEmpty(), "Scraper should have persisted at least one public IP");

        final StackitEntity ipEntity = ips.stream()
                .filter(e -> "193.148.160.10".equals(e.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(ipEntity, "Expected public IP 193.148.160.10");
        assertEquals(StackitConstants.RESOURCE_TYPE_PUBLIC_IP, ipEntity.getType());
        assertEquals("ATTACHED", ipEntity.getStatus());
        assertEquals("true", ipEntity.getTags().get("attached"));

        assertNotNull(ipEntity.getData());
        assertEquals("193.148.160.10", ipEntity.getData().get("ip"));
        assertEquals(true, ipEntity.getData().get("attached"));
        assertEquals(serverId.toString(), ipEntity.getData().get("serverId"));
        assertEquals("edge-gateway-vm", ipEntity.getData().get("serverName"));
    }

    @Test
    @Transactional
    public void testScrapeUnattachedPublicIp() {
        // Run scraper without any servers in database
        scraper.scrape();

        final List<StackitEntity> ips = repository.list("type = ?1 and deletedAt is null", StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        assertFalse(ips.isEmpty(), "Scraper should have persisted at least one public IP");

        final StackitEntity ipEntity = ips.stream()
                .filter(e -> "193.148.160.10".equals(e.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(ipEntity);
        assertEquals("UNATTACHED", ipEntity.getStatus());
        assertEquals("false", ipEntity.getTags().get("attached"));
        assertEquals(false, ipEntity.getData().get("attached"));
        assertNull(ipEntity.getData().get("serverId"));
        assertNull(ipEntity.getData().get("serverName"));
    }

    @Test
    @Transactional
    public void testScrapeWithSoftDelete() {
        // Persist a stale public IP that will not be returned by MockIaasApi
        final StackitEntity staleIp = new StackitEntity();
        final UUID staleId = UUID.randomUUID();
        staleIp.setId(staleId);
        staleIp.setResourceId("stale-ip-id-999");
        staleIp.setName("193.148.160.99");
        staleIp.setType(StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        staleIp.setStatus("AVAILABLE");
        staleIp.setRegion("eu01");
        staleIp.setProjectId(StackitSdkMockProducer.MOCK_PROJECT_ID.toString());
        staleIp.setCreatedAt(Instant.now());
        staleIp.setUpdatedAt(Instant.now());
        staleIp.setData(Map.of("ip", "193.148.160.99", "attached", false));
        repository.persistAndFlush(staleIp);

        // Run scraper
        scraper.scrape();

        // Stale IP should be soft-deleted
        final StackitEntity retrievedStale = repository.findById(staleId);
        assertNotNull(retrievedStale);
        assertNotNull(retrievedStale.getDeletedAt(), "Stale public IP must be soft-deleted");

        // Newly scraped IP should be active
        final List<StackitEntity> activeIps = repository.list("type = ?1 and deletedAt is null", StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        assertTrue(activeIps.stream().anyMatch(e -> "193.148.160.10".equals(e.getName())));
    }
}
