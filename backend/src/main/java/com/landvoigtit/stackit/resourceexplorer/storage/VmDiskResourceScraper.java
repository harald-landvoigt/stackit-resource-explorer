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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Inject
    com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig sdkConfig;

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

    private record ServerAttachment(String serverId, String serverName, boolean isBootVolume) {}

    private boolean scrapeProjectVolumes(final Project project, final List<String> currentResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        final List<String> regions = sdkConfig != null ? sdkConfig.getRegions() : StackitConstants.DEFAULT_REGIONS;
        boolean allRegionsSucceeded = true;

        // Build server attachment lookup from active compute instances in this project
        final Map<String, String> serverNameMap = new HashMap<>();
        final Map<String, ServerAttachment> volumeAttachmentMap = new HashMap<>();
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
                    if (srvId != null) {
                        serverNameMap.put(srvId.toLowerCase(), srvName);
                        if (server.getId() != null) {
                            serverNameMap.put(server.getId().toString().toLowerCase(), srvName);
                        }
                    }

                    if (server.getData() != null) {
                        final Object bootVol = server.getData().get("bootVolumeId");
                        if (bootVol != null && !bootVol.toString().isBlank()) {
                            final String bootVolId = bootVol.toString().toLowerCase();
                            volumeAttachmentMap.put(bootVolId, new ServerAttachment(srvId, srvName, true));
                        }

                        final Object attachedVols = server.getData().get("attachedVolumes");
                        if (attachedVols instanceof Iterable<?> volList) {
                            for (final Object v : volList) {
                                if (v != null && !v.toString().isBlank()) {
                                    final String attachedVolId = v.toString().toLowerCase();
                                    volumeAttachmentMap.put(attachedVolId, new ServerAttachment(srvId, srvName, false));
                                }
                            }
                        }
                    }
                }
            }
        } catch (final Exception e) {
            log.warn("Could not query compute servers for disk attachment cross-referencing in project {}: {}", projectIdStr, e.getMessage());
        }

        for (final String region : regions) {
            try {
                final IaasApi regionalApi = sdkConfig != null ? sdkConfig.iaasApiForRegion(region, iaasApi) : iaasApi;
                final VolumeListResponse response = regionalApi.listVolumes(project.getProjectId(), null);
                if (response == null || response.getItems() == null) {
                    continue;
                }

                for (final Volume volume : response.getItems()) {
                    final VmDiskResourceDto dto = VmDiskResourceMapper.mapToDto(volume);
                    final String volIdStr = dto.getVolumeId() != null ? dto.getVolumeId().toLowerCase() : null;
                    final ServerAttachment crossRef = volIdStr != null ? volumeAttachmentMap.get(volIdStr) : null;

                    if (crossRef != null) {
                        dto.setAttached(true);
                        if (crossRef.serverId != null) {
                            dto.setServerId(crossRef.serverId);
                        }
                        if (crossRef.serverName != null) {
                            dto.setServerName(crossRef.serverName);
                        }
                        dto.setBootVolume(crossRef.isBootVolume);
                    } else if (volume.getServerId() != null) {
                        final String srvId = volume.getServerId().toString();
                        dto.setAttached(true);
                        dto.setServerId(srvId);
                        dto.setServerName(serverNameMap.get(srvId.toLowerCase()));
                        if (dto.getBootVolume() == null) {
                            dto.setBootVolume(Boolean.TRUE.equals(volume.getBootable()));
                        }
                    } else {
                        dto.setAttached(false);
                        dto.setServerId(null);
                        dto.setServerName(null);
                        if (dto.getBootVolume() == null) {
                            dto.setBootVolume(Boolean.TRUE.equals(volume.getBootable()));
                        }
                    }

                    if (validator.validate(dto).isEmpty()) {
                        final StackitEntity entity = VmDiskResourceMapper.mapToEntity(dto);
                        entity.setProjectId(projectIdStr);
                        repository.persistOrUpdate(entity);
                        currentResourceIds.add(entity.getResourceId());
                    } else {
                        log.warn("Invalid VM Disk DTO: {}", dto.getVolumeId());
                    }
                }
            } catch (final Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("404") || msg.contains("403") || msg.contains("not_found")) {
                    log.debug("VM Disks not enabled or accessible for project {} in region {}: {}", projectIdStr, region, msg);
                } else {
                    log.warn("Failed to scrape VM Disks for project {} in region {}: {}", projectIdStr, region, e.getMessage());
                    allRegionsSucceeded = false;
                }
            }
        }
        return allRegionsSucceeded;
    }
}
