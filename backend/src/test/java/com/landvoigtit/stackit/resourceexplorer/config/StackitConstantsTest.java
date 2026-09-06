package com.landvoigtit.stackit.resourceexplorer.config;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

public class StackitConstantsTest {

    @Test
    public void testPrivateConstructor() throws Exception {
        final Constructor<StackitConstants> constructor = StackitConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        final StackitConstants instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    public void testApiUrlsAndTemplates() {
        assertEquals("https://authorization.api.stackit.cloud", StackitConstants.DEFAULT_AUTHORIZATION_API_URL);
        assertEquals("https://service-account.api.stackit.cloud", StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL);
        assertEquals("https://cost.api.stackit.cloud", StackitConstants.DEFAULT_COST_API_URL);
        assertEquals("https://cost.api.stackit.cloud", StackitConstants.DEFAULT_BILLING_API_URL);

        assertEquals("https://authorization.api.stackit.cloud/v2/project/p-123/members",
                StackitConstants.formatMembersUrl("p-123"));
        assertEquals("https://custom-auth.local/v2/project/p-123/members",
                StackitConstants.formatMembersUrl("https://custom-auth.local", "p-123"));
        assertEquals("https://authorization.api.stackit.cloud/v2/project/p-123/members",
                StackitConstants.formatMembersUrl(null, "p-123"));

        assertEquals("https://service-account.api.stackit.cloud/v2/projects/p-123/service-accounts",
                StackitConstants.formatServiceAccountsUrl("p-123"));
        assertEquals("https://custom-sa.local/v2/projects/p-123/service-accounts",
                StackitConstants.formatServiceAccountsUrl("https://custom-sa.local", "p-123"));
        assertEquals("https://service-account.api.stackit.cloud/v2/projects/p-123/service-accounts",
                StackitConstants.formatServiceAccountsUrl(null, "p-123"));

        assertEquals("/v1/projects/p-123/invoices", StackitConstants.formatProjectInvoicesPath("p-123"));
        assertEquals("/v1/organizations/org-123/invoices", StackitConstants.formatOrgInvoicesPath("org-123"));

        assertEquals("https://cost.api.stackit.cloud/v3/costs/org-123?from=2026-09-01&to=2026-09-30&granularity=daily&includeZeroCosts=true",
                StackitConstants.formatCostsUrl("org-123", "2026-09-01", "2026-09-30"));
        assertEquals("https://custom-cost.local/v3/costs/org-123?from=2026-09-01&to=2026-09-30&granularity=daily&includeZeroCosts=true",
                StackitConstants.formatCostsUrl("https://custom-cost.local", "org-123", "2026-09-01", "2026-09-30"));
    }

    @Test
    public void testResourceTypesAndRegions() {
        assertEquals("compute", StackitConstants.RESOURCE_TYPE_COMPUTE);
        assertEquals("storage", StackitConstants.RESOURCE_TYPE_STORAGE);
        assertEquals("network", StackitConstants.RESOURCE_TYPE_NETWORK);
        assertEquals("iam", StackitConstants.RESOURCE_TYPE_IAM);
        assertEquals("billing", StackitConstants.RESOURCE_TYPE_BILLING);
        assertEquals("billing-org", StackitConstants.RESOURCE_TYPE_BILLING_ORG);

        assertEquals("eu-central-1", StackitConstants.DEFAULT_REGION);
        assertEquals("global", StackitConstants.GLOBAL_REGION);
        assertEquals("ACTIVE", StackitConstants.STATUS_ACTIVE);
        assertEquals("AVAILABLE", StackitConstants.STATUS_AVAILABLE);
        assertEquals("service-account", StackitConstants.ROLE_SERVICE_ACCOUNT);
        assertEquals("standard", StackitConstants.STORAGE_CLASS_STANDARD);
        assertEquals("unknown", StackitConstants.UNKNOWN_PROJECT_ID);
    }
}
