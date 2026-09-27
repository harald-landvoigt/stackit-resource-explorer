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
import java.util.Set;
import java.util.UUID;

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
            existing.setName(entity.getName());
            existing.setStatus(entity.getStatus());
            existing.setRegion(entity.getRegion());
            existing.setProjectId(entity.getProjectId());
            existing.setUpdatedAt(Instant.now());
            existing.setDeletedAt(null);
            existing.setTags(entity.getTags());

            mergePublicIpHistory(existing, entity);

            existing.setData(entity.getData());
        } else {
            persist(entity);
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
        for (final StackitEntity entity : missing) {
            entity.setDeletedAt(Instant.now());
            if (StackitConstants.RESOURCE_TYPE_COMPUTE.equalsIgnoreCase(entity.getType()) && entity.getData() != null) {
                final Object histObj = entity.getData().get("publicIpHistory");
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
                    entity.getData().put("publicIpHistory", updatedList);
                }
            }
        }
    }

    public List<StackitEntity> search(final String query) {
        return search(query, 100);
    }

    public List<StackitEntity> search(final String query, final int limit) {
        final int maxResults = limit > 0 ? limit : 100;
        if (query == null || query.isBlank()) {
            return find("order by createdAt desc")
                    .page(0, maxResults)
                    .list();
        }
        final String trimmed = query.trim();
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

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByType(final String query) {
        return executeAggregation("type", query, false);
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByRegion(final String query) {
        return executeAggregation("coalesce(region, 'global')", query, false);
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByStatus(final String query) {
        final String columnExpr = "CASE WHEN deleted_at IS NOT NULL THEN 'DELETED' ELSE UPPER(coalesce(status, 'UNKNOWN')) END";
        return executeAggregation(columnExpr, query, false);
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByProject(final String query) {
        final String columnExpr = "CASE WHEN project_id IS NULL OR project_id = '' OR LOWER(project_id) = 'unknown' THEN 'Global / No Project' ELSE project_id END";
        return executeAggregation(columnExpr, query, false);
    }

    private List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> executeAggregation(
            final String columnExpr,
            final String query,
            final boolean excludeDeleted) {
        final String trimmed = query != null ? query.trim() : "";
        final String prefixQuery = !trimmed.isBlank() ? buildPrefixQuery(trimmed) : null;
        final String deletedClause = excludeDeleted ? "deleted_at IS NULL" : "1=1";

        final String sql;
        if (trimmed.isBlank()) {
            sql = "SELECT " + columnExpr + " AS agg_key, COUNT(*) FROM stackit_resources " +
                    "WHERE " + deletedClause + " " +
                    "GROUP BY 1 " +
                    "ORDER BY COUNT(*) DESC";
        } else if (prefixQuery != null) {
            sql = "SELECT " + columnExpr + " AS agg_key, COUNT(*) FROM stackit_resources " +
                    "WHERE (search_vector @@ websearch_to_tsquery('english', :query) " +
                    "   OR search_vector @@ to_tsquery('english', :prefixQuery)) " +
                    "AND " + deletedClause + " " +
                    "GROUP BY 1 " +
                    "ORDER BY COUNT(*) DESC";
        } else {
            sql = "SELECT " + columnExpr + " AS agg_key, COUNT(*) FROM stackit_resources " +
                    "WHERE search_vector @@ websearch_to_tsquery('english', :query) " +
                    "AND " + deletedClause + " " +
                    "GROUP BY 1 " +
                    "ORDER BY COUNT(*) DESC";
        }

        final var nativeQuery = getEntityManager().createNativeQuery(sql);
        if (!trimmed.isBlank()) {
            nativeQuery.setParameter("query", trimmed);
            if (prefixQuery != null) {
                nativeQuery.setParameter("prefixQuery", prefixQuery);
            }
        }

        @SuppressWarnings("unchecked")
        final List<Object[]> rows = nativeQuery.getResultList();
        final List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> results = new java.util.ArrayList<>(rows.size());
        for (final Object[] row : rows) {
            final String key = row[0] != null ? row[0].toString() : "UNKNOWN";
            final long count = ((Number) row[1]).longValue();
            results.add(new com.landvoigtit.stackit.resourceexplorer.AggregationItemDto(key, count));
        }
        return results;
    }

    private String buildPrefixQuery(final String query) {
        if (query == null) {
            return null;
        }
        final String[] tokens = query.trim().split("\\s+");
        final java.util.List<String> cleanTokens = new java.util.ArrayList<>();
        for (final String token : tokens) {
            final String cleaned = token.replaceAll("[^a-zA-Z0-9_-]", "");
            if (cleaned.matches(".*[a-zA-Z0-9].*")) {
                cleanTokens.add("'" + cleaned.replace("'", "''") + "':*");
            }
        }
        if (cleanTokens.isEmpty()) {
            return null;
        }
        return String.join(" & ", cleanTokens);
    }
}
