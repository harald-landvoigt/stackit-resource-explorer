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
        assertEquals("https://dns.api.stackit.cloud", StackitConstants.DEFAULT_DNS_API_URL);

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

        assertEquals("/v1/projects/p-123/zones", StackitConstants.formatDnsZonesPath("p-123"));
        assertEquals("/v1/projects/p-123/zones/z-456/rrsets", StackitConstants.formatDnsRecordSetsPath("p-123", "z-456"));
        assertEquals("https://dns.api.stackit.cloud/v1/projects/p-123/zones", StackitConstants.formatDnsZonesUrl("p-123"));
        assertEquals("https://custom-dns.local/v1/projects/p-123/zones", StackitConstants.formatDnsZonesUrl("https://custom-dns.local", "p-123"));
        assertEquals("https://dns.api.stackit.cloud/v1/projects/p-123/zones/z-456/rrsets", StackitConstants.formatDnsRecordSetsUrl("p-123", "z-456"));
        assertEquals("https://custom-dns.local/v1/projects/p-123/zones/z-456/rrsets", StackitConstants.formatDnsRecordSetsUrl("https://custom-dns.local", "p-123", "z-456"));

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
        assertEquals("dns-zone", StackitConstants.RESOURCE_TYPE_DNS_ZONE);
        assertEquals("dns-record-set", StackitConstants.RESOURCE_TYPE_DNS_RECORD_SET);

        assertEquals("eu-central-1", StackitConstants.DEFAULT_REGION);
        assertEquals("global", StackitConstants.GLOBAL_REGION);
        assertEquals("eu01", StackitConstants.REGION_EU01);
        assertEquals("eu02", StackitConstants.REGION_EU02);
        assertEquals(java.util.List.of("eu01", "eu02"), StackitConstants.DEFAULT_REGIONS);
        assertEquals("ACTIVE", StackitConstants.STATUS_ACTIVE);
        assertEquals("AVAILABLE", StackitConstants.STATUS_AVAILABLE);
        assertEquals("service-account", StackitConstants.ROLE_SERVICE_ACCOUNT);
        assertEquals("standard", StackitConstants.STORAGE_CLASS_STANDARD);
        assertEquals("unknown", StackitConstants.UNKNOWN_PROJECT_ID);
    }

    @Test
    public void testIsServiceDisabled() {
        assertFalse(StackitConstants.isServiceDisabled((String) null));
        assertFalse(StackitConstants.isServiceDisabled(""));
        assertFalse(StackitConstants.isServiceDisabled("   "));
        assertFalse(StackitConstants.isServiceDisabled("500 Internal Server Error"));
        assertFalse(StackitConstants.isServiceDisabled("403 Forbidden: User lacks role 'loadbalancer.admin'"));

        assertTrue(StackitConstants.isServiceDisabled("Service not enabled"));
        assertTrue(StackitConstants.isServiceDisabled("servicenotenabled"));
        assertTrue(StackitConstants.isServiceDisabled("The service is not enabled for region eu02"));
        assertTrue(StackitConstants.isServiceDisabled("project.not_found"));
        assertTrue(StackitConstants.isServiceDisabled("HTTP 404 Not Found"));
        assertTrue(StackitConstants.isServiceDisabled("status 404"));
        assertTrue(StackitConstants.isServiceDisabled("403 Forbidden: {\"message\":\"Service not enabled\"}"));
        assertTrue(StackitConstants.isServiceDisabled("HTTP 403: {\"error\":\"service not enabled\"}"));

        // Throwable overload
        assertFalse(StackitConstants.isServiceDisabled((Throwable) null));
        assertTrue(StackitConstants.isServiceDisabled(new RuntimeException("Service not enabled")));
        assertTrue(StackitConstants.isServiceDisabled(new RuntimeException("Wrapped", new IllegalStateException("403 Forbidden: Service not enabled"))));
    }

    @Test
    public void testIsPermissionIssueWithServiceDisabledPrecedence() {
        assertFalse(StackitConstants.isPermissionIssue((String) null));
        assertFalse(StackitConstants.isPermissionIssue(""));
        assertFalse(StackitConstants.isPermissionIssue("HTTP 404 Not Found"));

        // True permission issues
        assertTrue(StackitConstants.isPermissionIssue("403 Forbidden: User lacks permissions"));
        assertTrue(StackitConstants.isPermissionIssue("401 Unauthorized"));
        assertTrue(StackitConstants.isPermissionIssue("AccessDenied: User does not have access"));
        assertTrue(StackitConstants.isPermissionIssue("permission denied accessing resource"));

        // Disabled service should NOT be flagged as permission issue
        assertFalse(StackitConstants.isPermissionIssue("403 Forbidden: {\"message\":\"Service not enabled\"}"));
        assertFalse(StackitConstants.isPermissionIssue("HTTP 403: service not enabled"));
        assertFalse(StackitConstants.isPermissionIssue("404 Not Found"));
        assertFalse(StackitConstants.isPermissionIssue("project.not_found"));

        // Throwable overload
        assertFalse(StackitConstants.isPermissionIssue((Throwable) null));
        assertTrue(StackitConstants.isPermissionIssue(new RuntimeException("403 Forbidden: User lacks permissions")));
        assertFalse(StackitConstants.isPermissionIssue(new RuntimeException("403 Forbidden: Service not enabled")));
    }

    @Test
    public void testCleanErrorMessage() {
        assertEquals("", StackitConstants.cleanErrorMessage((String) null));
        assertEquals("", StackitConstants.cleanErrorMessage(""));
        assertEquals("Resource not found", StackitConstants.cleanErrorMessage("Resource not found"));

        final String rawApiExceptionMsg = "Message: 404 Not Found\n" +
                "HTTP response code: 404\n" +
                "HTTP response body: {\"error\":\"Service disabled in region eu02\"}\n" +
                "HTTP response headers: {date=[Tue, 29 Sep 2026 12:00:00 GMT], server=[istio-envoy], x-envoy-upstream-service-time=[12]}";

        final String cleaned = StackitConstants.cleanErrorMessage(rawApiExceptionMsg);
        assertEquals("HTTP 404: {\"error\":\"Service disabled in region eu02\"}", cleaned);
        assertFalse(cleaned.contains("istio-envoy"));
        assertFalse(cleaned.contains("HTTP response headers"));

        // Null response body
        final String rawMsgNoBody = "Message: 404 Not Found\n" +
                "HTTP response code: 404\n" +
                "HTTP response body: null\n" +
                "HTTP response headers: {date=[Tue, 29 Sep 2026 12:00:00 GMT], server=[istio-envoy]}";
        assertEquals("HTTP 404: 404 Not Found", StackitConstants.cleanErrorMessage(rawMsgNoBody));

        // Throwable overload
        assertEquals("", StackitConstants.cleanErrorMessage((Throwable) null));
        assertEquals("Network timeout", StackitConstants.cleanErrorMessage(new RuntimeException("Network timeout")));
        final cloud.stackit.sdk.core.exception.ApiException apiEx = new cloud.stackit.sdk.core.exception.ApiException(404, "404 Not Found");
        assertTrue(StackitConstants.cleanErrorMessage(apiEx).contains("404"));
        assertFalse(StackitConstants.cleanErrorMessage(apiEx).contains("HTTP response headers"));
    }
}
