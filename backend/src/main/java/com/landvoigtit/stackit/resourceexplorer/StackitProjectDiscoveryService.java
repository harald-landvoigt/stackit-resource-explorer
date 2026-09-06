package com.landvoigtit.stackit.resourceexplorer;

import cloud.stackit.sdk.core.exception.ApiException;
import cloud.stackit.sdk.resourcemanager.v0api.api.ResourceManagerApi;
import cloud.stackit.sdk.resourcemanager.v0api.model.GetProjectResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListFoldersResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListFoldersResponseItemsInner;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListProjectsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Parent;
import cloud.stackit.sdk.resourcemanager.v0api.model.ParentListInner;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class StackitProjectDiscoveryService {

    private static final BigDecimal PAGE_SIZE = BigDecimal.valueOf(50);

    private final StackitSdkConfig sdkConfig;
    private final ResourceManagerApi resourceManagerApi;

    @Inject
    public StackitProjectDiscoveryService(final StackitSdkConfig sdkConfig, final ResourceManagerApi resourceManagerApi) {
        this.sdkConfig = sdkConfig;
        this.resourceManagerApi = resourceManagerApi;
    }

    public final String discoverOrganizationId() {
        try {
            final String tokenOrgId = sdkConfig.getDiscoveredOrganizationId();
            if (tokenOrgId != null && !tokenOrgId.isBlank()) {
                log.info("Discovered organization ID {} from access token claims.", tokenOrgId);
                return tokenOrgId;
            }
            log.debug("No organization ID found in access token claims. Attempting discovery via initial project parent...");
            final String initialProjectId = resolveInitialProjectId();
            if (initialProjectId != null) {
                log.debug("Found initial project ID {}; querying project details to determine organization ID...", initialProjectId);
                final GetProjectResponse initialProject = resourceManagerApi.getProject(initialProjectId, true);
                final String orgId = getOrganizationId(initialProject);
                if (orgId != null && !orgId.isBlank()) {
                    log.info("Discovered organization ID {} via project {} parent hierarchy.", orgId, initialProjectId);
                    return orgId;
                } else {
                    log.warn("Project {} has no organization parent container in hierarchy.", initialProjectId);
                }
            } else {
                log.debug("Cannot discover organization ID: No initial project ID could be resolved.");
            }
        } catch (final Exception e) {
            log.error("Failed to discover organization ID: {}", formatApiException(e), e);
        }
        return null;
    }

    public final List<Project> discoverProjects() {
        final String configuredKeyPath = sdkConfig.getServiceAccountKeyPath().orElse("<not configured>");
        final String saEmail = sdkConfig.getServiceAccountEmail();
        String orgId = sdkConfig.getDiscoveredOrganizationId();
        String initialProjectId = null;

        try {
            if (orgId == null) {
                initialProjectId = resolveInitialProjectId();
                if (initialProjectId != null) {
                    try {
                        final GetProjectResponse initialProject = resourceManagerApi.getProject(initialProjectId, true);
                        orgId = getOrganizationId(initialProject);
                    } catch (final Exception e) {
                        log.warn("Could not query parent hierarchy for initial project {}: {}", initialProjectId, formatApiException(e));
                    }
                }
            }

            if (orgId != null) {
                log.info("Discovering projects recursively under organization container {}...", orgId);
                final Map<String, Project> projectsById = new LinkedHashMap<>();
                final Set<String> visitedContainers = new HashSet<>();
                discoverProjectsRecursively(orgId, projectsById, visitedContainers);
                if (!projectsById.isEmpty()) {
                    log.info("Successfully discovered {} project(s) under organization {}.", projectsById.size(), orgId);
                    return new ArrayList<>(projectsById.values());
                }
                log.warn("Recursive project discovery under organization {} yielded 0 projects. Attempting fallback project discovery...", orgId);
            }

            if (initialProjectId == null) {
                initialProjectId = resolveInitialProjectId();
            }

            if (initialProjectId != null) {
                log.info("Attempting direct discovery of fallback project ID: {}", initialProjectId);
                try {
                    final ListProjectsResponse projectsResponse = resourceManagerApi.listProjects(null, List.of(initialProjectId), null, null, null, null);
                    if (projectsResponse != null && projectsResponse.getItems() != null && !projectsResponse.getItems().isEmpty()) {
                        log.info("Successfully discovered fallback project {} via listProjects.", initialProjectId);
                        return projectsResponse.getItems();
                    }
                } catch (final Exception e) {
                    log.warn("Direct listProjects query for project {} failed: {}", initialProjectId, formatApiException(e));
                }

                try {
                    final GetProjectResponse directProject = resourceManagerApi.getProject(initialProjectId, false);
                    if (directProject != null) {
                        final Project fallbackProject = new Project();
                        fallbackProject.setProjectId(directProject.getProjectId());
                        if (fallbackProject.getProjectId() == null) {
                            try {
                                fallbackProject.setProjectId(UUID.fromString(initialProjectId));
                            } catch (final IllegalArgumentException ignored) {
                            }
                        }
                        fallbackProject.setName(directProject.getName());
                        fallbackProject.setContainerId(directProject.getContainerId());
                        log.info("Successfully discovered single project {} ({}) via getProject fallback.",
                                directProject.getName(), directProject.getProjectId());
                        return List.of(fallbackProject);
                    }
                } catch (final Exception e) {
                    log.warn("Direct getProject query for project {} failed: {}", initialProjectId, formatApiException(e));
                }
            }

            log.error("Unable to query accessible projects from Resource Manager API. " +
                    "Diagnostics: keyPath='{}', serviceAccountEmail='{}', orgId='{}', initialProjectId='{}'. " +
                    "Please check: " +
                    "1) Service account key file is mounted, readable, and valid JSON. " +
                    "2) The service account is assigned appropriate roles in the STACKIT portal " +
                    "(e.g., 'resourcemanager.organization.viewer' for organization-wide discovery, " +
                    "or 'resourcemanager.project.viewer' on the project).",
                    configuredKeyPath, saEmail, orgId, initialProjectId);
            return Collections.emptyList();
        } catch (final Exception e) {
            log.error("Failed to discover STACKIT projects. Diagnostics: keyPath='{}', serviceAccountEmail='{}', orgId='{}', initialProjectId='{}': {}",
                    configuredKeyPath, saEmail, orgId, initialProjectId, formatApiException(e), e);
        }
        return Collections.emptyList();
    }

    private void discoverProjectsRecursively(final String containerId,
                                            final Map<String, Project> projectsById,
                                            final Set<String> visitedContainers) {
        if (containerId == null || !visitedContainers.add(containerId)) {
            return;
        }

        // 1. List projects in this container with paging
        try {
            BigDecimal offset = BigDecimal.ZERO;
            ListProjectsResponse projectsResponse;
            do {
                projectsResponse = resourceManagerApi.listProjects(containerId, null, null, offset, PAGE_SIZE, null);
                if (projectsResponse == null || projectsResponse.getItems() == null || projectsResponse.getItems().isEmpty()) {
                    break;
                }
                for (final Project project : projectsResponse.getItems()) {
                    final String projectIdStr = project.getProjectId() != null ? project.getProjectId().toString() : project.getContainerId();
                    if (projectIdStr != null) {
                        projectsById.putIfAbsent(projectIdStr, project);
                    }
                }

                if (projectsResponse.getItems().size() < PAGE_SIZE.intValue()) {
                    break; // Last page reached
                }
                offset = offset.add(BigDecimal.valueOf(projectsResponse.getItems().size()));
            } while (true);
        } catch (final Exception e) {
            log.warn("Failed to list projects in container {}: {}", containerId, formatApiException(e));
        }

        // 2. List subfolders in this container and traverse recursively with paging
        try {
            BigDecimal folderOffset = BigDecimal.ZERO;
            ListFoldersResponse foldersResponse;
            do {
                foldersResponse = resourceManagerApi.listFolders(containerId, null, null, PAGE_SIZE, folderOffset, null);
                if (foldersResponse == null || foldersResponse.getItems() == null || foldersResponse.getItems().isEmpty()) {
                    break;
                }
                for (final ListFoldersResponseItemsInner folder : foldersResponse.getItems()) {
                    final String subContainerId = folder.getContainerId();
                    if (subContainerId != null) {
                        discoverProjectsRecursively(subContainerId, projectsById, visitedContainers);
                    }
                }

                if (foldersResponse.getItems().size() < PAGE_SIZE.intValue()) {
                    break; // Last page reached
                }
                folderOffset = folderOffset.add(BigDecimal.valueOf(foldersResponse.getItems().size()));
            } while (true);
        } catch (final Exception e) {
            log.warn("Failed to list folders in container {}: {}", containerId, formatApiException(e));
        }
    }

    public final String getOrganizationId(final GetProjectResponse projectResponse) {
        if (projectResponse == null) {
            return null;
        }
        if (projectResponse.getParents() != null) {
            for (final ParentListInner parent : projectResponse.getParents()) {
                if (parent.getType() != null && "ORGANIZATION".equalsIgnoreCase(parent.getType().name())) {
                    return parent.getId() != null ? parent.getId().toString() : parent.getContainerId();
                }
            }
        }
        final Parent directParent = projectResponse.getParent();
        if (directParent != null) {
            return directParent.getId() != null ? directParent.getId().toString() : directParent.getContainerId();
        }
        return null;
    }

    private String resolveInitialProjectId() {
        final String discoveredProjectId = sdkConfig.getDiscoveredProjectId();
        if (discoveredProjectId != null && !discoveredProjectId.isBlank()) {
            return discoveredProjectId;
        }

        // Query accessible projects where the service account is an active member
        final String saEmail = sdkConfig.getServiceAccountEmail();
        if (saEmail != null && !saEmail.isBlank()) {
            try {
                log.debug("Attempting to resolve initial project ID via member query for {}", saEmail);
                final ListProjectsResponse projectsResponse = resourceManagerApi.listProjects(null, null, saEmail, BigDecimal.ZERO, BigDecimal.valueOf(10), null);
                if (projectsResponse != null && projectsResponse.getItems() != null && !projectsResponse.getItems().isEmpty()) {
                    final Project firstProject = projectsResponse.getItems().get(0);
                    final String discoveredMemberProjectId = firstProject.getProjectId() != null ? firstProject.getProjectId().toString() : firstProject.getContainerId();
                    log.info("Discovered initial project ID {} via service account membership query ({})", discoveredMemberProjectId, saEmail);
                    return discoveredMemberProjectId;
                } else {
                    log.debug("No projects found for service account member query ({})", saEmail);
                }
            } catch (final Exception e) {
                log.warn("Failed to query accessible projects for service account {}: {}", saEmail, formatApiException(e));
            }
        }
        return null;
    }

    private String formatApiException(final Exception e) {
        if (e instanceof ApiException apiException) {
            final int code = apiException.getCode();
            final String body = apiException.getResponseBody();
            return String.format("HTTP %d: %s%s",
                    code,
                    apiException.getMessage(),
                    (body != null && !body.isBlank()) ? " | Body: " + body.trim() : "");
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

}
