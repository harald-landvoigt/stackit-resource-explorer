package com.landvoigtit.stackit.resourceexplorer.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class StackitResourceRepository implements PanacheRepositoryBase<StackitEntity, UUID> {

    @Transactional
    public void persistOrUpdate(final StackitEntity entity) {
        if (entity == null || entity.getId() == null) {
            return;
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
            existing.setData(entity.getData());
        } else {
            persist(entity);
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
        }
    }

    public List<StackitEntity> search(final String query) {
        return search(query, 100);
    }

    public List<StackitEntity> search(final String query, final int limit) {
        final int maxResults = limit > 0 ? limit : 100;
        if (query == null || query.isBlank()) {
            return find("deletedAt is null order by createdAt desc")
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
                    "AND deleted_at IS NULL " +
                    "ORDER BY (ts_rank(search_vector, websearch_to_tsquery('english', :query)) * 2.0 + " +
                    "         ts_rank(search_vector, to_tsquery('english', :prefixQuery))) DESC " +
                    "LIMIT :limit";
        } else {
            sql = "SELECT * FROM stackit_resources " +
                    "WHERE search_vector @@ websearch_to_tsquery('english', :query) " +
                    "AND deleted_at IS NULL " +
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
        return executeAggregation("type", query, true);
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByRegion(final String query) {
        return executeAggregation("coalesce(region, 'global')", query, true);
    }

    public List<com.landvoigtit.stackit.resourceexplorer.AggregationItemDto> aggregateByStatus(final String query) {
        final String columnExpr = "CASE WHEN deleted_at IS NOT NULL THEN 'DELETED' ELSE UPPER(coalesce(status, 'UNKNOWN')) END";
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
