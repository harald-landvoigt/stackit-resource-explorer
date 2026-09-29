package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.core.exception.ApiException;
import cloud.stackit.sdk.iaas.v1api.api.IaasApi;
import cloud.stackit.sdk.iaas.v1api.model.Volume;
import cloud.stackit.sdk.iaas.v1api.model.VolumeListResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.access.AccessIssueRegistry;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class VmDiskResourceScraperUnitTest {

    private IaasApi iaasApi;
    private StackitProjectDiscoveryService projectDiscoveryService;
    private StackitResourceRepository repository;
    private Validator validator;
    private StackitSdkConfig sdkConfig;
    private AccessIssueRegistry accessIssueRegistry;
    private VmDiskResourceScraper scraper;

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PROJECT_NAME = "Test Project";

    @BeforeEach
    public void setUp() throws Exception {
        iaasApi = mock(IaasApi.class);
        projectDiscoveryService = mock(StackitProjectDiscoveryService.class);
        repository = mock(StackitResourceRepository.class);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        sdkConfig = mock(StackitSdkConfig.class);
        accessIssueRegistry = mock(AccessIssueRegistry.class);

        when(sdkConfig.getRegions()).thenReturn(List.of("eu01"));
        when(sdkConfig.iaasApiForRegion(anyString(), any())).thenReturn(iaasApi);

        final Project project = new Project();
        project.setProjectId(PROJECT_ID);
        project.setName(PROJECT_NAME);
        when(projectDiscoveryService.discoverProjects()).thenReturn(List.of(project));

        scraper = new VmDiskResourceScraper();
        setField(scraper, "iaasApi", iaasApi);
        setField(scraper, "projectDiscoveryService", projectDiscoveryService);
        setField(scraper, "repository", repository);
        setField(scraper, "validator", validator);
        setField(scraper, "sdkConfig", sdkConfig);
        setField(scraper, "accessIssueRegistry", accessIssueRegistry);
    }

    private void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testDisabledServiceDoesNotRegisterAccessIssue() throws Exception {
        final ApiException disabledException = new ApiException(404, "404 Not Found");
        when(iaasApi.listVolumes(eq(PROJECT_ID), any()))
                .thenThrow(disabledException);

        scraper.scrape();

        verify(accessIssueRegistry, never()).recordFailure(any(), any(), any(), any(), anyInt(), any());
        verify(accessIssueRegistry, times(1)).recordSuccess(eq(PROJECT_ID.toString()), eq(PROJECT_NAME), eq(StackitConstants.RESOURCE_TYPE_VMDISKS), isNull());
    }

    @Test
    public void testTruePermissionDeniedRegistersFailure() throws Exception {
        final ApiException forbiddenException = new ApiException(403, "403 Forbidden: User lacks volume.admin permission");
        when(iaasApi.listVolumes(eq(PROJECT_ID), any()))
                .thenThrow(forbiddenException);

        scraper.scrape();

        verify(accessIssueRegistry, times(1)).recordFailure(
                eq(PROJECT_ID.toString()),
                eq(PROJECT_NAME),
                eq(StackitConstants.RESOURCE_TYPE_VMDISKS),
                eq("eu01"),
                eq(403),
                contains("User lacks volume.admin permission")
        );
        verify(accessIssueRegistry, never()).recordSuccess(any(), any(), any(), any());
    }

    @Test
    public void testSuccessfulScrapeRegistersSuccess() throws Exception {
        final Volume volume = new Volume(null, null, UUID.randomUUID(), null, null, "AVAILABLE", null);
        volume.setName("test-volume");
        final VolumeListResponse response = new VolumeListResponse();
        response.setItems(List.of(volume));

        when(iaasApi.listVolumes(eq(PROJECT_ID), any()))
                .thenReturn(response);

        scraper.scrape();

        verify(accessIssueRegistry, never()).recordFailure(any(), any(), any(), any(), anyInt(), any());
        verify(accessIssueRegistry, times(1)).recordSuccess(eq(PROJECT_ID.toString()), eq(PROJECT_NAME), eq(StackitConstants.RESOURCE_TYPE_VMDISKS), isNull());
        verify(repository, atLeastOnce()).persistOrUpdate(any());
    }
}
