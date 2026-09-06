package com.landvoigtit.stackit.resourceexplorer.compute;

import cloud.stackit.sdk.iaas.v1api.api.IaasApi;
import cloud.stackit.sdk.iaas.v1api.model.Server;
import cloud.stackit.sdk.iaas.v1api.model.ServerListResponse;
import cloud.stackit.sdk.resourcemanager.v0api.api.ResourceManagerApi;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListProjectsResponse;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import io.quarkus.scheduler.Scheduled;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Optional;

@ApplicationScoped
@Slf4j
public class ComputeResourceScraper {

    @Inject
    IaasApi iaasApi;

    @Inject
    com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Scheduled(every = "${stackit.compute.schedule:1h}")
    public void scrape() {
        log.info("Starting Compute resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectCompute(project, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_COMPUTE, projectIdStr, currentResourceIds);
                }
            }
            log.info("Compute resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape Compute resources", e);
        }
    }

    private boolean scrapeProjectCompute(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        try {
            final ServerListResponse serverListResponse = iaasApi.listServers(project.getProjectId(), true, null);
            if (serverListResponse == null || serverListResponse.getItems() == null) {
                return true;
            }
            for (final Server server : serverListResponse.getItems()) {
                final ComputeResourceDto dto = ComputeResourceMapper.mapToDto(server);
                if (validator.validate(dto).isEmpty()) {
                    final StackitEntity entity = ComputeResourceMapper.mapToEntity(dto);
                    entity.setProjectId(projectIdStr);
                    repository.persistOrUpdate(entity);
                    currentResourceIds.add(entity.getResourceId());
                } else {
                    log.warn("Invalid Server DTO: {}", dto.getServerId());
                }
            }
            return true;
        } catch (final Exception e) {
            log.warn("Failed to scrape Compute resources for project {}: {}", projectIdStr, e.getMessage());
            return false;
        }
    }
}
