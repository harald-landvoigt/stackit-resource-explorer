package com.landvoigtit.stackit.resourceexplorer.persistence;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class StackitResourceRepository implements PanacheRepositoryBase<StackitEntity, UUID> {

    @Transactional
    public void persistOrUpdate(final StackitEntity entity) {
        if (entity == null || entity.getId() == null) {
            return;
        }
        // Prune any stale duplicate entities with the same (type, projectId, resourceId) but a different id
        if (entity.getType() != null && entity.getProjectId() != null && entity.getResourceId() != null) {
            final List<StackitEntity> duplicates = list("type = ?1 and projectId = ?2 and resourceId = ?3 and id != ?4",
                    entity.getType(), entity.getProjectId(), entity.getResourceId(), entity.getId());
            if (!duplicates.isEmpty()) {
                for (final StackitEntity dup : duplicates) {
                    delete(dup);
                }
                flush();
            }
        }
        final StackitEntity existing = findById(entity.getId());
        if (existing != null) {
            mergePublicIpHistory(existing, entity);

            final boolean changed = existing.getDeletedAt() != null
                    || !Objects.equals(existing.getName(), entity.getName())
                    || !Objects.equals(existing.getStatus(), entity.getStatus())
                    || !Objects.equals(existing.getRegion(), entity.getRegion())
                    || !Objects.equals(existing.getProjectId(), entity.getProjectId())
                    || !Objects.equals(existing.getTags(), entity.getTags())
                    || !Objects.equals(existing.getData(), entity.getData());

            if (!changed) {
                return;
            }

            existing.setName(entity.getName());
            existing.setStatus(entity.getStatus());
            existing.setRegion(entity.getRegion());
            existing.setProjectId(entity.getProjectId());
            existing.setUpdatedAt(Instant.now());
            existing.setDeletedAt(null);
            existing.setTags(entity.getTags());
            existing.setData(entity.getData());
        } else {
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(Instant.now());
            }
            if (entity.getUpdatedAt() == null) {
                entity.setUpdatedAt(Instant.now());
            }
            persist(entity);
        }
    }

    @Transactional
    public void persistOrUpdateAll(final List<StackitEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (final StackitEntity entity : entities) {
            persistOrUpdate(entity);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergePublicIpHistory(final StackitEntity existing, final StackitEntity entity) {
        if (existing == null || entity == null || entity.getData() == null) {
            return;
        }
        if (!StackitConstants.RESOURCE_TYPE_COMPUTE.equalsIgnoreCase(entity.getType())) {
            return;
        }

        final Map<String, Object> existingData = existing.getData();
        final Map<String, Object> newData = entity.getData();
        if (existingData == null) {
            return;
        }

        final Object existingHistObj = existingData.get("publicIpHistory");
        final List<Map<String, Object>> existingHistory = (existingHistObj instanceof List<?>)
                ? (List<Map<String, Object>>) existingHistObj
                : Collections.emptyList();

        final Object newPublicIpsObj = newData.get("publicIps");
        final List<String> currentPublicIps = (newPublicIpsObj instanceof List<?>)
                ? (List<String>) newPublicIpsObj
                : Collections.emptyList();

        if (existingHistory.isEmpty() && currentPublicIps.isEmpty()) {
            return;
        }

        final String now = Instant.now().toString();
        final List<Map<String, Object>> mergedHistory = new ArrayList<>();
        final Set<String> processedIps = new HashSet<>();

        for (final Map<String, Object> entry : existingHistory) {
            if (entry == null) {
                continue;
            }
            final String ip = (String) entry.get("ip");
            if (ip == null || ip.isBlank()) {
                continue;
            }
            final Map<String, Object> updatedEntry = new LinkedHashMap<>(entry);
            processedIps.add(ip);
            if (currentPublicIps.contains(ip)) {
                updatedEntry.put("active", true);
                updatedEntry.put("lastSeen", now);
            } else {
                updatedEntry.put("active", false);
            }
            mergedHistory.add(updatedEntry);
        }

        for (final String ip : currentPublicIps) {
            if (ip != null && !ip.isBlank() && !processedIps.contains(ip)) {
                final Map<String, Object> newEntry = new LinkedHashMap<>();
                newEntry.put("ip", ip);
                newEntry.put("firstSeen", now);
                newEntry.put("lastSeen", now);
                newEntry.put("active", true);
                mergedHistory.add(newEntry);
                processedIps.add(ip);
            }
        }

        if (!mergedHistory.isEmpty()) {
            newData.put("publicIpHistory", mergedHistory);
        }
    }

    @Transactional
    public void softDeleteMissing(final String type, final String projectId, final List<String> activeResourceIds) {
        if (activeResourceIds == null) {
            return;
        }
        final List<StackitEntity> missing;
        if (activeResourceIds.isEmpty()) {
            missing = list("type = ?1 and projectId = ?2 and deletedAt is null", type, projectId);
        } else {
            missing = list("type = ?1 and projectId = ?2 and resourceId not in ?3 and deletedAt is null", type, projectId, activeResourceIds);
        }
        final Instant now = Instant.now();
        for (final StackitEntity entity : missing) {
            entity.setDeletedAt(now);
            entity.setUpdatedAt(now);
            entity.setStatus(StackitConstants.STATUS_DELETED);
            if (entity.getData() != null) {
                final Map<String, Object> updatedData = new LinkedHashMap<>(entity.getData());
                boolean dataChanged = false;
                if (updatedData.containsKey("status") && !"deleted".equalsIgnoreCase(String.valueOf(updatedData.get("status")))) {
                    updatedData.put("status", "deleted");
                    dataChanged = true;
                }
                if (updatedData.containsKey("state") && !"deleted".equalsIgnoreCase(String.valueOf(updatedData.get("state")))) {
                    updatedData.put("state", "deleted");
                    dataChanged = true;
                }
                if (StackitConstants.RESOURCE_TYPE_COMPUTE.equalsIgnoreCase(entity.getType())) {
                    final Object histObj = updatedData.get("publicIpHistory");
                    if (histObj instanceof List<?> histList) {
                        final List<Map<String, Object>> updatedList = new ArrayList<>();
                        for (final Object item : histList) {
                            if (item instanceof Map<?, ?> itemMap) {
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> mutableMap = new LinkedHashMap<>((Map<String, Object>) itemMap);
                                mutableMap.put("active", false);
                                updatedList.add(mutableMap);
                            }
                        }
                        updatedData.put("publicIpHistory", updatedList);
                        dataChanged = true;
                    }
                }
                if (dataChanged) {
                    entity.setData(updatedData);
                }
            }
        }
    }

    public List<StackitEntity> search(final String query) {
        return search(query, 100);
    }

    public static final int MAX_QUERY_LENGTH = 256;
    public static final int MAX_PREFIX_TOKENS = 10;
    private static final Pattern CLEAN_TOKEN_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]");

    public List<StackitEntity> search(final String query, final int limit) {
        final int maxResults = limit > 0 ? limit : 100;
        if (query == null || query.isBlank()) {
            return find("order by createdAt desc")
                    .page(0, maxResults)
                    .list();
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH).trim();
        }
        final String prefixQuery = buildPrefixQuery(trimmed);

        final String sql;
        if (prefixQuery != null) {
            sql = "SELECT * FROM stackit_resources " +
                    "WHERE (search_vector @@ websearch_to_tsquery('english', :query) " +
                    "   OR search_vector @@ to_tsquery('english', :prefixQuery)) " +
                    "ORDER BY (ts_rank(search_vector, websearch_to_tsquery('english', :query)) * 2.0 + " +
                    "         ts_rank(search_vector, to_tsquery('english', :prefixQuery))) DESC " +
                    "LIMIT :limit";
        } else {
            sql = "SELECT * FROM stackit_resources " +
                    "WHERE search_vector @@ websearch_to_tsquery('english', :query) " +
                    "ORDER BY ts_rank(search_vector, websearch_to_tsquery('english', :query)) DESC " +
                    "LIMIT :limit";
        }

        final var nativeQuery = getEntityManager()
                .createNativeQuery(sql, StackitEntity.class)
                .setParameter("query", trimmed)
                .setParameter("limit", maxResults);

        if (prefixQuery != null) {
            nativeQuery.setParameter("prefixQuery", prefixQuery);
        }

        @SuppressWarnings("unchecked")
        final List<StackitEntity> results = nativeQuery.getResultList();
        return results;
    }

    public record AggregationsResult(
            List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byType,
            List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byRegion,
            List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byStatus,
            List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byProject
    ) {}

    public AggregationsResult aggregateAll(final String query, final boolean excludeDeleted) {
        final String trimmed = query != null ? query.trim() : "";
        final String boundedQuery = trimmed.length() > MAX_QUERY_LENGTH ? trimmed.substring(0, MAX_QUERY_LENGTH).trim() : trimmed;
        final String prefixQuery = !boundedQuery.isBlank() ? buildPrefixQuery(boundedQuery) : null;
        final String deletedClause = excludeDeleted ? "deleted_at IS NULL" : "1=1";

        final String whereClause;
        if (boundedQuery.isBlank()) {
            whereClause = "WHERE " + deletedClause;
        } else if (prefixQuery != null) {
            whereClause = "WHERE (search_vector @@ websearch_to_tsquery('english', :query) " +
                    "   OR search_vector @@ to_tsquery('english', :prefixQuery)) " +
                    "AND " + deletedClause;
        } else {
            whereClause = "WHERE search_vector @@ websearch_to_tsquery('english', :query) " +
                    "AND " + deletedClause;
        }

        final String typeExpr = AggregationDimension.TYPE.getExpression();
        final String regionExpr = AggregationDimension.REGION.getExpression();
        final String statusExpr = AggregationDimension.STATUS.getExpression();
        final String projectExpr = AggregationDimension.PROJECT.getExpression();

        final String sql = "WITH matched AS (" +
                "    SELECT " +
                "        " + typeExpr + " AS dim_type, " +
                "        " + regionExpr + " AS dim_region, " +
                "        " + statusExpr + " AS dim_status, " +
                "        " + projectExpr + " AS dim_project " +
                "    FROM stackit_resources " +
                "    " + whereClause + " " +
                ") " +
                "SELECT " +
                "    GROUPING(dim_type) AS g_type, " +
                "    GROUPING(dim_region) AS g_region, " +
                "    GROUPING(dim_status) AS g_status, " +
                "    GROUPING(dim_project) AS g_project, " +
                "    dim_type, dim_region, dim_status, dim_project, " +
                "    COUNT(*) AS cnt " +
                "FROM matched " +
                "GROUP BY GROUPING SETS ((dim_type), (dim_region), (dim_status), (dim_project)) " +
                "ORDER BY cnt DESC";

        final var nativeQuery = getEntityManager().createNativeQuery(sql);
        if (!boundedQuery.isBlank()) {
            nativeQuery.setParameter("query", boundedQuery);
            if (prefixQuery != null) {
                nativeQuery.setParameter("prefixQuery", prefixQuery);
            }
        }

        @SuppressWarnings("unchecked")
        final List<Object[]> rows = nativeQuery.getResultList();
        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byType = new java.util.ArrayList<>();
        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byRegion = new java.util.ArrayList<>();
        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byStatus = new java.util.ArrayList<>();
        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> byProject = new java.util.ArrayList<>();

        for (final Object[] row : rows) {
            final int gType = ((Number) row[0]).intValue();
            final int gRegion = ((Number) row[1]).intValue();
            final int gStatus = ((Number) row[2]).intValue();
            final int gProject = ((Number) row[3]).intValue();
            final long count = ((Number) row[8]).longValue();

            if (gType == 0) {
                final String key = row[4] != null ? row[4].toString() : "UNKNOWN";
                byType.add(new com.landvoigtit.stackit.resourceexplorer.AggregationItemDto(key, count));
            } else if (gRegion == 0) {
                final String key = row[5] != null ? row[5].toString() : "UNKNOWN";
                byRegion.add(new com.landvoigtit.stackit.resourceexplorer.AggregationItemDto(key, count));
            } else if (gStatus == 0) {
                final String key = row[6] != null ? row[6].toString() : "UNKNOWN";
                byStatus.add(new com.landvoigtit.stackit.resourceexplorer.AggregationItemDto(key, count));
            } else if (gProject == 0) {
                final String key = row[7] != null ? row[7].toString() : "UNKNOWN";
                byProject.add(new com.landvoigtit.stackit.resourceexplorer.AggregationItemDto(key, count));
            }
        }

        return new AggregationsResult(byType, byRegion, byStatus, byProject);
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByType(final String query) {
        return aggregateAll(query, false).byType();
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByRegion(final String query) {
        return aggregateAll(query, false).byRegion();
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByStatus(final String query) {
        return aggregateAll(query, false).byStatus();
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByProject(final String query) {
        return aggregateAll(query, false).byProject();
    }

    public String buildPrefixQuery(final String query) {
        if (query == null) {
            return null;
        }
        final String[] tokens = query.trim().split("\\s+");
        final java.util.List<String> cleanTokens = new java.util.ArrayList<>();
        for (final String token : tokens) {
            final String cleaned = CLEAN_TOKEN_PATTERN.matcher(token).replaceAll("");
            if (containsAlphanumeric(cleaned)) {
                cleanTokens.add("'" + cleaned.replace("'", "''") + "':*");
                if (cleanTokens.size() >= MAX_PREFIX_TOKENS) {
                    break;
                }
            }
        }
        if (cleanTokens.isEmpty()) {
            return null;
        }
        return String.join(" & ", cleanTokens);
    }

    private static boolean containsAlphanumeric(final String str) {
        for (int i = 0; i < str.length(); i++) {
            final char c = str.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return true;
            }
        }
        return false;
    }
}
