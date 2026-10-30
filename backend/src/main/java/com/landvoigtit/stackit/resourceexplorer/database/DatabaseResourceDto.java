package com.landvoigtit.stackit.resourceexplorer.database;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object representing a STACKIT Managed Database instance
 * across multiple database and messaging engines.
 */
@Getter
@Setter
public class DatabaseResourceDto {

    @NotBlank
    private String instanceId;

    @NotBlank
    private String name;

    @NotBlank
    private String engine;

    private String status;
    private String version;
    private String flavor;
    private String flavorDescription;
    private Integer cpu;
    private Integer memory;
    private String planId;
    private Integer storage;
    private String storageClass;
    private Integer replicas;
    private String host;
    private Integer port;
    private String accessScope;
    private String backupSchedule;
    private List<String> acls;
    private String cfGuid;
    private String cfSpaceGuid;
    private String cfOrganizationGuid;
    private String dashboardUrl;
    private String imageUrl;
    private String created;
    private String updated;
    private Map<String, String> labels;
    private Map<String, Object> options;
}
