package com.landvoigtit.stackit.resourceexplorer.dns;

import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.StackitProjectDiscoveryService;
import com.landvoigtit.stackit.resourceexplorer.access.AccessIssueRegistry;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
public class DnsResourceScraper {

    @Inject
    DnsApiClient dnsApiClient;

    @Inject
    StackitProjectDiscoveryService projectDiscoveryService;

    @Inject
    StackitResourceRepository repository;

    @Inject
    Validator validator;

    @Inject
    AccessIssueRegistry accessIssueRegistry;

    @Scheduled(every = "${stackit.dns.schedule:1h}")
    public void scrape() {
        log.info("Starting DNS resource scrape...");
        try {
            final List<Project> projects = projectDiscoveryService.discoverProjects();
            if (projects == null || projects.isEmpty()) {
                log.warn("DNS resource scrape skipped: No accessible projects found.");
                return;
            }

            for (final Project project : projects) {
                if (project.getProjectId() == null) {
                    continue;
                }
                final String projectIdStr = project.getProjectId().toString();
                final List<String> currentZoneResourceIds = new ArrayList<>();
                final List<String> currentRecordSetResourceIds = new ArrayList<>();
                final boolean success = scrapeProjectDnsZones(project, currentZoneResourceIds, currentRecordSetResourceIds);

                if (success) {
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_DNS_ZONE, projectIdStr, currentZoneResourceIds);
                    repository.softDeleteMissing(StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET, projectIdStr, currentRecordSetResourceIds);
                }
            }
            log.info("DNS resource scrape completed successfully.");
        } catch (final Exception e) {
            log.error("Failed to scrape DNS resources", e);
        }
    }

    private record ServerRef(String serverId, String serverName) {}

    private static class ScrapeContext {
        boolean succeeded = true;
        boolean permissionDenied = false;
        String permissionDeniedMsg;
        Integer statusCode;
    }

    private boolean scrapeProjectDnsZones(
            final Project project,
            final List<String> currentZoneResourceIds,
            final List<String> currentRecordSetResourceIds) {
        final String projectIdStr = project.getProjectId().toString();
        final Map<String, String> ipToPublicIpIdMap = buildIpToPublicIpMap(projectIdStr);
        final Map<String, ServerRef> ipToServerMap = buildIpToServerMap(projectIdStr);
        final ScrapeContext context = new ScrapeContext();

        try {
            final List<Map<String, Object>> zones = dnsApiClient.listZones(projectIdStr);
            if (zones != null) {
                for (final Map<String, Object> zoneJson : zones) {
                    processZone(zoneJson, projectIdStr, ipToPublicIpIdMap, ipToServerMap, currentZoneResourceIds, currentRecordSetResourceIds);
                }
            }
        } catch (final Exception e) {
            handleScrapeError(projectIdStr, e, context);
        }

        recordAccessResult(projectIdStr, project.getName(), context);
        return context.succeeded;
    }

    private void processZone(
            final Map<String, Object> zoneJson,
            final String projectIdStr,
            final Map<String, String> ipToPublicIpIdMap,
            final Map<String, ServerRef> ipToServerMap,
            final List<String> currentZoneResourceIds,
            final List<String> currentRecordSetResourceIds) {
        final String zoneId = zoneJson.get("id") != null ? zoneJson.get("id").toString() : null;
        final List<Map<String, Object>> recordSetsJson = fetchRecordSetsSafely(projectIdStr, zoneId);

        final DnsZoneResourceDto dto = DnsZoneResourceMapper.mapToDto(zoneJson, recordSetsJson);
        if (dto == null) {
            return;
        }

        crossReferenceRecordSets(dto, ipToPublicIpIdMap, ipToServerMap);

        if (validator != null && !validator.validate(dto).isEmpty()) {
            log.warn("Invalid DNS Zone DTO for zoneId: {}", dto.getZoneId());
            return;
        }

        final StackitEntity entity = DnsZoneResourceMapper.mapToEntity(dto);
        if (entity != null) {
            entity.setProjectId(projectIdStr);
            repository.persistOrUpdate(entity);
            currentZoneResourceIds.add(entity.getResourceId());
        }

        persistRecordSetEntities(dto, projectIdStr, currentRecordSetResourceIds);
    }

    private void persistRecordSetEntities(
            final DnsZoneResourceDto zoneDto,
            final String projectIdStr,
            final List<String> currentRecordSetResourceIds) {
        if (zoneDto == null || zoneDto.getRecordSets() == null) {
            return;
        }
        for (final DnsRecordSetDto rs : zoneDto.getRecordSets()) {
            final StackitEntity rsEntity = DnsZoneResourceMapper.mapRecordSetToEntity(
                    rs, projectIdStr, zoneDto.getZoneId(), zoneDto.getName()
            );
            if (rsEntity != null) {
                repository.persistOrUpdate(rsEntity);
                currentRecordSetResourceIds.add(rsEntity.getResourceId());
            }
        }
    }

    private List<Map<String, Object>> fetchRecordSetsSafely(final String projectIdStr, final String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return dnsApiClient.listRecordSets(projectIdStr, zoneId);
        } catch (final Exception e) {
            log.warn("Failed to fetch record sets for zone {} in project {}: {}", zoneId, projectIdStr, e.getMessage());
            return Collections.emptyList();
        }
    }

    private void crossReferenceRecordSets(
            final DnsZoneResourceDto dto,
            final Map<String, String> ipToPublicIpIdMap,
            final Map<String, ServerRef> ipToServerMap) {
        if (dto == null || dto.getRecordSets() == null) {
            return;
        }
        for (final DnsRecordSetDto rs : dto.getRecordSets()) {
            matchRecordSet(rs, ipToPublicIpIdMap, ipToServerMap);
        }
    }

    private void matchRecordSet(
            final DnsRecordSetDto rs,
            final Map<String, String> ipToPublicIpIdMap,
            final Map<String, ServerRef> ipToServerMap) {
        if (rs.getRecords() == null) {
            return;
        }
        for (final String rawRecord : rs.getRecords()) {
            if (rawRecord == null || rawRecord.isBlank()) {
                continue;
            }
            final String normalized = cleanRecordContent(rawRecord);
            if (ipToPublicIpIdMap.containsKey(normalized)) {
                rs.setMatchedPublicIpId(ipToPublicIpIdMap.get(normalized));
            }
            if (ipToServerMap.containsKey(normalized)) {
                final ServerRef srv = ipToServerMap.get(normalized);
                rs.setMatchedServerId(srv.serverId());
                rs.setMatchedServerName(srv.serverName());
            }
        }
    }

    private String cleanRecordContent(final String record) {
        String trimmed = record.trim();
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private Map<String, String> buildIpToPublicIpMap(final String projectIdStr) {
        final Map<String, String> map = new HashMap<>();
        try {
            final List<StackitEntity> publicIps = repository.list(
                    "type = ?1 and projectId = ?2 and deletedAt is null",
                    StackitConstants.RESOURCE_TYPE_PUBLIC_IP,
                    projectIdStr
            );
            if (publicIps != null) {
                for (final StackitEntity entity : publicIps) {
                    mapPublicIpEntity(entity, map);
                }
            }
        } catch (final Exception e) {
            log.warn("Could not query Public IPs for cross-referencing in project {}: {}", projectIdStr, e.getMessage());
        }
        return map;
    }

    private void mapPublicIpEntity(final StackitEntity entity, final Map<String, String> map) {
        if (entity.getData() != null && entity.getData().get("ip") != null) {
            final String ip = entity.getData().get("ip").toString().trim();
            final String resId = entity.getResourceId() != null
                    ? entity.getResourceId()
                    : (entity.getId() != null ? entity.getId().toString() : null);
            if (!ip.isBlank() && resId != null) {
                map.put(ip, resId);
            }
        }
    }

    private Map<String, ServerRef> buildIpToServerMap(final String projectIdStr) {
        final Map<String, ServerRef> map = new HashMap<>();
        try {
            final List<StackitEntity> servers = repository.list(
                    "type = ?1 and projectId = ?2 and deletedAt is null",
                    StackitConstants.RESOURCE_TYPE_COMPUTE,
                    projectIdStr
            );
            if (servers != null) {
                for (final StackitEntity server : servers) {
                    mapServerIps(server, map);
                }
            }
        } catch (final Exception e) {
            log.warn("Could not query compute instances for DNS cross-referencing in project {}: {}", projectIdStr, e.getMessage());
        }
        return map;
    }

    private void mapServerIps(final StackitEntity server, final Map<String, ServerRef> map) {
        if (server.getData() == null) {
            return;
        }
        final String srvId = server.getResourceId() != null
                ? server.getResourceId()
                : (server.getId() != null ? server.getId().toString() : null);
        final String srvName = server.getName();
        final ServerRef ref = new ServerRef(srvId, srvName);

        for (final String key : List.of("publicIps", "privateIps", "ipAddresses")) {
            final Object ipsObj = server.getData().get(key);
            if (ipsObj instanceof Iterable<?> ipList) {
                for (final Object ip : ipList) {
                    if (ip != null && !ip.toString().isBlank()) {
                        map.put(ip.toString().trim(), ref);
                    }
                }
            }
        }
    }

    private void handleScrapeError(
            final String projectIdStr,
            final Exception e,
            final ScrapeContext context) {
        final String cleanedMsg = StackitConstants.cleanErrorMessage(e);
        if (StackitConstants.isServiceDisabled(e) || isNotFoundOrNotEnabled(e, cleanedMsg)) {
            log.debug("DNS service not enabled or not found for project {}: {}", projectIdStr, cleanedMsg);
            context.succeeded = true;
        } else if (StackitConstants.isPermissionIssue(cleanedMsg)) {
            log.warn("Permission denied accessing DNS for project {}: {}", projectIdStr, cleanedMsg);
            context.succeeded = false;
            context.permissionDenied = true;
            context.permissionDeniedMsg = cleanedMsg;
            context.statusCode = 403;
        } else {
            log.warn("Failed to scrape DNS zones for project {}: {}", projectIdStr, cleanedMsg);
            context.succeeded = false;
            context.permissionDeniedMsg = cleanedMsg;
            context.statusCode = 500;
        }
    }

    private boolean isNotFoundOrNotEnabled(final Exception e, final String msg) {
        final String lower = msg.toLowerCase();
        return lower.contains("404") || lower.contains("not_found") || lower.contains("not enabled");
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
                    StackitConstants.RESOURCE_TYPE_DNS_ZONE,
                    null,
                    context.statusCode != null ? context.statusCode : 403,
                    context.permissionDeniedMsg
            );
        } else if (context.succeeded) {
            accessIssueRegistry.recordSuccess(
                    projectIdStr,
                    projectName,
                    StackitConstants.RESOURCE_TYPE_DNS_ZONE,
                    null
            );
        }
    }
}
