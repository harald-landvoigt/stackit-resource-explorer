package com.landvoigtit.stackit.resourceexplorer.access;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory registry for tracking access statuses and permission failures across
 * STACKIT projects and resource types.
 */
@ApplicationScoped
public class AccessIssueRegistry {

    public static final List<String> STANDARD_RESOURCE_TYPES = List.of(
            StackitConstants.RESOURCE_TYPE_COMPUTE,
            StackitConstants.RESOURCE_TYPE_STORAGE,
            StackitConstants.RESOURCE_TYPE_NETWORK,
            StackitConstants.RESOURCE_TYPE_NETWORK_VPC,
            StackitConstants.RESOURCE_TYPE_VMDISKS,
            StackitConstants.RESOURCE_TYPE_IAM,
            StackitConstants.RESOURCE_TYPE_BILLING
    );

    private final Map<String, String> projectNames = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AccessStatus>> projectStatuses = new ConcurrentHashMap<>();
    private final Map<String, AccessIssueRecordDto> activeIssues = new ConcurrentHashMap<>();

    /**
     * Registers a known project name without recording resource-type statuses.
     */
    public void registerProject(final String projectId, final String projectName) {
        if (projectId != null && !projectId.isBlank()) {
            updateProjectName(projectId, projectName);
        }
    }

    /**
     * Records a successful access/scraping attempt for a project and resource type.
     */
    public void recordSuccess(final String projectId, final String projectName, final String resourceType, final String region) {
        if (projectId == null || projectId.isBlank() || resourceType == null) {
            return;
        }
        updateProjectName(projectId, projectName);
        projectStatuses.computeIfAbsent(projectId, k -> new ConcurrentHashMap<>())
                .put(resourceType.toLowerCase(), AccessStatus.ACCESSIBLE);
        activeIssues.remove(buildKey(projectId, resourceType));
    }

    /**
     * Records an access failure or permission denied issue for a project and resource type.
     */
    public void recordFailure(final String projectId,
                              final String projectName,
                              final String resourceType,
                              final String region,
                              final Integer statusCode,
                              final String errorMessage) {
        if (projectId == null || projectId.isBlank() || resourceType == null) {
            return;
        }
        updateProjectName(projectId, projectName);
        final String normType = resourceType.toLowerCase();
        projectStatuses.computeIfAbsent(projectId, k -> new ConcurrentHashMap<>())
                .put(normType, AccessStatus.ACCESS_DENIED);

        final AccessIssueRecordDto issue = AccessIssueRecordDto.builder()
                .projectId(projectId)
                .projectName(resolveDisplayName(projectId, projectName))
                .resourceType(normType)
                .region(region != null ? region : StackitConstants.GLOBAL_REGION)
                .status(AccessStatus.ACCESS_DENIED)
                .statusCode(statusCode)
                .errorMessage(errorMessage)
                .lastChecked(Instant.now())
                .build();

        activeIssues.put(buildKey(projectId, normType), issue);
    }

    /**
     * Generates a consolidated summary report containing counts, matrix rows, and active issues.
     */
    public AccessIssuesSummaryDto getSummary() {
        final List<ProjectAccessMatrixRowDto> matrix = buildMatrixRows();
        final List<AccessIssueRecordDto> issues = activeIssues.values().stream()
                .sorted(Comparator.comparing(AccessIssueRecordDto::getLastChecked, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        final long affectedProjects = matrix.stream().filter(ProjectAccessMatrixRowDto::isHasAccessIssues).count();

        return AccessIssuesSummaryDto.builder()
                .totalIssues(issues.size())
                .totalProjectsChecked(matrix.size())
                .affectedProjectsCount(affectedProjects)
                .matrix(matrix)
                .issues(issues)
                .build();
    }

    /**
     * Clears all recorded statuses and active issues (useful for testing or full scraper reset).
     */
    public void clear() {
        projectNames.clear();
        projectStatuses.clear();
        activeIssues.clear();
    }

    private List<ProjectAccessMatrixRowDto> buildMatrixRows() {
        return projectNames.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> {
                    final String projectId = entry.getKey();
                    final String projectName = entry.getValue();
                    final Map<String, AccessStatus> currentStatuses = projectStatuses.getOrDefault(projectId, Collections.emptyMap());

                    final Map<String, AccessStatus> statuses = new LinkedHashMap<>();
                    boolean hasIssues = false;
                    for (final String type : STANDARD_RESOURCE_TYPES) {
                        final AccessStatus status = currentStatuses.getOrDefault(type, AccessStatus.NOT_CHECKED);
                        statuses.put(type, status);
                        if (status == AccessStatus.ACCESS_DENIED) {
                            hasIssues = true;
                        }
                    }

                    return ProjectAccessMatrixRowDto.builder()
                            .projectId(projectId)
                            .projectName(projectName)
                            .statuses(statuses)
                            .hasAccessIssues(hasIssues)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private void updateProjectName(final String projectId, final String projectName) {
        if (projectName != null && !projectName.isBlank()) {
            projectNames.put(projectId, projectName);
        } else {
            projectNames.putIfAbsent(projectId, projectId);
        }
    }

    private String resolveDisplayName(final String projectId, final String projectName) {
        if (projectName != null && !projectName.isBlank()) {
            return projectName;
        }
        return projectNames.getOrDefault(projectId, projectId);
    }

    private String buildKey(final String projectId, final String resourceType) {
        return projectId + "#" + resourceType.toLowerCase();
    }
}
