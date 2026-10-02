package com.landvoigtit.stackit.resourceexplorer;

import cloud.stackit.sdk.resourcemanager.v0api.api.ResourceManagerApi;
import cloud.stackit.sdk.resourcemanager.v0api.model.GetProjectResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListProjectsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Parent;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StackitProjectDiscoveryServiceTest {

    private StackitSdkConfig sdkConfig;
    private ResourceManagerApi resourceManagerApi;
    private StackitProjectDiscoveryService service;

    private static final String MOCK_ORG_ID = "869271ad-bde6-4e7b-99a1-0c2dab2b4171";
    private static final UUID PROJECT_ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    public void setUp() {
        sdkConfig = mock(StackitSdkConfig.class);
        resourceManagerApi = mock(ResourceManagerApi.class);

        when(sdkConfig.getDiscoveryCacheTtl()).thenReturn(Duration.ofMinutes(10));
        when(sdkConfig.getServiceAccountKeyPath()).thenReturn(Optional.of("/path/to/key.json"));
        when(sdkConfig.getDiscoveredOrganizationId()).thenReturn(MOCK_ORG_ID);

        service = new StackitProjectDiscoveryService(sdkConfig, resourceManagerApi);
    }

    private Project createProject(final UUID id, final String name) {
        final Project p = new Project();
        p.setProjectId(id);
        p.setName(name);
        p.setContainerId("container-" + id);
        return p;
    }

    @Test
    public void testDiscoverProjectsReturnsCachedWithinTtl() throws Exception {
        final Project p1 = createProject(PROJECT_ID_1, "Project Alpha");
        final ListProjectsResponse response = new ListProjectsResponse();
        response.setItems(List.of(p1));

        when(resourceManagerApi.listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any()))
                .thenReturn(response);

        // First call triggers remote discovery
        final List<Project> firstCall = service.discoverProjects();
        assertEquals(1, firstCall.size());
        assertEquals("Project Alpha", firstCall.get(0).getName());

        // Second call within TTL returns cached result without invoking API again
        final List<Project> secondCall = service.discoverProjects();
        assertEquals(1, secondCall.size());
        assertEquals("Project Alpha", secondCall.get(0).getName());

        verify(resourceManagerApi, times(1))
                .listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any());
    }

    @Test
    public void testDiscoverProjectsForceRefreshBypassesCache() throws Exception {
        final Project p1 = createProject(PROJECT_ID_1, "Project Alpha");
        final Project p2 = createProject(PROJECT_ID_2, "Project Beta");

        final ListProjectsResponse response1 = new ListProjectsResponse();
        response1.setItems(List.of(p1));

        final ListProjectsResponse response2 = new ListProjectsResponse();
        response2.setItems(List.of(p1, p2));

        when(resourceManagerApi.listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any()))
                .thenReturn(response1)
                .thenReturn(response2);

        // First call
        final List<Project> firstCall = service.discoverProjects(false);
        assertEquals(1, firstCall.size());

        // Force refresh call
        final List<Project> secondCall = service.discoverProjects(true);
        assertEquals(2, secondCall.size());

        verify(resourceManagerApi, times(2))
                .listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any());
    }

    @Test
    public void testGetProjectNamesMapUsesCache() throws Exception {
        final Project p1 = createProject(PROJECT_ID_1, "Project Alpha");
        final Project p2 = createProject(PROJECT_ID_2, "Project Beta");

        final ListProjectsResponse response = new ListProjectsResponse();
        response.setItems(List.of(p1, p2));

        when(resourceManagerApi.listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any()))
                .thenReturn(response);

        final Map<String, String> namesMap = service.getProjectNamesMap();
        assertNotNull(namesMap);
        assertEquals(2, namesMap.size());
        assertEquals("Project Alpha", namesMap.get(PROJECT_ID_1.toString()));
        assertEquals("Project Beta", namesMap.get(PROJECT_ID_2.toString()));

        // Repeated call uses cache
        final Map<String, String> cachedMap = service.getProjectNamesMap();
        assertEquals(namesMap, cachedMap);

        verify(resourceManagerApi, times(1))
                .listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any());
    }

    @Test
    public void testDiscoverOrganizationIdCachesResult() throws Exception {
        when(sdkConfig.getDiscoveredOrganizationId()).thenReturn(null);
        when(sdkConfig.getDiscoveredProjectId()).thenReturn(PROJECT_ID_1.toString());

        final GetProjectResponse projResponse = new GetProjectResponse();
        projResponse.setProjectId(PROJECT_ID_1);
        final Parent parent = new Parent();
        parent.setType(Parent.TypeEnum.ORGANIZATION);
        parent.setId(UUID.fromString(MOCK_ORG_ID));
        projResponse.setParent(parent);

        when(resourceManagerApi.getProject(eq(PROJECT_ID_1.toString()), eq(true))).thenReturn(projResponse);

        final String orgId1 = service.discoverOrganizationId();
        assertEquals(MOCK_ORG_ID, orgId1);

        final String orgId2 = service.discoverOrganizationId();
        assertEquals(MOCK_ORG_ID, orgId2);

        verify(resourceManagerApi, times(1)).getProject(eq(PROJECT_ID_1.toString()), eq(true));
    }

    @Test
    public void testConcurrentRequestsCoalesce() throws Exception {
        final Project p1 = createProject(PROJECT_ID_1, "Project Alpha");
        final ListProjectsResponse response = new ListProjectsResponse();
        response.setItems(List.of(p1));

        when(resourceManagerApi.listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(50); // Simulate API latency
                    return response;
                });

        final int threads = 8;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch latch = new CountDownLatch(threads);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    final List<Project> projects = service.discoverProjects();
                    if (projects != null && !projects.isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threads, successCount.get());
        verify(resourceManagerApi, times(1))
                .listProjects(eq(MOCK_ORG_ID), any(), any(), any(), any(), any());
    }
}
