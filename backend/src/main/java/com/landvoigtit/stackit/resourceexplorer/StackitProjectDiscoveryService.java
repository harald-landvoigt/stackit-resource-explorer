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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
@Slf4j
public class StackitProjectDiscoveryService {

    private static final BigDecimal PAGE_SIZE = BigDecimal.valueOf(50);

    private final StackitSdkConfig sdkConfig;
    private final ResourceManagerApi resourceManagerApi;

    private final AtomicReference<String> cachedOrganizationId = new AtomicReference<>();
    private final Object orgLock = new Object();

    public record DiscoveredProjectsSnapshot(
            List<Project> projects,
            Map<String, String> projectNames,
            Instant cachedAt
    ) {
        public boolean isValid(final Duration ttl) {
            if (projects == null || cachedAt == null) {
                return false;
            }
            final Duration effectiveTtl = ttl != null ? ttl : Duration.ofMinutes(10);
            return Duration.between(cachedAt, Instant.now()).compareTo(effectiveTtl) < 0;
        }
    }

    private final AtomicReference<DiscoveredProjectsSnapshot> projectsSnapshot = new AtomicReference<>();
    private final Object discoveryLock = new Object();

    @Inject
    public StackitProjectDiscoveryService(final StackitSdkConfig sdkConfig, final ResourceManagerApi resourceManagerApi) {
        this.sdkConfig = sdkConfig;
        this.resourceManagerApi = resourceManagerApi;
    }

    public final String discoverOrganizationId() {
        final String cachedOrg = cachedOrganizationId.get();
        if (cachedOrg != null && !cachedOrg.isBlank()) {
            return cachedOrg;
        }
        synchronized (orgLock) {
            final String existing = cachedOrganizationId.get();
            if (existing != null && !existing.isBlank()) {
                return existing;
            }
            final String discovered = resolveOrganizationId();
            if (discovered != null) {
                cachedOrganizationId.set(discovered);
            }
            return discovered;
        }
    }

    private String resolveOrganizationId() {
        try {
            final String tokenOrgId = sdkConfig.getDiscoveredOrganizationId();
            if (tokenOrgId != null && !tokenOrgId.isBlank()) {
                log.info("Discovered organization ID {} from access token claims.", tokenOrgId);
                return tokenOrgId;
            }
            log.info("No organization ID found in access token claims. Attempting discovery via initial project parent...");
            return discoverOrgIdViaInitialProject();
        } catch (final Exception e) {
            log.error("Failed to discover organization ID: {}", formatApiException(e), e);
            return null;
        }
    }

    private String discoverOrgIdViaInitialProject() throws ApiException {
        final String initialProjectId = resolveInitialProjectId();
        if (initialProjectId == null) {
            log.info("Cannot discover organization ID: No initial project ID could be resolved.");
            return null;
        }
        log.info("Found initial project ID {}; querying project details to determine organization ID...", initialProjectId);
        final GetProjectResponse initialProject = resourceManagerApi.getProject(initialProjectId, true);
        final String orgId = getOrganizationId(initialProject);
        if (orgId != null && !orgId.isBlank()) {
            log.info("Discovered organization ID {} via project {} parent hierarchy.", orgId, initialProjectId);
            return orgId;
        }
        log.warn("Project {} has no organization parent container in hierarchy.", initialProjectId);
        return null;
    }

    public List<Project> discoverProjects() {
        return discoverProjects(false);
    }

    public List<Project> discoverProjects(final boolean forceRefresh) {
        final Duration ttl = sdkConfig != null ? sdkConfig.getDiscoveryCacheTtl() : Duration.ofMinutes(10);
        final DiscoveredProjectsSnapshot snapshot = projectsSnapshot.get();
        if (!forceRefresh && snapshot != null && snapshot.isValid(ttl)) {
            return snapshot.projects();
        }

        synchronized (discoveryLock) {
            final DiscoveredProjectsSnapshot currentSnapshot = projectsSnapshot.get();
            if (!forceRefresh && currentSnapshot != null && currentSnapshot.isValid(ttl)) {
                return currentSnapshot.projects();
            }

            final List<Project> discovered = fetchProjectsRemotely();
            if (discovered != null && !discovered.isEmpty()) {
                final List<Project> unmodifiableProjects = Collections.unmodifiableList(discovered);
                final DiscoveredProjectsSnapshot newSnapshot = new DiscoveredProjectsSnapshot(
                        unmodifiableProjects,
                        buildProjectNamesMap(discovered),
                        Instant.now()
                );
                projectsSnapshot.set(newSnapshot);
                return unmodifiableProjects;
            } else if (currentSnapshot != null && currentSnapshot.projects() != null) {
                return currentSnapshot.projects();
            }
            return discovered != null ? discovered : Collections.emptyList();
        }
    }

