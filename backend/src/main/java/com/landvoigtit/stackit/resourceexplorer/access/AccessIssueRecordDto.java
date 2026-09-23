package com.landvoigtit.stackit.resourceexplorer.access;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Data Transfer Object representing an individual access issue or inspection result.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessIssueRecordDto {
    private String projectId;
    private String projectName;
    private String resourceType;
    private String region;
    private AccessStatus status;
    private Integer statusCode;
    private String errorMessage;
    private Instant lastChecked;
}
