package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.iaas.v1api.api.IaasApi;
import cloud.stackit.sdk.iaas.v1api.model.Network;
import cloud.stackit.sdk.iaas.v1api.model.NetworkListResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class NetworkVpcResourceScraper {

    @Inject
    IaasApi iaasApi;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Scheduled(every = "${stackit.network-vpc.schedule:1h}")
    public void scrape() {
        log.info("Starting Network VPC resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("Network VPC resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectNetworks(project, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_NETWORK_VPC, projectIdStr, currentResourceIds);
                }
            }
            log.info("Network VPC resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape Network VPC resources", e);
        }
    }

    private boolean scrapeProjectNetworks(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        try {
            final NetworkListResponse response = iaasApi.listNetworks(project.getProjectId(), null);
            if (response == null || response.getItems() == null) {
                return true;
            }

            for (final Network network : response.getItems()) {
                final NetworkVpcResourceDto dto = NetworkVpcResourceMapper.mapToDto(network);
                if (validator.validate(dto).isEmpty()) {
                    final StackitEntity entity = NetworkVpcResourceMapper.mapToEntity(dto);
                    entity.setProjectId(projectIdStr);
                    repository.persistOrUpdate(entity);
                    currentResourceIds.add(entity.getResourceId());
                } else {
                    log.warn("Invalid Network VPC DTO: {}", dto.getNetworkId());
                }
            }
            return true;
        } catch (final Exception e) {
            log.warn("Failed to scrape Network VPC resources for project {}: {}", projectIdStr, e.getMessage());
            return false;
        }
    }
}
