package com.landvoigtit.stackit.resourceexplorer.dns;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class DnsZoneResourceMapper {

    private DnsZoneResourceMapper() {
        // Utility class
    }

    public static DnsZoneResourceDto mapToDto(final Map<String, Object> zoneJson,
                                              final List<Map<String, Object>> recordSetsJson) {
        if (zoneJson == null) {
            return null;
        }

        final DnsZoneResourceDto dto = new DnsZoneResourceDto();
        dto.setZoneId(asString(zoneJson.get("id")));
        dto.setName(asString(zoneJson.get("name")));
        dto.setDnsName(asString(zoneJson.get("dnsName")));
        dto.setDescription(asString(zoneJson.get("description")));
        dto.setZoneType(asString(zoneJson.get("type")));
        dto.setVisibility(asString(zoneJson.get("visibility")));
        dto.setActive(asBoolean(zoneJson.get("active")));
        dto.setIsReverseZone(asBoolean(zoneJson.get("isReverseZone")));
        dto.setPrimaryNameServer(asString(zoneJson.get("primaryNameServer")));
        dto.setContactEmail(asString(zoneJson.get("contactEmail")));
        dto.setDefaultTTL(asInteger(zoneJson.get("defaultTTL")));
        dto.setRecordCount(asInteger(zoneJson.get("recordCount")));
        dto.setAcl(asString(zoneJson.get("acl")));

        final String state = asString(zoneJson.get("state"));
        dto.setStatus(resolveStatus(state, dto.getActive()));
        dto.setLabels(extractLabels(zoneJson.get("labelsMap")));

        final List<DnsRecordSetDto> recordSets = mapRecordSets(recordSetsJson);
        dto.setRecordSets(recordSets);
        dto.setRecordSetsSummary(calculateRecordSummary(recordSets));

        return dto;
    }

    public static DnsRecordSetDto mapRecordSetToDto(final Map<String, Object> rrsetJson) {
        if (rrsetJson == null) {
            return null;
        }

        final DnsRecordSetDto dto = new DnsRecordSetDto();
        dto.setId(asString(rrsetJson.get("id")));
        dto.setName(asString(rrsetJson.get("name")));
        dto.setType(asString(rrsetJson.get("type")));
        dto.setTtl(asInteger(rrsetJson.get("ttl")));
        dto.setComment(asString(rrsetJson.get("comment")));
        dto.setActive(asBoolean(rrsetJson.get("active")));
        dto.setState(asString(rrsetJson.get("state")));
        dto.setRecords(extractRecords(rrsetJson.get("records")));
        return dto;
    }

    public static StackitEntity mapToEntity(final DnsZoneResourceDto dto) {
        if (dto == null) {
            return null;
        }

        final StackitEntity entity = new StackitEntity();
        if (dto.getZoneId() != null) {
            try {
                entity.setId(UUID.fromString(dto.getZoneId()));
            } catch (final IllegalArgumentException e) {
                entity.setId(UUID.nameUUIDFromBytes(dto.getZoneId().getBytes()));
            }
            entity.setResourceId(dto.getZoneId());
        }

        entity.setName(dto.getDnsName() != null && !dto.getDnsName().isBlank() ? dto.getDnsName() : dto.getName());
        entity.setType(StackitConstants.RESOURCE_TYPE_DNS_ZONE);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : StackitConstants.STATUS_ACTIVE);
        entity.setRegion(StackitConstants.GLOBAL_REGION);
        entity.setProjectId(StackitConstants.UNKNOWN_PROJECT_ID);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        entity.setTags(buildEntityTags(dto));
        entity.setData(buildEntityData(dto));

        return entity;
    }

    public static StackitEntity mapRecordSetToEntity(final DnsRecordSetDto dto,
                                                     final String projectId,
                                                     final String zoneId,
                                                     final String zoneName) {
        if (dto == null) {
            return null;
        }

        final String resourceId = (dto.getId() != null && !dto.getId().isBlank())
                ? dto.getId()
                : (zoneId + "/" + dto.getName() + "/" + dto.getType());

        final StackitEntity entity = new StackitEntity();
        try {
            entity.setId(UUID.fromString(resourceId));
        } catch (final IllegalArgumentException e) {
            entity.setId(UUID.nameUUIDFromBytes(resourceId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        entity.setResourceId(resourceId);
        entity.setName(dto.getName());
        entity.setType(StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET);
        entity.setStatus(dto.getState() != null && !dto.getState().isBlank() ? dto.getState() : StackitConstants.STATUS_ACTIVE);
        entity.setRegion(StackitConstants.GLOBAL_REGION);
        entity.setProjectId(projectId != null ? projectId : StackitConstants.UNKNOWN_PROJECT_ID);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        final Map<String, String> tags = new HashMap<>();
        if (dto.getType() != null) {
            tags.put("recordType", dto.getType());
        }
        if (zoneName != null && !zoneName.isBlank()) {
            tags.put("zoneName", zoneName);
        }
        if (zoneId != null && !zoneId.isBlank()) {
            tags.put("zoneId", zoneId);
        }
        entity.setTags(tags);

        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordSetId", dto.getId());
        data.put("zoneId", zoneId);
        data.put("zoneName", zoneName);
        data.put("recordType", dto.getType());
        data.put("ttl", dto.getTtl());
        data.put("records", dto.getRecords());
        data.put("comment", dto.getComment());
        data.put("active", dto.getActive());
        data.put("state", dto.getState());
        data.put("matchedPublicIpId", dto.getMatchedPublicIpId());
        data.put("matchedServerId", dto.getMatchedServerId());
        data.put("matchedServerName", dto.getMatchedServerName());
        entity.setData(data);

        return entity;
    }

    private static Map<String, String> buildEntityTags(final DnsZoneResourceDto dto) {
        final Map<String, String> tags = new HashMap<>();
        if (dto.getLabels() != null) {
            tags.putAll(dto.getLabels());
        }
        if (dto.getDnsName() != null) {
            tags.put("dnsName", dto.getDnsName());
        }
        if (dto.getZoneType() != null) {
            tags.put("zone-type", dto.getZoneType());
        }
        if (dto.getVisibility() != null) {
            tags.put("visibility", dto.getVisibility());
        }
        if (dto.getActive() != null) {
            tags.put("active", String.valueOf(dto.getActive()));
        }
        if (dto.getRecordCount() != null) {
            tags.put("record-count", String.valueOf(dto.getRecordCount()));
        }
        return tags;
    }

    private static Map<String, Object> buildEntityData(final DnsZoneResourceDto dto) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("zoneId", dto.getZoneId());
        data.put("name", dto.getName());
        data.put("dnsName", dto.getDnsName());
        data.put("zoneType", dto.getZoneType());
        data.put("visibility", dto.getVisibility());
        data.put("isReverseZone", dto.getIsReverseZone());
        data.put("active", dto.getActive());
        data.put("primaryNameServer", dto.getPrimaryNameServer());
        data.put("contactEmail", dto.getContactEmail());
        data.put("defaultTTL", dto.getDefaultTTL());
        data.put("recordCount", dto.getRecordCount());
        data.put("acl", dto.getAcl());
        data.put("description", dto.getDescription());
        if (dto.getRecordSetsSummary() != null) {
            data.put("recordSetsSummary", dto.getRecordSetsSummary());
        }
        if (dto.getRecordSets() != null) {
            data.put("recordSets", dto.getRecordSets());
        }
        return data;
    }

    private static String resolveStatus(final String state, final Boolean active) {
        if ("CREATE_SUCCEEDED".equalsIgnoreCase(state) || Boolean.TRUE.equals(active)) {
            return StackitConstants.STATUS_ACTIVE;
        } else if (state != null && !state.isBlank()) {
            return state;
        }
        return StackitConstants.STATUS_ACTIVE;
    }

    private static List<DnsRecordSetDto> mapRecordSets(final List<Map<String, Object>> recordSetsJson) {
        if (recordSetsJson == null || recordSetsJson.isEmpty()) {
            return Collections.emptyList();
        }
        final List<DnsRecordSetDto> list = new ArrayList<>();
        for (final Map<String, Object> raw : recordSetsJson) {
            final DnsRecordSetDto dto = mapRecordSetToDto(raw);
            if (dto != null) {
                list.add(dto);
            }
        }
        return list;
    }

    private static Map<String, Integer> calculateRecordSummary(final List<DnsRecordSetDto> recordSets) {
        if (recordSets == null || recordSets.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, Integer> summary = new TreeMap<>();
        for (final DnsRecordSetDto rs : recordSets) {
            final String type = rs.getType() != null ? rs.getType().toUpperCase() : "UNKNOWN";
            summary.put(type, summary.getOrDefault(type, 0) + 1);
        }
        return summary;
    }

    private static List<String> extractRecords(final Object rawRecords) {
        if (rawRecords instanceof List<?> list) {
            final List<String> result = new ArrayList<>();
            for (final Object item : list) {
                if (item instanceof Map<?, ?> itemMap && itemMap.get("content") != null) {
                    result.add(itemMap.get("content").toString());
                } else if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static Map<String, String> extractLabels(final Object labelsObj) {
        if (labelsObj instanceof Map<?, ?> rawMap) {
            final Map<String, String> labels = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null && v != null) {
                    labels.put(k.toString(), v.toString());
                }
            });
            return labels;
        }
        return Collections.emptyMap();
    }

    private static String asString(final Object obj) {
        return obj != null ? obj.toString() : null;
    }

    private static Boolean asBoolean(final Object obj) {
        if (obj instanceof Boolean b) {
            return b;
        }
        return obj != null ? Boolean.valueOf(obj.toString()) : null;
    }

    private static Integer asInteger(final Object obj) {
        if (obj instanceof Number num) {
            return num.intValue();
        }
        if (obj != null) {
            try {
                return Integer.parseInt(obj.toString());
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
