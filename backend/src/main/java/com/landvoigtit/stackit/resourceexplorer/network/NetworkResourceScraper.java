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

    @Inject
    com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig sdkConfig;

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
        final List<String> regions = sdkConfig != null ? sdkConfig.getRegions() : StackitConstants.DEFAULT_REGIONS;
        boolean allRegionsSucceeded = true;

        for (final String region : regions) {
            try {
                final String limit = "100";
                final ListLoadBalancersResponse albResponse = albApi.listLoadBalancers(projectIdStr, region, limit, null);
                if (albResponse == null || albResponse.getLoadBalancers() == null) {
                    continue;
                }

                for (final LoadBalancer lb : albResponse.getLoadBalancers()) {
                    final NetworkResourceDto dto = NetworkResourceMapper.mapToDto(lb);
                    if (dto.getRegion() == null || dto.getRegion().isBlank()) {
                        dto.setRegion(region);
                    }
                    if (validator.validate(dto).isEmpty()) {
                        final StackitEntity entity = NetworkResourceMapper.mapToEntity(dto);
                        entity.setProjectId(projectIdStr);
                        repository.persistOrUpdate(entity);
                        currentResourceIds.add(entity.getResourceId());
                    } else {
                        log.warn("Invalid Network DTO: {}", dto.getLoadBalancerId());
                    }
                }
            } catch (final Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("404") || msg.contains("403") || msg.contains("not_found")) {
                    log.debug("ALB not enabled or accessible for project {} in region {}: {}", projectIdStr, region, msg);
                } else {
                    log.warn("Failed to scrape ALB resources for project {} in region {}: {}", projectIdStr, region, e.getMessage());
                    allRegionsSucceeded = false;
                }
            }
        }
        return allRegionsSucceeded;
    }
}