    private Map<String, String> buildProjectNamesMap(final List<Project> projects) {
        final Map<String, String> namesMap = new LinkedHashMap<>();
        for (final Project p : projects) {
            final String pid = resolveProjectId(p);
            if (pid != null && p.getName() != null && !p.getName().isBlank()) {
                namesMap.put(pid, p.getName());
            }
        }
        return Collections.unmodifiableMap(namesMap);
    }

    private String resolveProjectId(final Project project) {
        if (project.getProjectId() != null) {
            return project.getProjectId().toString();
        }
        return project.getContainerId();
    }

    public Map<String, String> getProjectNamesMap() {
        final Duration ttl = sdkConfig != null ? sdkConfig.getDiscoveryCacheTtl() : Duration.ofMinutes(10);
        final DiscoveredProjectsSnapshot snapshot = projectsSnapshot.get();
        if (snapshot != null && snapshot.isValid(ttl) && snapshot.projectNames() != null) {
            return snapshot.projectNames();
        }
        discoverProjects(false);
        final DiscoveredProjectsSnapshot updated = projectsSnapshot.get();
        return updated != null && updated.projectNames() != null ? updated.projectNames() : Collections.emptyMap();
    }

    private List<Project> fetchProjectsRemotely() {
        final String configuredKeyPath = sdkConfig.getServiceAccountKeyPath().orElse("<not configured>");
        final String saEmail = sdkConfig.getServiceAccountEmail();
        final String orgId = discoverOrganizationId();

        try {
            if (orgId != null) {
                final List<Project> orgProjects = discoverProjectsUnderOrganization(orgId);
                if (!orgProjects.isEmpty()) {
                    return orgProjects;
                }
            }

            final String initialProjectId = resolveInitialProjectId();
            if (initialProjectId != null) {
                final List<Project> fallbackProjects = discoverFallbackProjects(initialProjectId);
                if (!fallbackProjects.isEmpty()) {
                    return fallbackProjects;
                }
            }

            logFallbackDiagnostics(configuredKeyPath, saEmail, orgId, initialProjectId);
            return Collections.emptyList();
        } catch (final Exception e) {
            log.error("Failed to discover STACKIT projects. Diagnostics: keyPath='{}', serviceAccountEmail='{}', orgId='{}': {}",
                    configuredKeyPath, saEmail, orgId, formatApiException(e), e);
            return Collections.emptyList();
        }
    }

    private List<Project> discoverProjectsUnderOrganization(final String orgId) {
        log.info("Discovering projects recursively under organization container {}...", orgId);
        final Map<String, Project> projectsById = new LinkedHashMap<>();
        final Set<String> visitedContainers = new HashSet<>();
        discoverProjectsRecursively(orgId, projectsById, visitedContainers);
        if (!projectsById.isEmpty()) {
            log.info("Successfully discovered {} project(s) under organization {}.", projectsById.size(), orgId);
            return new ArrayList<>(projectsById.values());
        }
        log.warn("Recursive project discovery under organization {} yielded 0 projects. Attempting fallback project discovery...", orgId);
        return Collections.emptyList();
    }

    private List<Project> discoverFallbackProjects(final String initialProjectId) {
        log.info("Attempting direct discovery of fallback project ID: {}", initialProjectId);
        final List<Project> listProjectsFallback = queryFallbackListProjects(initialProjectId);
        if (!listProjectsFallback.isEmpty()) {
            return listProjectsFallback;
        }
        return queryFallbackGetProject(initialProjectId);
    }

    private List<Project> queryFallbackListProjects(final String initialProjectId) {
        try {
            final ListProjectsResponse projectsResponse = resourceManagerApi.listProjects(null, List.of(initialProjectId), null, null, null, null);
            if (projectsResponse != null && projectsResponse.getItems() != null && !projectsResponse.getItems().isEmpty()) {
                log.info("Successfully discovered fallback project {} via listProjects.", initialProjectId);
                return projectsResponse.getItems();
            }
        } catch (final Exception e) {
            log.warn("Direct listProjects query for project {} failed: {}", initialProjectId, formatApiException(e));
        }
        return Collections.emptyList();
    }

    private List<Project> queryFallbackGetProject(final String initialProjectId) {
        try {
            final GetProjectResponse directProject = resourceManagerApi.getProject(initialProjectId, false);
            if (directProject != null) {
                final Project fallbackProject = new Project();
                fallbackProject.setProjectId(resolveProjectUuid(directProject.getProjectId(), initialProjectId));
                fallbackProject.setName(directProject.getName());
                fallbackProject.setContainerId(directProject.getContainerId());
                log.info("Successfully discovered single project {} ({}) via getProject fallback.",
                        directProject.getName(), directProject.getProjectId());
                return List.of(fallbackProject);
            }
        } catch (final Exception e) {
            log.warn("Direct getProject query for project {} failed: {}", initialProjectId, formatApiException(e));
        }
        return Collections.emptyList();
    }

