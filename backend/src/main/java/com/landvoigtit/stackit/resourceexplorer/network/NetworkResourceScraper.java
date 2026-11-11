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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Inject
    com.landvoigtit.stackit.resourceexplorer.access.AccessIssueRegistry accessIssueRegistry;

    /** Maximum page size accepted by the ALB API. */
    static final String ALB_PAGE_SIZE = "1000";

    /** Safety guard against an API that keeps returning a next page id forever. */
    static final int MAX_ALB_PAGES = 10_000;

    private final AtomicBoolean isScraping = new AtomicBoolean(false);

    @Scheduled(every = "${stackit.network.schedule:1h}", delayed = "${stackit.network.delay:1m30s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void scrape() {
        if (!isScraping.compareAndSet(false, true)) {
            log.warn("Network scrape is already running, skipping overlapping execution.");
            return;
        }
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
        } finally {
            isScraping.set(false);
        }
    }

    private boolean scrapeProjectAlb(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        final String projectName = project.getName();
        final List<String> regions = sdkConfig != null ? sdkConfig.getRegions() : StackitConstants.DEFAULT_REGIONS;
        boolean allRegionsSucceeded = true;
        boolean permissionDenied = false;
        String permissionDeniedMsg = null;
        String permissionDeniedRegion = null;

        for (final String region : regions) {
            try {
                for (final LoadBalancer lb : listAllLoadBalancers(projectIdStr, region)) {
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
                if (StackitConstants.isServiceDisabled(e)) {
                    log.debug("ALB not enabled for project {} in region {}: {}", projectIdStr, region, msg);
                } else if (StackitConstants.isPermissionIssue(msg)) {
                    log.warn("Permission denied accessing ALB resources for project {} in region {}: {}", projectIdStr, region, msg);
                    permissionDenied = true;
                    permissionDeniedMsg = msg;
                    permissionDeniedRegion = region;
                    allRegionsSucceeded = false;
                } else {
                    log.warn("Failed to scrape ALB resources for project {} in region {}: {}", projectIdStr, region, e.getMessage());
                    allRegionsSucceeded = false;
                }
            }
        }

        if (accessIssueRegistry != null) {
            if (permissionDenied) {
                accessIssueRegistry.recordFailure(projectIdStr, projectName, StackitConstants.RESOURCE_TYPE_NETWORK, permissionDeniedRegion, 403, permissionDeniedMsg);
            } else if (allRegionsSucceeded) {
                accessIssueRegistry.recordSuccess(projectIdStr, projectName, StackitConstants.RESOURCE_TYPE_NETWORK, null);
            }
        }

        return allRegionsSucceeded;
    }

    /**
     * Fetches all Application Load Balancers of a project in a region by following
     * {@code nextPageId} until the API reports no further pages.
     */
    List<LoadBalancer> listAllLoadBalancers(final String projectIdStr, final String region) throws Exception {
        final List<LoadBalancer> all = new ArrayList<>();
        final Set<String> seenPageIds = new HashSet<>();
        String pageId = null;
        int pages = 0;
        do {
            final ListLoadBalancersResponse response = albApi.listLoadBalancers(projectIdStr, region, ALB_PAGE_SIZE, pageId);
            if (response == null) {
                break;
            }
            if (response.getLoadBalancers() != null) {
                all.addAll(response.getLoadBalancers());
            }
            final String next = response.getNextPageId();
            if (next == null || next.isBlank() || !seenPageIds.add(next)) {
                break;
            }
            pageId = next;
        } while (++pages < MAX_ALB_PAGES);
        return all;
    }
}
