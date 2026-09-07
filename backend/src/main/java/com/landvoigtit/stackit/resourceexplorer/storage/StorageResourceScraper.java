package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v2api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v2api.model.Bucket;
import cloud.stackit.sdk.objectstorage.v2api.model.ListBucketsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import io.quarkus.scheduler.Scheduled;

import java.util.ArrayList;
import java.util.List;

import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;

@ApplicationScoped
@Slf4j
public class StorageResourceScraper {

    @Inject
    ObjectStorageApi objectStorageApi;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Inject
    StackitSdkConfig sdkConfig;

    @Scheduled(every = "${stackit.storage.schedule:off}")
    public void scrape() {
        log.info("Starting Storage resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("Storage resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectStorage(projectIdStr, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_STORAGE, projectIdStr, currentResourceIds);
                }
            }
            log.info("Storage resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape Storage resources", e);
        }
    }

    private boolean scrapeProjectStorage(final String projectIdStr, final List<String> currentResourceIds) {
        final List<String> regions = sdkConfig != null ? sdkConfig.getRegions() : StackitConstants.DEFAULT_REGIONS;
        boolean allRegionsSucceeded = true;

        for (final String region : regions) {
            try {
                final ListBucketsResponse bucketsResponse = objectStorageApi.listBuckets(projectIdStr, region);
                if (bucketsResponse == null || bucketsResponse.getBuckets() == null) {
                    continue;
                }

                for (final Bucket bucket : bucketsResponse.getBuckets()) {
                    final StorageResourceDto dto = StorageResourceMapper.mapToDto(bucket);
                    if (validator.validate(dto).isEmpty()) {
                        final StackitEntity entity = StorageResourceMapper.mapToEntity(dto);
                        entity.setProjectId(projectIdStr);
                        repository.persistOrUpdate(entity);
                        currentResourceIds.add(entity.getResourceId());
                    } else {
                        log.warn("Invalid Storage DTO: {}", dto.getBucketName());
                    }
                }
            } catch (final Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("404") || msg.contains("403") || msg.contains("not_found")) {
                    log.debug("Storage not enabled or accessible for project {} in region {}: {}", projectIdStr, region, msg);
                } else {
                    log.warn("Failed to scrape Storage resources for project {} in region {}: {}", projectIdStr, region, e.getMessage());
                    allRegionsSucceeded = false;
                }
            }
        }
        return allRegionsSucceeded;
    }
}
