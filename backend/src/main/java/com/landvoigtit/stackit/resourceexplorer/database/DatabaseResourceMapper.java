package com.landvoigtit.stackit.resourceexplorer.database;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mapper utility for converting raw STACKIT database API payloads to DatabaseResourceDto
 * and StackitEntity models.
 */
public final class DatabaseResourceMapper {

    private DatabaseResourceMapper() {
        // Utility class
    }

    public static DatabaseResourceDto mapToDto(final Map<String, Object> json, final String engine) {
        if (json == null) {
            return null;
        }

        final DatabaseResourceDto dto = new DatabaseResourceDto();
        dto.setInstanceId(asString(json.get("id") != null ? json.get("id") : json.get("instanceId")));
        dto.setName(asString(json.get("name")));
        dto.setEngine(engine != null ? engine.toLowerCase() : "database");
        dto.setStatus(extractStatus(json));
        dto.setVersion(asString(json.get("version")));

        extractFlavorFields(json, dto);
        dto.setStorage(extractStorage(json));
        dto.setStorageClass(extractStorageClass(json));
        dto.setReplicas(extractReplicas(json));
        extractConnectionInfo(json, dto);
        dto.setAccessScope(extractAccessScope(json));
        dto.setBackupSchedule(asString(json.get("backupSchedule") != null ? json.get("backupSchedule") : json.get("backup_schedule")));
        dto.setAcls(extractAcls(json));
        dto.setCfGuid(asString(json.get("cfGuid") != null ? json.get("cfGuid") : json.get("cf_guid")));
        dto.setCfSpaceGuid(asString(json.get("cfSpaceGuid") != null ? json.get("cfSpaceGuid") : json.get("cf_space_guid")));
        dto.setCfOrganizationGuid(asString(json.get("cfOrganizationGuid") != null ? json.get("cfOrganizationGuid") : json.get("cf_organization_guid")));
        dto.setDashboardUrl(asString(json.get("dashboardUrl") != null ? json.get("dashboardUrl") : json.get("dashboard_url")));
        dto.setImageUrl(asString(json.get("imageUrl") != null ? json.get("imageUrl") : json.get("image_url")));
        dto.setCreated(asString(json.get("created")));
        dto.setUpdated(asString(json.get("updated")));
        dto.setLabels(extractLabels(json.get("labels")));
        dto.setOptions(extractOptions(json));

        return dto;
    }

    public static StackitEntity mapToEntity(final DatabaseResourceDto dto, final String projectId, final String region) {
        if (dto == null) {
            return null;
        }

        final StackitEntity entity = new StackitEntity();
        final String projId = projectId != null ? projectId : StackitConstants.UNKNOWN_PROJECT_ID;
        final String effectiveRegion = region != null ? region : StackitConstants.REGION_EU01;

        final String resourceId = dto.getInstanceId() != null ? dto.getInstanceId() : UUID.randomUUID().toString();
        entity.setId(generateDeterministicId(projId, dto.getEngine(), resourceId));
        entity.setResourceId(resourceId);
        entity.setName(dto.getName() != null ? dto.getName() : resourceId);
        entity.setType(StackitConstants.RESOURCE_TYPE_DATABASE);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : StackitConstants.STATUS_ACTIVE);
        entity.setRegion(effectiveRegion);
        entity.setProjectId(projId);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        entity.setTags(buildTags(dto));
        entity.setData(buildData(dto));

