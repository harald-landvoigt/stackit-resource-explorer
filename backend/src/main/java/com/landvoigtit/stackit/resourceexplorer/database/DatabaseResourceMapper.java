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
        dto.setInstanceId(asString(json.get("id")));
        dto.setName(asString(json.get("name")));
        dto.setEngine(engine != null ? engine.toLowerCase() : "database");
        dto.setStatus(asString(json.get("status")));
        dto.setVersion(asString(json.get("version")));

        extractFlavorFields(json, dto);
        dto.setStorage(extractStorage(json));
        dto.setReplicas(extractReplicas(json));
        dto.setBackupSchedule(asString(json.get("backupSchedule")));
        dto.setAcls(extractAcls(json));
        dto.setCreated(asString(json.get("created")));
        dto.setUpdated(asString(json.get("updated")));
        dto.setLabels(extractLabels(json.get("labels")));
        dto.setOptions(extractOptions(json.get("options")));

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
        if (dto.getStorage() != null) {
            tags.put("storage-gb", String.valueOf(dto.getStorage()));
        }
        if (dto.getReplicas() != null) {
            tags.put("replicas", String.valueOf(dto.getReplicas()));
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
        data.put("version", dto.getVersion());
        data.put("storage", dto.getStorage());
        data.put("replicas", dto.getReplicas());
        data.put("status", dto.getStatus());
        data.put("backupSchedule", dto.getBackupSchedule());
        data.put("acls", dto.getAcls());
        data.put("created", dto.getCreated());
        data.put("updated", dto.getUpdated());
        if (dto.getLabels() != null) {
            data.put("labels", dto.getLabels());
        }
        if (dto.getOptions() != null) {
            data.put("options", dto.getOptions());
        }
        return data;
    }

    private static void extractFlavorFields(final Map<String, Object> json, final DatabaseResourceDto dto) {
        final Object flavorObj = json.get("flavor");
        if (flavorObj instanceof Map<?, ?> flavorMap) {
            final Object id = flavorMap.get("id");
            final Object name = flavorMap.get("name");
            final Object desc = flavorMap.get("description");
            dto.setFlavor(asString(id != null ? id : name));
            dto.setFlavorDescription(asString(desc));
        } else if (flavorObj != null) {
            dto.setFlavor(asString(flavorObj));
        } else if (json.get("plan") != null) {
            dto.setFlavor(asString(json.get("plan")));
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

    @SuppressWarnings("unchecked")
    private static List<String> extractAcls(final Map<String, Object> json) {
        final Object aclObj = json.containsKey("acl") ? json.get("acl") : json.get("acls");
        if (aclObj instanceof Map<?, ?> aclMap) {
            final Object items = aclMap.get("items");
            if (items instanceof List<?> itemList) {
                return convertToStringList(itemList);
            }
        } else if (aclObj instanceof List<?> list) {
            return convertToStringList(list);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractOptions(final Object optionsObj) {
        if (optionsObj instanceof Map<?, ?> rawMap) {
            final Map<String, Object> options = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k != null) {
                    options.put(k.toString(), v);
                }
            });
            return options;
        }
        return Collections.emptyMap();
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
