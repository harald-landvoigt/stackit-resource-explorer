package com.landvoigtit.stackit.resourceexplorer.access;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Data Transfer Object representing the consolidated access audit report with summary,
 * matrix rows, and detailed issues.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessIssuesSummaryDto {
    private long totalIssues;
    private long totalProjectsChecked;
    private long affectedProjectsCount;
    private List<ProjectAccessMatrixRowDto> matrix;
    private List<AccessIssueRecordDto> issues;
}
