package com.landvoigtit.stackit.resourceexplorer.dns;

import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.StackitSdkMockProducer;
import com.landvoigtit.stackit.resourceexplorer.access.AccessIssueRegistry;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
public class DnsResourceScraperTest {

    @Inject
    DnsResourceScraper scraper;

    @Inject
    StackitResourceRepository repository;

    @Inject
    AccessIssueRegistry accessIssueRegistry;

    @BeforeEach
    @Transactional
    public void cleanUp() {
        repository.delete("type = ?1", StackitConstants.RESOURCE_TYPE_DNS_ZONE);
        repository.delete("type = ?1", StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        repository.delete("type = ?1", StackitConstants.RESOURCE_TYPE_COMPUTE);
    }

    @Test
    @Transactional
    public void testScrapeAttachedDnsZoneAndRecordSets() {
        // Pre-create compute instance matching record IP 193.148.160.10
        final StackitEntity server = new StackitEntity();
        final UUID serverId = UUID.randomUUID();
        server.setId(serverId);
        server.setResourceId("server-vm-mock-01");
        server.setName("edge-gateway-vm");
        server.setType(StackitConstants.RESOURCE_TYPE_COMPUTE);
        server.setStatus("RUNNING");
        server.setRegion("eu01");
        server.setProjectId(StackitSdkMockProducer.MOCK_PROJECT_ID.toString());
        server.setCreatedAt(Instant.now());
        server.setUpdatedAt(Instant.now());
        server.setData(Map.of("publicIps", List.of("193.148.160.10")));
        repository.persistAndFlush(server);

        // Pre-create public IP matching record IP 193.148.160.10
        final StackitEntity publicIp = new StackitEntity();
        final UUID pipId = UUID.randomUUID();
        publicIp.setId(pipId);
        publicIp.setResourceId("pip-mock-01");
        publicIp.setName("193.148.160.10");
        publicIp.setType(StackitConstants.RESOURCE_TYPE_PUBLIC_IP);
        publicIp.setStatus("ATTACHED");
        publicIp.setRegion("eu01");
        publicIp.setProjectId(StackitSdkMockProducer.MOCK_PROJECT_ID.toString());
        publicIp.setCreatedAt(Instant.now());
        publicIp.setUpdatedAt(Instant.now());
        publicIp.setData(Map.of("ip", "193.148.160.10"));
        repository.persistAndFlush(publicIp);

        // Run scraper
        scraper.scrape();

        final List<StackitEntity> zones = repository.list("type = ?1 and deletedAt is null", StackitConstants.RESOURCE_TYPE_DNS_ZONE);
        assertFalse(zones.isEmpty(), "Scraper should have persisted at least one DNS zone");

        final StackitEntity zoneEntity = zones.stream()
                .filter(e -> "example.com.".equals(e.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(zoneEntity, "Expected DNS zone example.com.");
        assertEquals("zone-mock-01", zoneEntity.getResourceId());
        assertEquals(StackitConstants.RESOURCE_TYPE_DNS_ZONE, zoneEntity.getType());
        assertEquals("ACTIVE", zoneEntity.getStatus());
        assertEquals(StackitConstants.GLOBAL_REGION, zoneEntity.getRegion());

        assertNotNull(zoneEntity.getData());
        final Object recordSetsObj = zoneEntity.getData().get("recordSets");
        assertTrue(recordSetsObj instanceof List<?>);

        final List<?> recordSets = (List<?>) recordSetsObj;
        assertFalse(recordSets.isEmpty(), "DNS zone should have mapped record sets");

        final Object firstRs = recordSets.get(0);
        if (firstRs instanceof DnsRecordSetDto rsDto) {
            assertEquals("rs-mock-01", rsDto.getId());
            assertEquals("A", rsDto.getType());
            assertEquals("server-vm-mock-01", rsDto.getMatchedServerId());
            assertEquals("edge-gateway-vm", rsDto.getMatchedServerName());
            assertEquals("pip-mock-01", rsDto.getMatchedPublicIpId());
        } else if (firstRs instanceof Map<?, ?> rsMap) {
            assertEquals("rs-mock-01", rsMap.get("id"));
            assertEquals("A", rsMap.get("type"));
            assertEquals("server-vm-mock-01", rsMap.get("matchedServerId"));
            assertEquals("edge-gateway-vm", rsMap.get("matchedServerName"));
            assertEquals("pip-mock-01", rsMap.get("matchedPublicIpId"));
        } else {
            fail("Unexpected record set type: " + firstRs.getClass());
        }

        final List<StackitEntity> recordSetEntities = repository.list("type = ?1 and deletedAt is null", StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET);
        assertFalse(recordSetEntities.isEmpty(), "Scraper should have persisted standalone DNS record sets");
        final StackitEntity rsEntity = recordSetEntities.stream()
                .filter(e -> "rs-mock-01".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(rsEntity, "Expected record set entity rs-mock-01");
        assertEquals(StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET, rsEntity.getType());
        assertEquals("ACTIVE", rsEntity.getStatus());
        assertEquals("server-vm-mock-01", rsEntity.getData().get("matchedServerId"));
        assertEquals("edge-gateway-vm", rsEntity.getData().get("matchedServerName"));
        assertEquals("pip-mock-01", rsEntity.getData().get("matchedPublicIpId"));
    }

    @Test
    @Transactional
    public void testScrapeWithSoftDelete() {
        // Persist stale DNS zone
        final StackitEntity staleZone = new StackitEntity();
        final UUID staleId = UUID.randomUUID();
        staleZone.setId(staleId);
        staleZone.setResourceId("stale-zone-id-999");
        staleZone.setName("stale.example.com.");
        staleZone.setType(StackitConstants.RESOURCE_TYPE_DNS_ZONE);
        staleZone.setStatus("ACTIVE");
        staleZone.setRegion(StackitConstants.GLOBAL_REGION);
        staleZone.setProjectId(StackitSdkMockProducer.MOCK_PROJECT_ID.toString());
        staleZone.setCreatedAt(Instant.now());
        staleZone.setUpdatedAt(Instant.now());
        repository.persistAndFlush(staleZone);

        // Persist stale DNS record set
        final StackitEntity staleRecord = new StackitEntity();
        final UUID staleRecordId = UUID.randomUUID();
        staleRecord.setId(staleRecordId);
        staleRecord.setResourceId("stale-record-id-999");
        staleRecord.setName("old.example.com.");
        staleRecord.setType(StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET);
        staleRecord.setStatus("ACTIVE");
        staleRecord.setRegion(StackitConstants.GLOBAL_REGION);
        staleRecord.setProjectId(StackitSdkMockProducer.MOCK_PROJECT_ID.toString());
        staleRecord.setCreatedAt(Instant.now());
        staleRecord.setUpdatedAt(Instant.now());
        repository.persistAndFlush(staleRecord);

        // Run scraper
        scraper.scrape();

        final StackitEntity retrievedStale = repository.findById(staleId);
        assertNotNull(retrievedStale);
        assertNotNull(retrievedStale.getDeletedAt(), "Stale DNS zone must be soft-deleted");

        final StackitEntity retrievedStaleRecord = repository.findById(staleRecordId);
        assertNotNull(retrievedStaleRecord);
        assertNotNull(retrievedStaleRecord.getDeletedAt(), "Stale DNS record set must be soft-deleted");

        final List<StackitEntity> activeZones = repository.list("type = ?1 and deletedAt is null", StackitConstants.RESOURCE_TYPE_DNS_ZONE);
        assertTrue(activeZones.stream().anyMatch(e -> "example.com.".equals(e.getName())));

        final List<StackitEntity> activeRecords = repository.list("type = ?1 and deletedAt is null", StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET);
        assertTrue(activeRecords.stream().anyMatch(e -> "rs-mock-01".equals(e.getResourceId())));
    }

    @Test
    public void testScrapePermissionDeniedUnit() throws IOException {
        final DnsApiClient dnsApiMock = mock(DnsApiClient.class);
        final StackitProjectDiscoveryService discoveryMock = mock(StackitProjectDiscoveryService.class);
        final StackitResourceRepository repoMock = mock(StackitResourceRepository.class);
        final AccessIssueRegistry registryMock = mock(AccessIssueRegistry.class);

        final Project project = new Project();
        project.setProjectId(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        project.setName("Secure Project");
        when(discoveryMock.discoverProjects()).thenReturn(List.of(project));

        when(dnsApiMock.listZones(anyString()))
                .thenThrow(new IOException("Failed to fetch DNS zones: HTTP 403 Forbidden - User lacks dns.reader"));

        final DnsResourceScraper unitScraper = new DnsResourceScraper();
        unitScraper.dnsApiClient = dnsApiMock;
        unitScraper.projectDiscoveryService = discoveryMock;
        unitScraper.repository = repoMock;
        unitScraper.accessIssueRegistry = registryMock;

        unitScraper.scrape();

        verify(registryMock, times(1)).recordFailure(
                eq("12345678-1234-1234-1234-123456789012"),
                eq("Secure Project"),
                eq(StackitConstants.RESOURCE_TYPE_DNS_ZONE),
                isNull(),
                eq(403),
                contains("403")
        );
        verify(repoMock, never()).softDeleteMissing(anyString(), anyString(), anyList());
    }

    @Test
    public void testScrapeServiceDisabledUnit() throws IOException {
        final DnsApiClient dnsApiMock = mock(DnsApiClient.class);
        final StackitProjectDiscoveryService discoveryMock = mock(StackitProjectDiscoveryService.class);
        final StackitResourceRepository repoMock = mock(StackitResourceRepository.class);
        final AccessIssueRegistry registryMock = mock(AccessIssueRegistry.class);

        final Project project = new Project();
        project.setProjectId(UUID.fromString("12345678-1234-1234-1234-123456789012"));
        project.setName("Disabled DNS Project");
        when(discoveryMock.discoverProjects()).thenReturn(List.of(project));

        when(dnsApiMock.listZones(anyString()))
                .thenThrow(new IOException("HTTP 404: Service not enabled for this project"));

        final DnsResourceScraper unitScraper = new DnsResourceScraper();
        unitScraper.dnsApiClient = dnsApiMock;
        unitScraper.projectDiscoveryService = discoveryMock;
        unitScraper.repository = repoMock;
        unitScraper.accessIssueRegistry = registryMock;

        unitScraper.scrape();

        verify(registryMock, never()).recordFailure(anyString(), anyString(), anyString(), any(), anyInt(), anyString());
        verify(registryMock, times(1)).recordSuccess(
                eq("12345678-1234-1234-1234-123456789012"),
                eq("Disabled DNS Project"),
                eq(StackitConstants.RESOURCE_TYPE_DNS_ZONE),
                isNull()
        );
        verify(repoMock, times(1)).softDeleteMissing(
                eq(StackitConstants.RESOURCE_TYPE_DNS_ZONE),
                eq("12345678-1234-1234-1234-123456789012"),
                eq(List.of())
        );
    }

    @Test
    public void testScrapeEmptyProjects() {
        final DnsApiClient dnsApiMock = mock(DnsApiClient.class);
        final StackitProjectDiscoveryService discoveryMock = mock(StackitProjectDiscoveryService.class);
        final StackitResourceRepository repoMock = mock(StackitResourceRepository.class);

        when(discoveryMock.discoverProjects()).thenReturn(List.of());

        final DnsResourceScraper unitScraper = new DnsResourceScraper();
        unitScraper.dnsApiClient = dnsApiMock;
        unitScraper.projectDiscoveryService = discoveryMock;
        unitScraper.repository = repoMock;

        unitScraper.scrape();

        verifyNoInteractions(dnsApiMock);
        verifyNoInteractions(repoMock);
    }
}
