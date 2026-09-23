package com.landvoigtit.stackit.resourceexplorer.access;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Data Transfer Object representing a row in the Project x Resource Type access matrix.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectAccessMatrixRowDto {
    private String projectId;
    private String projectName;
    private Map<String, AccessStatus> statuses;
    private boolean hasAccessIssues;
}
