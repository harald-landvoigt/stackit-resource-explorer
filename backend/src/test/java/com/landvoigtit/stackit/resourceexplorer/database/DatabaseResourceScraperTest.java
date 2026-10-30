package com.landvoigtit.stackit.resourceexplorer.database;

import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.access.AccessIssueRegistry;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DatabaseResourceScraperTest {

    private DatabaseApiClient apiClientMock;
    private StackitProjectDiscoveryService discoveryMock;
    private StackitResourceRepository repoMock;
    private AccessIssueRegistry registryMock;
    private StackitSdkConfig sdkConfigMock;
    private DatabaseResourceScraper scraper;

    @BeforeEach
    public void setUp() {
        apiClientMock = mock(DatabaseApiClient.class);
        discoveryMock = mock(StackitProjectDiscoveryService.class);
        repoMock = mock(StackitResourceRepository.class);
        registryMock = mock(AccessIssueRegistry.class);
        sdkConfigMock = mock(StackitSdkConfig.class);

        when(sdkConfigMock.getRegions()).thenReturn(List.of("eu01"));

        scraper = new DatabaseResourceScraper();
        scraper.databaseApiClient = apiClientMock;
        scraper.projectDiscoveryService = discoveryMock;
        scraper.repository = repoMock;
        scraper.accessIssueRegistry = registryMock;
        scraper.sdkConfig = sdkConfigMock;
    }

    @Test
    public void testScrapeSuccessfulMultiEngineDiscovery() throws IOException {
        final Project project = new Project();
        project.setProjectId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        project.setName("DB Test Project");
        when(discoveryMock.discoverProjects()).thenReturn(List.of(project));

        when(apiClientMock.listInstances(eq("postgresql"), anyString(), anyString())).thenReturn(List.of(
                Map.of("id", "pg-inst-1", "name", "postgres-main", "status", "Ready", "flavor", "c1.2", "storage", 50)
        ));
        when(apiClientMock.listInstances(eq("mongodb"), anyString(), anyString())).thenReturn(List.of(
                Map.of("id", "mongo-inst-1", "name", "mongo-main", "status", "Ready", "flavor", "small", "storage", 10)
        ));
        when(apiClientMock.listInstances(argThat(e -> !"postgresql".equals(e) && !"mongodb".equals(e)), anyString(), anyString()))
                .thenReturn(List.of());

        scraper.scrape();

        final ArgumentCaptor<StackitEntity> entityCaptor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repoMock, atLeast(2)).persistOrUpdate(entityCaptor.capture());

        final List<StackitEntity> saved = entityCaptor.getAllValues();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(e -> "pg-inst-1".equals(e.getResourceId())));
        assertTrue(saved.stream().anyMatch(e -> "mongo-inst-1".equals(e.getResourceId())));

        verify(repoMock, times(1)).softDeleteMissing(
                eq(StackitConstants.RESOURCE_TYPE_DATABASE),
                eq("11111111-1111-1111-1111-111111111111"),
                argThat(ids -> ids.contains("pg-inst-1") && ids.contains("mongo-inst-1"))
        );

        verify(apiClientMock, never()).listInstances(eq("redis"), anyString(), anyString());

        verify(registryMock, times(1)).recordSuccess(
                eq("11111111-1111-1111-1111-111111111111"),
                eq("DB Test Project"),
                eq(StackitConstants.RESOURCE_TYPE_DATABASE),
                isNull()
        );
    }

    @Test
    public void testScrapeServiceDisabledHandledGracefully() throws IOException {
        final Project project = new Project();
        project.setProjectId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        project.setName("Disabled DB Project");
        when(discoveryMock.discoverProjects()).thenReturn(List.of(project));

        when(apiClientMock.listInstances(anyString(), anyString(), anyString()))
                .thenThrow(new IOException("HTTP 404: Service not enabled for this project"));

        scraper.scrape();

        verify(registryMock, never()).recordFailure(anyString(), anyString(), anyString(), any(), anyInt(), anyString());
        verify(registryMock, times(1)).recordSuccess(
                eq("22222222-2222-2222-2222-222222222222"),
                eq("Disabled DB Project"),
                eq(StackitConstants.RESOURCE_TYPE_DATABASE),
                isNull()
        );
        verify(repoMock, times(1)).softDeleteMissing(
                eq(StackitConstants.RESOURCE_TYPE_DATABASE),
                eq("22222222-2222-2222-2222-222222222222"),
                eq(List.of())
        );
    }

    @Test
    public void testScrapePermissionDeniedRecordedInAccessIssueRegistry() throws IOException {
        final Project project = new Project();
        project.setProjectId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        project.setName("Restricted DB Project");
        when(discoveryMock.discoverProjects()).thenReturn(List.of(project));

        when(apiClientMock.listInstances(anyString(), anyString(), anyString()))
                .thenThrow(new IOException("Failed to fetch postgresql instances: HTTP 403 Forbidden - User lacks database.reader"));

        scraper.scrape();

        verify(registryMock, atLeastOnce()).recordFailure(
                eq("33333333-3333-3333-3333-333333333333"),
                eq("Restricted DB Project"),
                eq(StackitConstants.RESOURCE_TYPE_DATABASE),
                any(),
                eq(403),
                contains("403")
        );
        verify(repoMock, never()).softDeleteMissing(anyString(), anyString(), anyList());
    }

    @Test
    public void testScrapeEmptyProjectsSkipped() {
        when(discoveryMock.discoverProjects()).thenReturn(List.of());
        scraper.scrape();
        verifyNoInteractions(apiClientMock);
        verifyNoInteractions(repoMock);
    }

    @Test
    public void testScrapeMultiRegionDiscoversInstancesAcrossRegions() throws IOException {
        when(sdkConfigMock.getRegions()).thenReturn(List.of("eu01", "eu02"));

        final Project project = new Project();
        project.setProjectId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        project.setName("Multi-Region DB Project");
        when(discoveryMock.discoverProjects()).thenReturn(List.of(project));

        when(apiClientMock.listInstances(eq("postgresql"), eq("44444444-4444-4444-4444-444444444444"), eq("eu01")))
                .thenReturn(List.of(Map.of("id", "pg-eu01-1", "name", "postgres-eu01", "status", "Ready")));
        when(apiClientMock.listInstances(eq("postgresql"), eq("44444444-4444-4444-4444-444444444444"), eq("eu02")))
                .thenReturn(List.of(Map.of("id", "pg-eu02-1", "name", "postgres-eu02", "status", "Ready")));

        when(apiClientMock.listInstances(eq("mongodb"), eq("44444444-4444-4444-4444-444444444444"), eq(StackitConstants.REGION_EU01)))
                .thenReturn(List.of(Map.of("id", "mongo-global-1", "name", "mongo-cluster", "status", "Ready")));

        when(apiClientMock.listInstances(argThat(e -> !"postgresql".equals(e) && !"mongodb".equals(e)), anyString(), any()))
                .thenReturn(List.of());

        scraper.scrape();

        final ArgumentCaptor<StackitEntity> entityCaptor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repoMock, atLeast(3)).persistOrUpdate(entityCaptor.capture());

        final List<StackitEntity> saved = entityCaptor.getAllValues();
        assertEquals(3, saved.size());

        final StackitEntity eu01Pg = saved.stream()
                .filter(e -> "pg-eu01-1".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        final StackitEntity eu02Pg = saved.stream()
                .filter(e -> "pg-eu02-1".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        final StackitEntity mongo = saved.stream()
                .filter(e -> "mongo-global-1".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);

        assertNotNull(eu01Pg);
        assertEquals("eu01", eu01Pg.getRegion());

        assertNotNull(eu02Pg);
        assertEquals("eu02", eu02Pg.getRegion());

        assertNotNull(mongo);

        verify(repoMock, times(1)).softDeleteMissing(
                eq(StackitConstants.RESOURCE_TYPE_DATABASE),
                eq("44444444-4444-4444-4444-444444444444"),
                argThat(ids -> ids.contains("pg-eu01-1") && ids.contains("pg-eu02-1") && ids.contains("mongo-global-1"))
        );
    }

    @Test
    public void testScrapeEnrichesInstancesWithNetworkAndLabelDetailsFromGetInstance() throws IOException {
        when(sdkConfigMock.getRegions()).thenReturn(List.of("eu02"));

        final Project project = new Project();
        project.setProjectId(UUID.fromString("c2cc46ae-e432-4e4a-b4df-42a4066c344b"));
        project.setName("sandbox-2");
        when(discoveryMock.discoverProjects()).thenReturn(List.of(project));

        when(apiClientMock.listInstances(eq("postgresql"), eq("c2cc46ae-e432-4e4a-b4df-42a4066c344b"), eq("eu02")))
                .thenReturn(List.of(Map.of(
                        "id", "e81e1e10-27b2-47df-9654-dc1388461787",
                        "name", "postgres-dvqco116br3j",
                        "status", "Ready"
                )));
        when(apiClientMock.getInstance(
                eq("postgresql"),
                eq("c2cc46ae-e432-4e4a-b4df-42a4066c344b"),
                eq("eu02"),
                eq("e81e1e10-27b2-47df-9654-dc1388461787")
        )).thenReturn(Map.of(
                "id", "e81e1e10-27b2-47df-9654-dc1388461787",
                "name", "postgres-dvqco116br3j",
                "version", "17",
                "flavor", Map.of("id", "2.4", "description", "Small, Compute optimized"),
                "storage", Map.of("size", 5, "class", "premium-perf2-stackit"),
                "connectionInfo", Map.of(
                        "write", Map.of(
                                "host", "e81e1e10-27b2-47df-9654-dc1388461787.postgresql.eu02.onstackit.cloud",
                                "port", 5432
                        )
                ),
                "network", Map.of(
                        "accessScope", "PUBLIC",
                        "acl", List.of("193.148.160.0/19", "45.129.40.0/21")
                ),
                "labels", Map.of(
                        "cost_center", "4711",
                        "owner", "harald.landvoigt"
                )
        ));
        when(apiClientMock.listInstances(argThat(e -> !"postgresql".equals(e)), anyString(), any()))
                .thenReturn(List.of());

        scraper.scrape();

        final ArgumentCaptor<StackitEntity> entityCaptor = ArgumentCaptor.forClass(StackitEntity.class);
        verify(repoMock, times(1)).persistOrUpdate(entityCaptor.capture());

        final StackitEntity saved = entityCaptor.getValue();
        assertEquals("e81e1e10-27b2-47df-9654-dc1388461787", saved.getResourceId());
        assertEquals(
                "e81e1e10-27b2-47df-9654-dc1388461787.postgresql.eu02.onstackit.cloud",
                saved.getData().get("host")
        );
        assertEquals(5432, saved.getData().get("port"));
        assertEquals("PUBLIC", saved.getData().get("accessScope"));
        assertEquals(List.of("193.148.160.0/19", "45.129.40.0/21"), saved.getData().get("acls"));
        assertEquals("4711", saved.getTags().get("cost_center"));
        assertEquals("harald.landvoigt", saved.getTags().get("owner"));
        assertEquals(Map.of("cost_center", "4711", "owner", "harald.landvoigt"), saved.getData().get("labels"));
    }
}
