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
    }

    @Test
    public void testCustomConfigUrls() {
        final StackitSdkConfig config = new StackitSdkConfig();
        config.billingApiUrl = "https://custom-billing.local";
        config.authorizationApiUrl = "https://custom-auth.local";
        config.serviceAccountApiUrl = "https://custom-sa.local";

        assertEquals("https://custom-billing.local", config.getBillingApiUrl());
        assertEquals("https://custom-auth.local", config.getAuthorizationApiUrl());
        assertEquals("https://custom-sa.local", config.getServiceAccountApiUrl());

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
}
