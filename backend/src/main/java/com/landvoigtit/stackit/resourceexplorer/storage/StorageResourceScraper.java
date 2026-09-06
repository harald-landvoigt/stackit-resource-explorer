package com.landvoigtit.stackit.resourceexplorer.storage;

import cloud.stackit.sdk.objectstorage.v1api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v1api.model.Bucket;
import cloud.stackit.sdk.objectstorage.v1api.model.ListBucketsResponse;
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

    @Scheduled(every = "${stackit.storage.schedule:off}")
    public void scrape() {
        log.info("Starting Storage resource scrape...");
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
        try {
            final ListBucketsResponse bucketsResponse = objectStorageApi.listBuckets(projectIdStr);
            if (bucketsResponse == null || bucketsResponse.getBuckets() == null) {
                return true;
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
            return true;
        } catch (final Exception e) {
            log.warn("Failed to scrape Storage resources for project {}: {}", projectIdStr, e.getMessage());
            return false;
        }
    }
}
