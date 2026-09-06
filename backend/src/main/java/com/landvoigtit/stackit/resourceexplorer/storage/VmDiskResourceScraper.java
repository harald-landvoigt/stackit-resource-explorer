package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.iaas.v1api.api.IaasApi;
import cloud.stackit.sdk.iaas.v1api.model.Volume;
import cloud.stackit.sdk.iaas.v1api.model.VolumeListResponse;
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
public class VmDiskResourceScraper {

    @Inject
    IaasApi iaasApi;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Scheduled(every = "${stackit.vmdisks.schedule:1h}")
    public void scrape() {
        log.info("Starting VM Disks resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("VM Disks resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectVolumes(project, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_VMDISKS, projectIdStr, currentResourceIds);
                }
            }
            log.info("VM Disks resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape VM Disks resources", e);
        }
    }

    private boolean scrapeProjectVolumes(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        try {
            final VolumeListResponse response = iaasApi.listVolumes(project.getProjectId(), null);
            if (response == null || response.getItems() == null) {
                return true;
            }

            for (final Volume volume : response.getItems()) {
                final VmDiskResourceDto dto = VmDiskResourceMapper.mapToDto(volume);
                if (validator.validate(dto).isEmpty()) {
                    final StackitEntity entity = VmDiskResourceMapper.mapToEntity(dto);
                    entity.setProjectId(projectIdStr);
                    repository.persistOrUpdate(entity);
                    currentResourceIds.add(entity.getResourceId());
                } else {
                    log.warn("Invalid VM Disk DTO: {}", dto.getVolumeId());
                }
            }
            return true;
        } catch (final Exception e) {
            log.warn("Failed to scrape VM Disks for project {}: {}", projectIdStr, e.getMessage());
            return false;
        }
    }
}
