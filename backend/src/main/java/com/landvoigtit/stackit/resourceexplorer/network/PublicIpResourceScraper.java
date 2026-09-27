package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.iaas.v1api.api.IaasApi;
import cloud.stackit.sdk.iaas.v1api.model.PublicIp;
import cloud.stackit.sdk.iaas.v1api.model.PublicIpListResponse;
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

    private boolean scrapeProjectPublicIps(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        final String projectName = project.getName();
        final List<String> regions = sdkConfig != null ? sdkConfig.getRegions() : StackitConstants.DEFAULT_REGIONS;
        boolean allRegionsSucceeded = true;
        boolean permissionDenied = false;
        String permissionDeniedMsg = null;
        String permissionDeniedRegion = null;

        // Query active compute instances to map public IP -> server
        final Map<String, ServerRef> ipToServerMap = new HashMap<>();
        try {
            final List<StackitEntity> servers = repository.list(
                    "type = ?1 and projectId = ?2 and deletedAt is null",
                    StackitConstants.RESOURCE_TYPE_COMPUTE,
                    projectIdStr
            );
            if (servers != null) {
                for (final StackitEntity server : servers) {
                    final String srvId = server.getResourceId() != null
                            ? server.getResourceId()
                            : (server.getId() != null ? server.getId().toString() : null);
                    final String srvName = server.getName();

                    if (server.getData() != null) {
                        final Object publicIpsObj = server.getData().get("publicIps");
                        if (publicIpsObj instanceof Iterable<?> ipList) {
                            for (final Object ip : ipList) {
                                if (ip != null && !ip.toString().isBlank()) {
                                    ipToServerMap.put(ip.toString().trim(), new ServerRef(srvId, srvName));
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            log.warn("Could not query compute instances for public IP cross-referencing in project {}: {}", projectIdStr, e.getMessage());
        }

        for (final String region : regions) {
            try {
                final IaasApi regionalApi = sdkConfig != null ? sdkConfig.iaasApiForRegion(region, iaasApi) : iaasApi;
                final PublicIpListResponse response = regionalApi.listPublicIPs(project.getProjectId(), null);
                if (response == null || response.getItems() == null) {
                    continue;
                }

                for (final PublicIp publicIp : response.getItems()) {
                    final PublicIpResourceDto dto = PublicIpResourceMapper.mapToDto(publicIp);
                    if (dto.getRegion() == null || dto.getRegion().isBlank()) {
                        dto.setRegion(region);
                    }

                    if (dto.getIp() != null && ipToServerMap.containsKey(dto.getIp().trim())) {
                        final ServerRef srv = ipToServerMap.get(dto.getIp().trim());
                        dto.setServerId(srv.serverId());
                        dto.setServerName(srv.serverName());
                        dto.setAttached(true);
                        dto.setStatus("ATTACHED");
                    }

                    if (validator.validate(dto).isEmpty()) {
                        final StackitEntity entity = PublicIpResourceMapper.mapToEntity(dto);
                        entity.setProjectId(projectIdStr);
                        repository.persistOrUpdate(entity);
                        currentResourceIds.add(entity.getResourceId());
                    } else {
                        log.warn("Invalid Public IP DTO: {}", dto.getPublicIpId());
                    }
                }
            } catch (final Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "";
                if (StackitConstants.isPermissionIssue(msg)) {
                    log.warn("Permission denied accessing Public IPs for project {} in region {}: {}", projectIdStr, region, msg);
                    permissionDenied = true;
                    permissionDeniedMsg = msg;
                    permissionDeniedRegion = region;
                    allRegionsSucceeded = false;
                } else if (msg.contains("404") || msg.contains("not_found")) {
                    log.warn("Public IPs not enabled for project {} in region {}: {}", projectIdStr, region, msg);
                } else {
                    log.warn("Failed to scrape Public IPs for project {} in region {}: {}", projectIdStr, region, e.getMessage());
                    allRegionsSucceeded = false;
                }
            }
        }

        if (accessIssueRegistry != null) {
            if (permissionDenied) {
                accessIssueRegistry.recordFailure(projectIdStr, projectName, StackitConstants.RESOURCE_TYPE_PUBLIC_IP, permissionDeniedRegion, 403, permissionDeniedMsg);
            } else if (allRegionsSucceeded) {
                accessIssueRegistry.recordSuccess(projectIdStr, projectName, StackitConstants.RESOURCE_TYPE_PUBLIC_IP, null);
            }
        }

        return allRegionsSucceeded;
    }
}
