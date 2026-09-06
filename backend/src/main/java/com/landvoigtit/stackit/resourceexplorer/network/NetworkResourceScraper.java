package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.alb.v2api.api.AlbApi;
import cloud.stackit.sdk.alb.v2api.model.LoadBalancer;
import cloud.stackit.sdk.alb.v2api.model.ListLoadBalancersResponse;
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
public class NetworkResourceScraper {

    @Inject
    AlbApi albApi;

    @Inject
    com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Scheduled(every = "${stackit.network.schedule:1h}")
    public void scrape() {
        log.info("Starting Network resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("Network resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectAlb(project, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_NETWORK, projectIdStr, currentResourceIds);
                }
            }
            log.info("Network resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape Network resources", e);
        }
    }

    private boolean scrapeProjectAlb(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        try {
            final String limit = "100";
            final ListLoadBalancersResponse albResponse = albApi.listLoadBalancers(projectIdStr, StackitConstants.ALB_DEFAULT_REGION, limit, null);
            if (albResponse == null || albResponse.getLoadBalancers() == null) {
                return true;
            }

            for (final LoadBalancer lb : albResponse.getLoadBalancers()) {
                final NetworkResourceDto dto = NetworkResourceMapper.mapToDto(lb);
                if (validator.validate(dto).isEmpty()) {
                    final StackitEntity entity = NetworkResourceMapper.mapToEntity(dto);
                    entity.setProjectId(projectIdStr);
                    repository.persistOrUpdate(entity);
                    currentResourceIds.add(entity.getResourceId());
                } else {
                    log.warn("Invalid Network DTO: {}", dto.getLoadBalancerId());
                }
            }
            return true;
        } catch (final Exception e) {
            log.warn("Failed to scrape ALB resources for project {}: {}", projectIdStr, e.getMessage());
            return false;
        }
    }
}