    private UUID resolveProjectUuid(final UUID currentId, final String fallbackIdStr) {
        if (currentId != null) {
            return currentId;
        }
        try {
            return UUID.fromString(fallbackIdStr);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private void logFallbackDiagnostics(final String configuredKeyPath, final String saEmail, final String orgId, final String initialProjectId) {
        log.error("Unable to query accessible projects from Resource Manager API. " +
                "Diagnostics: keyPath='{}', serviceAccountEmail='{}', orgId='{}', initialProjectId='{}'. " +
                "Please check: " +
                "1) Service account key file is mounted, readable, and valid JSON. " +
                "2) The service account is assigned appropriate roles in the STACKIT portal " +
                "(e.g., 'resourcemanager.organization.viewer' for organization-wide discovery, " +
                "or 'resourcemanager.project.viewer' on the project).",
                configuredKeyPath, saEmail, orgId, initialProjectId);
    }

    private void discoverProjectsRecursively(final String containerId,
                                            final Map<String, Project> projectsById,
                                            final Set<String> visitedContainers) {
        if (containerId == null || !visitedContainers.add(containerId)) {
            return;
        }
        fetchProjectsInContainer(containerId, projectsById);
        traverseSubfolders(containerId, projectsById, visitedContainers);
    }

    private void fetchProjectsInContainer(final String containerId, final Map<String, Project> projectsById) {
        try {
            BigDecimal offset = BigDecimal.ZERO;
            do {
                final ListProjectsResponse projectsResponse = resourceManagerApi.listProjects(containerId, null, null, offset, PAGE_SIZE, null);
                if (projectsResponse == null || projectsResponse.getItems() == null || projectsResponse.getItems().isEmpty()) {
                    break;
                }
                for (final Project project : projectsResponse.getItems()) {
                    final String projectIdStr = resolveProjectId(project);
                    if (projectIdStr != null) {
                        projectsById.putIfAbsent(projectIdStr, project);
                    }
                }
                if (projectsResponse.getItems().size() < PAGE_SIZE.intValue()) {
                    break;
                }
                offset = offset.add(BigDecimal.valueOf(projectsResponse.getItems().size()));
            } while (true);
        } catch (final Exception e) {
            log.warn("Failed to list projects in container {}: {}", containerId, formatApiException(e));
        }
    }

    private void traverseSubfolders(final String containerId,
                                    final Map<String, Project> projectsById,
                                    final Set<String> visitedContainers) {
        try {
            BigDecimal folderOffset = BigDecimal.ZERO;
            do {
                final ListFoldersResponse foldersResponse = resourceManagerApi.listFolders(containerId, null, null, PAGE_SIZE, folderOffset, null);
                if (foldersResponse == null || foldersResponse.getItems() == null || foldersResponse.getItems().isEmpty()) {
                    break;
                }
                for (final ListFoldersResponseItemsInner folder : foldersResponse.getItems()) {
                    if (folder.getContainerId() != null) {
                        discoverProjectsRecursively(folder.getContainerId(), projectsById, visitedContainers);
                    }
                }
                if (foldersResponse.getItems().size() < PAGE_SIZE.intValue()) {
                    break;
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
                    return resolveContainerOrId(parent.getId(), parent.getContainerId());
                }
            }
        }
        if (projectResponse.getParent() != null) {
            return resolveContainerOrId(projectResponse.getParent().getId(), projectResponse.getParent().getContainerId());
        }
        return null;
    }

    private String resolveContainerOrId(final UUID id, final String containerId) {
        if (id != null) {
            return id.toString();
        }
        return containerId;
    }

    private String resolveInitialProjectId() {
        final String discoveredProjectId = sdkConfig.getDiscoveredProjectId();
        if (discoveredProjectId != null && !discoveredProjectId.isBlank()) {
            return discoveredProjectId;
        }

        final String saEmail = sdkConfig.getServiceAccountEmail();
        if (saEmail != null && !saEmail.isBlank()) {
            try {
                log.info("Attempting to resolve initial project ID via member query for {}", saEmail);
                final ListProjectsResponse projectsResponse = resourceManagerApi.listProjects(null, null, saEmail, BigDecimal.ZERO, BigDecimal.valueOf(10), null);
                if (projectsResponse != null && projectsResponse.getItems() != null && !projectsResponse.getItems().isEmpty()) {
                    final Project firstProject = projectsResponse.getItems().get(0);
                    final String discoveredMemberProjectId = resolveProjectId(firstProject);
                    log.info("Discovered initial project ID {} via service account membership query ({})", discoveredMemberProjectId, saEmail);
                    return discoveredMemberProjectId;
                } else {
                    log.info("No projects found for service account member query ({})", saEmail);
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
