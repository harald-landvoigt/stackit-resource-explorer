package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.core.exception.ApiException;
import cloud.stackit.sdk.iaas.v2api.api.IaasApi;
import cloud.stackit.sdk.iaas.v2api.model.PublicIp;
import cloud.stackit.sdk.iaas.v2api.model.PublicIpListResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class PublicIpResourceScraper {

    @Inject
    IaasApi iaasApi;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Inject
    StackitSdkConfig sdkConfig;

    @Inject
    AccessIssueRegistry accessIssueRegistry;

    @Scheduled(every = "${stackit.publicips.schedule:1h}")
    public void scrape() {
        log.info("Starting Public IPs resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("Public IPs resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectPublicIps(project, currentResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_PUBLIC_IP, projectIdStr, currentResourceIds);
                }
            }
            log.info("Public IPs resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape Public IPs resources", e);
        }
    }

    private record ServerRef(String serverId, String serverName) {}

    private static class ScrapeContext {
        boolean allRegionsSucceeded = true;
        boolean permissionDenied = false;
        String permissionDeniedMsg;
        String permissionDeniedRegion;
    }

    private boolean scrapeProjectPublicIps(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        final List<String> regions = resolveRegions();
        final Map<String, ServerRef> ipToServerMap = buildIpToServerMap(projectIdStr);
        final ScrapeContext context = new ScrapeContext();

        for (final String region : regions) {
            scrapeRegionPublicIps(project, region, ipToServerMap, currentResourceIds, context);
        }

        recordAccessResult(projectIdStr, project.getName(), context);
        return context.allRegionsSucceeded;
    }

    private List<String> resolveRegions() {
        if (sdkConfig != null && sdkConfig.getRegions() != null && !sdkConfig.getRegions().isEmpty()) {
            return sdkConfig.getRegions();
        }
        return StackitConstants.DEFAULT_REGIONS;
    }

    private Map<String, ServerRef> buildIpToServerMap(final String projectIdStr) {
        final Map<String, ServerRef> ipToServerMap = new HashMap<>();
        try {
            final List<StackitEntity> servers = repository.list(
                    "type = ?1 and projectId = ?2 and deletedAt is null",
                    StackitConstants.RESOURCE_TYPE_COMPUTE,
                    projectIdStr
            );
            if (servers != null) {
                for (final StackitEntity server : servers) {
                    mapServerPublicIps(server, ipToServerMap);
                }
            }
        } catch (final Exception e) {
            log.warn("Could not query compute instances for public IP cross-referencing in project {}: {}", projectIdStr, e.getMessage());
        }
        return ipToServerMap;
    }

    private void mapServerPublicIps(final StackitEntity server, final Map<String, ServerRef> ipToServerMap) {
        if (server.getData() == null) {
            return;
        }
        final Object publicIpsObj = server.getData().get("publicIps");
        if (!(publicIpsObj instanceof Iterable<?> ipList)) {
            return;
        }
        final String srvId = server.getResourceId() != null
                ? server.getResourceId()
                : (server.getId() != null ? server.getId().toString() : null);
        final String srvName = server.getName();

        for (final Object ip : ipList) {
            if (ip != null && !ip.toString().isBlank()) {
                ipToServerMap.put(ip.toString().trim(), new ServerRef(srvId, srvName));
            }
        }
    }

    private void scrapeRegionPublicIps(
            final Project project,
            final String region,
            final Map<String, ServerRef> ipToServerMap,
            final List<String> currentResourceIds,
            final ScrapeContext context) {
        final String projectIdStr = project.getProjectId().toString();
        try {
            final PublicIpListResponse response = iaasApi.listPublicIPs(project.getProjectId(), region, null);
            if (response == null || response.getItems() == null) {
                return;
            }
            for (final PublicIp publicIp : response.getItems()) {
                processPublicIp(publicIp, region, projectIdStr, ipToServerMap, currentResourceIds);
            }
        } catch (final Exception e) {
            handleRegionError(projectIdStr, region, e, context);
        }
    }

    private void processPublicIp(
            final PublicIp publicIp,
            final String region,
            final String projectIdStr,
            final Map<String, ServerRef> ipToServerMap,
            final List<String> currentResourceIds) {
        final PublicIpResourceDto dto = PublicIpResourceMapper.mapToDto(publicIp);
        if (dto.getRegion() == null || dto.getRegion().isBlank()) {
            dto.setRegion(region);
        }

        attachServerRefIfMatching(dto, ipToServerMap);

        if (!validator.validate(dto).isEmpty()) {
            log.warn("Invalid Public IP DTO: {}", dto.getPublicIpId());
            return;
        }

        final StackitEntity entity = PublicIpResourceMapper.mapToEntity(dto);
        entity.setProjectId(projectIdStr);
        repository.persistOrUpdate(entity);
        currentResourceIds.add(entity.getResourceId());
    }

    private void attachServerRefIfMatching(
            final PublicIpResourceDto dto,
            final Map<String, ServerRef> ipToServerMap) {
        if (dto.getIp() == null) {
            return;
        }
        final String trimmedIp = dto.getIp().trim();
        final ServerRef srv = ipToServerMap.get(trimmedIp);
        if (srv != null) {
            dto.setServerId(srv.serverId());
            dto.setServerName(srv.serverName());
            dto.setAttached(true);
            dto.setStatus("ATTACHED");
        }
    }

    private void handleRegionError(
            final String projectIdStr,
            final String region,
            final Exception e,
            final ScrapeContext context) {
        final String cleanedMsg = StackitConstants.cleanErrorMessage(e);
        if (StackitConstants.isServiceDisabled(e) || isNotFoundOrNotEnabled(e, e.getMessage())) {
            log.debug("Public IPs not enabled or not found for project {} in region {}: {}", projectIdStr, region, cleanedMsg);
        } else if (StackitConstants.isPermissionIssue(e)) {
            log.warn("Permission denied accessing Public IPs for project {} in region {}: {}", projectIdStr, region, cleanedMsg);
            context.permissionDenied = true;
            context.permissionDeniedMsg = cleanedMsg;
            context.permissionDeniedRegion = region;
            context.allRegionsSucceeded = false;
        } else {
            log.warn("Failed to scrape Public IPs for project {} in region {}: {}", projectIdStr, region, cleanedMsg);
            context.allRegionsSucceeded = false;
        }
    }

    private boolean isNotFoundOrNotEnabled(final Exception e, final String msg) {
        if (e instanceof ApiException apiEx && apiEx.getCode() == 404) {
            return true;
        }
        return msg.contains("404") || msg.contains("not_found");
    }

    private void recordAccessResult(
            final String projectIdStr,
            final String projectName,
            final ScrapeContext context) {
        if (accessIssueRegistry == null) {
            return;
        }
        if (context.permissionDenied) {
            accessIssueRegistry.recordFailure(
                    projectIdStr,
                    projectName,
                    StackitConstants.RESOURCE_TYPE_PUBLIC_IP,
                    context.permissionDeniedRegion,
                    403,
                    context.permissionDeniedMsg
            );
        } else if (context.allRegionsSucceeded) {
            accessIssueRegistry.recordSuccess(
                    projectIdStr,
                    projectName,
                    StackitConstants.RESOURCE_TYPE_PUBLIC_IP,
                    null
            );
        }
    }
}
