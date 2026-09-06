package com.landvoigtit.stackit.resourceexplorer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "stackit_resources")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class StackitEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags")
    private Map<String, String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    private Map<String, Object> data;

    @Column(
            name = "search_vector",
            columnDefinition = "tsvector",
            insertable = false,
            updatable = false
    )
    @org.hibernate.annotations.GeneratedColumn(
            "setweight(to_tsvector('english', coalesce(name, '')), 'A') || " +
            "setweight(to_tsvector('english', coalesce(resource_id, '')), 'A') || " +
            "setweight(to_tsvector('english', coalesce(type, '')), 'B') || " +
            "setweight(to_tsvector('english', coalesce(tags::text, '')), 'B') || " +
            "setweight(to_tsvector('english', coalesce(status, '')), 'C') || " +
            "setweight(to_tsvector('english', coalesce(region, '')), 'C') || " +
            "setweight(to_tsvector('english', coalesce(project_id, '')), 'C') || " +
            "setweight(to_tsvector('english', coalesce(data::text, '')), 'C')"
    )
    private String searchVector;
}
