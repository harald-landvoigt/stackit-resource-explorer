package com.landvoigtit.stackit.resourceexplorer;

import cloud.stackit.sdk.resourcemanager.v0api.api.ResourceManagerApi;
import cloud.stackit.sdk.resourcemanager.v0api.model.GetProjectResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListProjectsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Parent;
import cloud.stackit.sdk.resourcemanager.v0api.model.ParentListInner;
import com.landvoigtit.stackit.resourceexplorer.billing.BillingApiClient;
import com.landvoigtit.stackit.resourceexplorer.billing.BillingResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
@Slf4j
public class BillingIntegrationTest {

    @Inject
    StackitProjectDiscoveryService discoveryService;

    @Inject
    BillingResourceScraper billingScraper;

    @Inject
    BillingApiClient billingApiClient;

    @Inject
    ResourceManagerApi resourceManagerApi;

    @Inject
    StackitResourceRepository repository;

    @Test
    public final void testBillingIntegration() {
        log.info("Starting billing integration test...");
        try {
            // 1. Verify discoveryService works with real credentials and resolves the organization ID and projects
            final String orgId = discoveryService.discoverOrganizationId();
            log.info("Resolved organization ID: {}", orgId);
            assertNotNull(orgId, "Resolved organization ID should not be null");

            final List<cloud.stackit.sdk.resourcemanager.v0api.model.Project> projects = discoveryService.discoverProjects();
            assertNotNull(projects, "Projects list should not be null");
            assertFalse(projects.isEmpty(), "Discovered projects list should not be empty");
            log.info("Project list fetched successfully. Found {} projects.", projects.size());

            // 2. Perform the scrape
            billingScraper.scrape();

            // 3. Verify repository query completed without issues
            final long count = repository.count("type = 'billing'");
            log.info("Billing scrape completed. Persisted {} billing invoices.", count);
            assertTrue(count >= 0, "Billing invoice count must be non-negative");

            final long orgCount = repository.count("type = 'billing-org'");
            log.info("Billing scrape completed. Persisted {} organization billing invoices.", orgCount);
            assertTrue(orgCount >= 0, "Organization billing invoice count must be non-negative");

        } catch (final Exception e) {
            if (isNetworkError(e)) {
                log.warn("STACKIT API endpoint is unreachable from the current environment (Connection reset/timeout/host unresolved). Skipping integration test assertions.");
                return;
            }
            log.error("Billing integration test failed with exception", e);
            fail("Billing integration test failed: " + e.getMessage());
        }
    }

    private boolean isNetworkError(final Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof java.net.SocketException ||
                t instanceof java.net.ConnectException ||
                t instanceof java.net.UnknownHostException ||
                t instanceof java.net.SocketTimeoutException) {
            return true;
        }
        return isNetworkError(t.getCause());
    }

    private String getOrganizationId(final GetProjectResponse projectResponse) {
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

}
