package com.landvoigtit.stackit.resourceexplorer;

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
                return tokenOrgId;
            }
            final String initialProjectId = resolveInitialProjectId();
            if (initialProjectId != null) {
                final GetProjectResponse initialProject = resourceManagerApi.getProject(initialProjectId, true);
                return getOrganizationId(initialProject);
            }
        } catch (final Exception e) {
            log.error("Failed to discover organization ID", e);
        }
        return null;
    }

    public final List<Project> discoverProjects() {
        try {
            String orgId = sdkConfig.getDiscoveredOrganizationId();
            if (orgId == null) {
                final String initialProjectId = resolveInitialProjectId();
                if (initialProjectId != null) {
                    final GetProjectResponse initialProject = resourceManagerApi.getProject(initialProjectId, true);
                    orgId = getOrganizationId(initialProject);
                }
            }

            if (orgId != null) {
                final Map<String, Project> projectsById = new LinkedHashMap<>();
                final Set<String> visitedContainers = new HashSet<>();
                discoverProjectsRecursively(orgId, projectsById, visitedContainers);
                if (!projectsById.isEmpty()) {
                    return new ArrayList<>(projectsById.values());
                }
            }

            final String fallbackProjectId = resolveInitialProjectId();
            if (fallbackProjectId != null) {
                final ListProjectsResponse projectsResponse = resourceManagerApi.listProjects(null, List.of(fallbackProjectId), null, null, null, null);
                return (projectsResponse != null && projectsResponse.getItems() != null) ? projectsResponse.getItems() : Collections.emptyList();
            }

            log.error("Unable to query accessible projects from Resource Manager API.");
            return Collections.emptyList();
        } catch (final Exception e) {
            log.error("Failed to discover STACKIT projects", e);
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
        // In DefaultApi: listProjects(containerParentId, containerIds, member, offset, limit, creationTimeStart)
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
            log.warn("Failed to list projects in container {}: {}", containerId, e.getMessage());
        }

        // 2. List subfolders in this container and traverse recursively with paging
        // In DefaultApi: listFolders(containerParentId, containerIds, member, limit, offset, creationTimeStart)
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
            log.warn("Failed to list folders in container {}: {}", containerId, e.getMessage());
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
                final ListProjectsResponse projectsResponse = resourceManagerApi.listProjects(null, null, saEmail, BigDecimal.ZERO, BigDecimal.valueOf(10), null);
                if (projectsResponse != null && projectsResponse.getItems() != null && !projectsResponse.getItems().isEmpty()) {
                    final Project firstProject = projectsResponse.getItems().get(0);
                    final String discoveredMemberProjectId = firstProject.getProjectId() != null ? firstProject.getProjectId().toString() : firstProject.getContainerId();
                    log.info("Discovered initial project ID {} via service account membership query ({})", discoveredMemberProjectId, saEmail);
                    return discoveredMemberProjectId;
                }
            } catch (final Exception e) {
                log.warn("Failed to query accessible projects for service account {}: {}", saEmail, e.getMessage());
            }
        }
        return null;
    }

}