        return entity;
    }

    public static StackitEntity mapToEntity(final Map<String, Object> json,
                                            final String engine,
                                            final String projectId,
                                            final String region) {
        if (json == null) {
            return null;
        }
        final DatabaseResourceDto dto = mapToDto(json, engine);
        return mapToEntity(dto, projectId, region);
    }

    private static UUID generateDeterministicId(final String projectId, final String engine, final String instanceId) {
        try {
            return UUID.fromString(instanceId);
        } catch (final IllegalArgumentException ignored) {
            final String combined = projectId + "/" + engine + "/" + instanceId;
            return UUID.nameUUIDFromBytes(combined.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> buildTags(final DatabaseResourceDto dto) {
        final Map<String, String> tags = new HashMap<>();
        if (dto.getEngine() != null) {
            tags.put("engine", dto.getEngine());
        }
        if (dto.getVersion() != null) {
            tags.put("version", dto.getVersion());
        }
        if (dto.getFlavor() != null) {
            tags.put("flavor", dto.getFlavor());
        }
        if (dto.getCpu() != null) {
            tags.put("cpu", String.valueOf(dto.getCpu()));
        }
        if (dto.getMemory() != null) {
            tags.put("memory-gb", String.valueOf(dto.getMemory()));
        }
        if (dto.getPlanId() != null) {
            tags.put("plan-id", dto.getPlanId());
        }
        if (dto.getStorage() != null) {
            tags.put("storage-gb", String.valueOf(dto.getStorage()));
        }
        if (dto.getReplicas() != null) {
            tags.put("replicas", String.valueOf(dto.getReplicas()));
        }
        if (dto.getHost() != null) {
            tags.put("host", dto.getHost());
        }
        if (dto.getPort() != null) {
            tags.put("port", String.valueOf(dto.getPort()));
        }
        if (dto.getAccessScope() != null) {
            tags.put("access-scope", dto.getAccessScope());
        }
        if (dto.getLabels() != null) {
            tags.putAll(dto.getLabels());
        }
        return tags;
    }

    private static Map<String, Object> buildData(final DatabaseResourceDto dto) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("instanceId", dto.getInstanceId());
        data.put("name", dto.getName());
        data.put("engine", dto.getEngine());
        data.put("flavor", dto.getFlavor());
        data.put("flavorDescription", dto.getFlavorDescription());
        if (dto.getCpu() != null) {
            data.put("cpu", dto.getCpu());
        }
        if (dto.getMemory() != null) {
            data.put("memory", dto.getMemory());
        }
        if (dto.getPlanId() != null) {
            data.put("planId", dto.getPlanId());
        }
        data.put("version", dto.getVersion());
        data.put("storage", dto.getStorage());
        if (dto.getStorageClass() != null) {
            data.put("storageClass", dto.getStorageClass());
        }
        data.put("replicas", dto.getReplicas());
        data.put("status", dto.getStatus());
        if (dto.getHost() != null) {
            data.put("host", dto.getHost());
        }
        if (dto.getPort() != null) {
            data.put("port", dto.getPort());
        }
        if (dto.getAccessScope() != null) {
            data.put("accessScope", dto.getAccessScope());
        }
        data.put("backupSchedule", dto.getBackupSchedule());
        data.put("acls", dto.getAcls());
        if (dto.getCfGuid() != null) {
            data.put("cfGuid", dto.getCfGuid());
        }
        if (dto.getCfSpaceGuid() != null) {
            data.put("cfSpaceGuid", dto.getCfSpaceGuid());
        }
        if (dto.getCfOrganizationGuid() != null) {
            data.put("cfOrganizationGuid", dto.getCfOrganizationGuid());
        }
        if (dto.getDashboardUrl() != null) {
            data.put("dashboardUrl", dto.getDashboardUrl());
        }
        if (dto.getImageUrl() != null) {
            data.put("imageUrl", dto.getImageUrl());
        }
        data.put("created", dto.getCreated());
        data.put("updated", dto.getUpdated());
        if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
            data.put("labels", dto.getLabels());
        }
        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            data.put("options", dto.getOptions());
        }
        return data;
    }

    private static String extractStatus(final Map<String, Object> json) {
        if (json.get("status") != null) {
            return asString(json.get("status"));
        }
        if (json.get("state") != null) {
            return asString(json.get("state"));
        }
        final Object lastOpObj = json.get("lastOperation") != null
                ? json.get("lastOperation")
                : json.get("last_operation");
        if (lastOpObj instanceof Map<?, ?> lastOpMap) {
            if (lastOpMap.get("state") != null) {
                return asString(lastOpMap.get("state"));
            }
            if (lastOpMap.get("type") != null) {
                return asString(lastOpMap.get("type"));
            }
        }
        return null;
    }

    private static void extractFlavorFields(final Map<String, Object> json, final DatabaseResourceDto dto) {
        final Object flavorObj = json.get("flavor");
        if (flavorObj instanceof Map<?, ?> flavorMap) {
            final Object id = flavorMap.get("id");
            final Object name = flavorMap.get("name");
            final Object desc = flavorMap.get("description");
            final Integer cpu = asInteger(flavorMap.get("cpu"));
            final Integer mem = asInteger(flavorMap.get("memory") != null ? flavorMap.get("memory") : flavorMap.get("ram"));
            dto.setCpu(cpu);
            dto.setMemory(mem);
            dto.setFlavorDescription(asString(desc));
            if (id != null || name != null) {
                dto.setFlavor(asString(id != null ? id : name));
            } else if (cpu != null && mem != null) {
                dto.setFlavor(cpu + " vCPU / " + mem + " GB");
            }
        } else if (flavorObj != null) {
            dto.setFlavor(asString(flavorObj));
        }

        if (dto.getFlavor() == null) {
            if (json.get("flavorId") != null) {
                dto.setFlavor(asString(json.get("flavorId")));
            } else if (json.get("flavor_id") != null) {
                dto.setFlavor(asString(json.get("flavor_id")));
            } else if (json.get("planName") != null) {
                dto.setFlavor(asString(json.get("planName")));
            } else if (json.get("plan_name") != null) {
                dto.setFlavor(asString(json.get("plan_name")));
            } else if (json.get("plan") != null) {
                dto.setFlavor(asString(json.get("plan")));
            } else if (json.get("planId") != null) {
                dto.setFlavor(asString(json.get("planId")));
            }
        }

        if (json.get("planId") != null || json.get("plan_id") != null) {
            dto.setPlanId(asString(json.get("planId") != null ? json.get("planId") : json.get("plan_id")));
        }
        if (dto.getCpu() == null) {
            dto.setCpu(asInteger(json.get("cpu")));
        }
        if (dto.getMemory() == null) {
            dto.setMemory(asInteger(json.get("memory") != null ? json.get("memory") : json.get("ram")));
        }
    }

    private static Integer extractStorage(final Map<String, Object> json) {
        final Object storageObj = json.get("storage");
        if (storageObj instanceof Map<?, ?> storageMap) {
            return asInteger(storageMap.get("size"));
        } else if (storageObj instanceof Number num) {
            return num.intValue();
        } else if (storageObj != null) {
            return asInteger(storageObj);
        } else if (json.get("storageSize") != null) {
            return asInteger(json.get("storageSize"));
        }
        final Object paramsObj = json.get("parameters");
        if (paramsObj instanceof Map<?, ?> paramsMap) {
            final Object maxDisk = paramsMap.get("max_disk_threshold") != null
                    ? paramsMap.get("max_disk_threshold")
                    : paramsMap.get("maxDiskThreshold");
            if (maxDisk != null) {
                return asInteger(maxDisk);
            }
        }
        return null;
    }

    private static String extractStorageClass(final Map<String, Object> json) {
        final Object storageObj = json.get("storage");
        if (storageObj instanceof Map<?, ?> storageMap) {
            final Object cls = storageMap.get("class");
            if (cls != null) {
                return asString(cls);
            }
        }
        if (json.get("storageClass") != null) {
            return asString(json.get("storageClass"));
        }
        return null;
    }

    private static Integer extractReplicas(final Map<String, Object> json) {
        final Object replicasObj = json.get("replicas");
        if (replicasObj != null) {
            return asInteger(replicasObj);
        }
        final Object countObj = json.get("replicaCount");
        if (countObj != null) {
            return asInteger(countObj);
        }
        return null;
    }

    private static void extractConnectionInfo(final Map<String, Object> json, final DatabaseResourceDto dto) {
        final Object connObj = json.containsKey("connectionInfo")
                ? json.get("connectionInfo")
                : json.get("connection_info");
        if (connObj instanceof Map<?, ?> connMap) {
            final Object endpointObj = connMap.containsKey("write")
                    ? connMap.get("write")
                    : connMap.get("read");
            if (endpointObj instanceof Map<?, ?> endpointMap) {
                dto.setHost(asString(endpointMap.get("host")));
                dto.setPort(asInteger(endpointMap.get("port")));
            } else {
                dto.setHost(asString(connMap.get("host")));
                dto.setPort(asInteger(connMap.get("port")));
            }
        }
        if (dto.getHost() == null) {
            dto.setHost(asString(json.get("host") != null ? json.get("host") : json.get("hostname")));
        }
        if (dto.getPort() == null) {
            dto.setPort(asInteger(json.get("port")));
        }
    }

    private static String extractAccessScope(final Map<String, Object> json) {
        final Object networkObj = json.get("network");
        if (networkObj instanceof Map<?, ?> networkMap) {
            final Object scope = networkMap.containsKey("accessScope")
                    ? networkMap.get("accessScope")
                    : networkMap.get("access_scope");
            if (scope != null) {
                return asString(scope);
            }
        }
        return asString(json.get("accessScope"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractAcls(final Map<String, Object> json) {
        final Object networkObj = json.get("network");
        if (networkObj instanceof Map<?, ?> networkMap) {
            final Object netAclObj = networkMap.containsKey("acl")
                    ? networkMap.get("acl")
                    : networkMap.get("acls");
            final List<String> netAcls = extractAclListFromObject(netAclObj);
            if (!netAcls.isEmpty()) {
                return netAcls;
            }
        }
        final Object aclObj = json.containsKey("acl") ? json.get("acl") : json.get("acls");
        final List<String> topAcls = extractAclListFromObject(aclObj);
        if (!topAcls.isEmpty()) {
            return topAcls;
        }
        final Object paramsObj = json.get("parameters");
        if (paramsObj instanceof Map<?, ?> paramsMap) {
            final Object sgwAcl = paramsMap.get("sgw_acl") != null
                    ? paramsMap.get("sgw_acl")
                    : paramsMap.get("sgwAcl");
            final List<String> paramAcls = extractAclListFromObject(sgwAcl);
            if (!paramAcls.isEmpty()) {
                return paramAcls;
            }
        }
        if (json.get("sgw_acl") != null) {
            return extractAclListFromObject(json.get("sgw_acl"));
        }
        return Collections.emptyList();
    }

    private static List<String> extractAclListFromObject(final Object aclObj) {
        if (aclObj instanceof Map<?, ?> aclMap) {
            final Object items = aclMap.get("items");
            if (items instanceof List<?> itemList) {
                return convertToStringList(itemList);
            }
        } else if (aclObj instanceof List<?> list) {
            return convertToStringList(list);
        } else if (aclObj instanceof String str && !str.isBlank()) {
            final List<String> result = new ArrayList<>();
            for (final String part : str.split(",")) {
                final String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static List<String> convertToStringList(final List<?> list) {
        final List<String> result = new ArrayList<>();
        for (final Object item : list) {
            if (item != null) {
                result.add(item.toString());
            }
        }
        return result;
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

    private static Map<String, Object> extractOptions(final Map<String, Object> json) {
        final Map<String, Object> options = new LinkedHashMap<>();
        if (json.get("options") instanceof Map<?, ?> rawOptions) {
            rawOptions.forEach((k, v) -> {
                if (k != null && v != null) {
                    options.put(k.toString(), v);
                }
            });
        }
        if (json.get("parameters") instanceof Map<?, ?> rawParams) {
            rawParams.forEach((k, v) -> {
                if (k != null && v != null) {
                    options.put(k.toString(), v);
                }
            });
        }
        final Object retentionObj = json.get("retentionDays") != null
                ? json.get("retentionDays")
                : json.get("retention_days");
        if (retentionObj != null && !options.containsKey("retentionDays")) {
            options.put("retentionDays", retentionObj);
        }
        if (json.get("encryption") != null && !options.containsKey("encryption")) {
            options.put("encryption", json.get("encryption"));
        }
        return options.isEmpty() ? Collections.emptyMap() : options;
    }

    private static String asString(final Object obj) {
        return obj != null ? obj.toString() : null;
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
