package com.landvoigtit.stackit.resourceexplorer.config;

import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class StackitSdkConfigTest {

    @Test
    public void testDefaultConfigUrls() {
        final StackitSdkConfig config = new StackitSdkConfig();
        assertEquals(StackitConstants.DEFAULT_BILLING_API_URL, config.getBillingApiUrl());
        assertEquals(StackitConstants.DEFAULT_AUTHORIZATION_API_URL, config.getAuthorizationApiUrl());
        assertEquals(StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL, config.getServiceAccountApiUrl());

        assertEquals("https://authorization.api.stackit.cloud/v2/project/proj-1/members", config.getMembersUrl("proj-1"));
        assertEquals("https://service-account.api.stackit.cloud/v2/projects/proj-1/service-accounts", config.getServiceAccountsUrl("proj-1"));
        assertEquals("https://postgres-flex-service.api.stackit.cloud", config.getPostgresFlexApiUrl());
        assertEquals("https://mongodb-flex-service.api.stackit.cloud", config.getMongodbFlexApiUrl());
        assertEquals("https://opensearch.api.stackit.cloud", config.getOpensearchApiUrl());
        assertEquals("https://mariadb.api.stackit.cloud", config.getMariadbApiUrl());
        assertEquals("https://mssql-flex-service.api.stackit.cloud", config.getSqlserverFlexApiUrl());
    }

    @Test
    public void testCustomConfigUrls() {
        final StackitSdkConfig config = new StackitSdkConfig();
        config.billingApiUrl = "https://custom-billing.local";
        config.authorizationApiUrl = "https://custom-auth.local";
        config.serviceAccountApiUrl = "https://custom-sa.local";
        config.postgresFlexApiUrl = "https://custom-postgres.local";
        config.mongodbFlexApiUrl = "https://custom-mongodb.local";
        config.opensearchApiUrl = "https://custom-opensearch.local";
        config.mariadbApiUrl = "https://custom-mariadb.local";
        config.sqlserverFlexApiUrl = "https://custom-sqlserver.local";

        assertEquals("https://custom-billing.local", config.getBillingApiUrl());
        assertEquals("https://custom-auth.local", config.getAuthorizationApiUrl());
        assertEquals("https://custom-sa.local", config.getServiceAccountApiUrl());
        assertEquals("https://custom-postgres.local", config.getPostgresFlexApiUrl());
        assertEquals("https://custom-mongodb.local", config.getMongodbFlexApiUrl());
        assertEquals("https://custom-opensearch.local", config.getOpensearchApiUrl());
        assertEquals("https://custom-mariadb.local", config.getMariadbApiUrl());
        assertEquals("https://custom-sqlserver.local", config.getSqlserverFlexApiUrl());

        assertEquals("https://custom-auth.local/v2/project/proj-1/members", config.getMembersUrl("proj-1"));
        assertEquals("https://custom-sa.local/v2/projects/proj-1/service-accounts", config.getServiceAccountsUrl("proj-1"));
    }

    @Test
    public void testDiscoveredOrgAndProjectWhenNoKeyPath() {
        final StackitSdkConfig config = new StackitSdkConfig();
        config.serviceAccountKeyPath = Optional.empty();
        assertNull(config.getDiscoveredOrganizationId());
        assertNull(config.getDiscoveredProjectId());
    }

    @Test
    public void testRegionsConfiguration() {
        final StackitSdkConfig config = new StackitSdkConfig();
        assertEquals(StackitConstants.DEFAULT_REGIONS, config.getRegions());

        config.regions = java.util.List.of("eu01", "eu02", "eu03");
        assertEquals(java.util.List.of("eu01", "eu02", "eu03"), config.getRegions());

        config.regions = java.util.List.of(" EU01 ", "eu02", "EU01");
        assertEquals(java.util.List.of("eu01", "eu02"), config.getRegions());
    }

    @Test
    public void testDiscoveryCacheTtl() {
        final StackitSdkConfig config = new StackitSdkConfig();
        assertEquals(java.time.Duration.ofMinutes(10), config.getDiscoveryCacheTtl());

        config.discoveryCacheTtl = java.time.Duration.ofMinutes(5);
        assertEquals(java.time.Duration.ofMinutes(5), config.getDiscoveryCacheTtl());
    }
}
