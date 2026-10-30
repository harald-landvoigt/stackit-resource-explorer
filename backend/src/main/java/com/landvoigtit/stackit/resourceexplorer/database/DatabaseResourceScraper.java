package com.landvoigtit.stackit.resourceexplorer.database;

import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.access.AccessIssueRegistry;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled scraper for discovering and cataloging STACKIT Managed Database and messaging instances
 * across multiple database engines (PostgreSQL Flex, MongoDB Flex, OpenSearch, MariaDB, SQL Server Flex).
 */
@ApplicationScoped
@Slf4j
public class DatabaseResourceScraper {

    public static final List<String> REGIONAL_ENGINES = List.of("postgresql", "sqlserver", "mongodb");
    public static final List<String> PROJECT_ENGINES = List.of("opensearch", "mariadb");

    @Inject
    DatabaseApiClient databaseApiClient;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    AccessIssueRegistry accessIssueRegistry;

    @Inject
    StackitSdkConfig sdkConfig;

    @Inject
    Validator validator;

    @Scheduled(every = "${stackit.database.schedule:1h}")
    public void scrape() {
        log.info("Starting managed database resources scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("Database scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectDatabases(project, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_DATABASE, projectIdStr, currentResourceIds);
                }
            }
            log.info("Managed database resources scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape managed database resources", e);
        }
    }

    private static class ScrapeContext {
        boolean succeeded = true;
        boolean permissionDenied = false;
        String permissionDeniedMsg;
        Integer statusCode;
        String failedRegion;
    }

    private boolean scrapeProjectDatabases(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        log.info("Scraping managed database resources for project {} ({})", projectIdStr, project.getName());
        final ScrapeContext context = new ScrapeContext();
        final List<String> regions = getEffectiveRegions();

        for (final String engine : REGIONAL_ENGINES) {
            scrapeRegionalEngine(engine, projectIdStr, regions, currentResourceIds, context);
        }

        for (final String engine : PROJECT_ENGINES) {
            scrapeProjectEngine(engine, projectIdStr, currentResourceIds, context);
        }

        recordAccessResult(projectIdStr, project.getName(), context);
        return context.succeeded;
    }

    private void scrapeRegionalEngine(final String engine,
                                      final String projectIdStr,
                                      final List<String> regions,
                                      final List<String> currentResourceIds,
                                      final ScrapeContext context) {
        for (final String region : regions) {
            scrapeEngineInstances(engine, projectIdStr, region, currentResourceIds, context);
        }
    }

    private void scrapeProjectEngine(final String engine,
                                     final String projectIdStr,
                                     final List<String> currentResourceIds,
                                     final ScrapeContext context) {
        scrapeEngineInstances(engine, projectIdStr, StackitConstants.REGION_EU01, currentResourceIds, context);
    }

    private void scrapeEngineInstances(final String engine,
                                       final String projectIdStr,
                                       final String region,
                                       final List<String> currentResourceIds,
                                       final ScrapeContext context) {
        log.info("Scraping database type '{}' for project {} in region {}", engine, projectIdStr, region);
        try {
            final List<Map<String, Object>> instances = databaseApiClient.listInstances(engine, projectIdStr, region);
            if (instances != null) {
                if (!instances.isEmpty()) {
                    log.info("Discovered {} database instance(s) of type '{}' in project {} / region {}",
                            instances.size(), engine, projectIdStr, region);
                }
                for (final Map<String, Object> json : instances) {
                    processInstanceJson(json, engine, projectIdStr, region, currentResourceIds);
                }
            }
        } catch (final Exception e) {
            handleEngineError(engine, projectIdStr, region, e, context);
        }
    }

    private void processInstanceJson(final Map<String, Object> json,
                                    final String engine,
                                    final String projectIdStr,
                                    final String region,
                                    final List<String> currentResourceIds) {
        if (json == null) {
            return;
        }
        final Map<String, Object> effectiveJson = enrichWithInstanceDetails(json, engine, projectIdStr, region);
        final StackitEntity entity = DatabaseResourceMapper.mapToEntity(effectiveJson, engine, projectIdStr, region);
        if (entity == null || entity.getResourceId() == null) {
            return;
        }

        if (validator != null && !validator.validate(entity).isEmpty()) {
            log.warn("Invalid Database Entity for resourceId: {}", entity.getResourceId());
            return;
        }

        repository.persistOrUpdate(entity);
        currentResourceIds.add(entity.getResourceId());
    }

    private Map<String, Object> enrichWithInstanceDetails(final Map<String, Object> summaryJson,
                                                          final String engine,
                                                          final String projectIdStr,
                                                          final String region) {
        final Object idObj = summaryJson.get("id") != null
                ? summaryJson.get("id")
                : summaryJson.get("instanceId");
        if (idObj == null || idObj.toString().isBlank()) {
            return summaryJson;
        }
        final String instanceId = idObj.toString();
        try {
            final Map<String, Object> detailJson = databaseApiClient.getInstance(engine, projectIdStr, region, instanceId);
            if (detailJson == null || detailJson.isEmpty()) {
                return summaryJson;
            }
            final Map<String, Object> merged = new LinkedHashMap<>(summaryJson);
            detailJson.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
            return merged;
        } catch (final Exception e) {
            log.warn("Failed to fetch details for database '{}' instance {} in project {} / region {}: {}",
                    engine, instanceId, projectIdStr, region, StackitConstants.cleanErrorMessage(e));
            return summaryJson;
        }
    }

    private void handleEngineError(final String engine,
                                   final String projectIdStr,
                                   final String region,
                                   final Exception e,
                                   final ScrapeContext context) {
        final String rawMsg = e.getMessage() != null ? e.getMessage() : "";
        final String cleanMsg = StackitConstants.cleanErrorMessage(e);

        if (StackitConstants.isServiceDisabled(e)) {
            log.info("Database service '{}' disabled in project {} / region {}: {}", engine, projectIdStr, region, cleanMsg);
        } else if (StackitConstants.isPermissionIssue(e)) {
            log.warn("Permission denied for database service '{}' in project {} / region {}: {}", engine, projectIdStr, region, cleanMsg);
            context.succeeded = false;
            context.permissionDenied = true;
            context.permissionDeniedMsg = cleanMsg;
            context.failedRegion = region;
            context.statusCode = extractStatusCode(rawMsg, 403);
        } else {
            log.error("Error scraping database service '{}' in project {} / region {}: {}", engine, projectIdStr, region, cleanMsg, e);
        }
    }

    private void recordAccessResult(final String projectIdStr, final String projectName, final ScrapeContext context) {
        if (accessIssueRegistry == null) {
            return;
        }
        if (context.permissionDenied) {
            accessIssueRegistry.recordFailure(
                    projectIdStr,
                    projectName,
                    StackitConstants.RESOURCE_TYPE_DATABASE,
                    context.failedRegion,
                    context.statusCode,
                    context.permissionDeniedMsg
            );
        } else {
            accessIssueRegistry.recordSuccess(
                    projectIdStr,
                    projectName,
                    StackitConstants.RESOURCE_TYPE_DATABASE,
                    null
            );
        }
    }

    private List<String> getEffectiveRegions() {
        if (sdkConfig != null && sdkConfig.getRegions() != null && !sdkConfig.getRegions().isEmpty()) {
            return sdkConfig.getRegions();
        }
        return StackitConstants.DEFAULT_REGIONS;
    }

    private Integer extractStatusCode(final String msg, final int defaultCode) {
        if (msg != null && msg.contains("401")) {
            return 401;
        }
        if (msg != null && msg.contains("403")) {
            return 403;
        }
        return defaultCode;
    }
}
