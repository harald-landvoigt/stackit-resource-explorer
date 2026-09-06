package com.landvoigtit.stackit.resourceexplorer.config;

/**
 * Centralized constant definitions for STACKIT API endpoints, resource types, and default configuration values.
 */
public final class StackitConstants {

    private StackitConstants() {
        // Utility class; prevent instantiation
    }

    // Default API Base URLs
    public static final String DEFAULT_AUTHORIZATION_API_URL = "https://authorization.api.stackit.cloud";
    public static final String DEFAULT_SERVICE_ACCOUNT_API_URL = "https://service-account.api.stackit.cloud";
    public static final String DEFAULT_COST_API_URL = "https://cost.api.stackit.cloud";
    public static final String DEFAULT_BILLING_API_URL = DEFAULT_COST_API_URL;

    // API Path Templates
    public static final String IAM_MEMBERS_PATH_TEMPLATE = "/v2/project/%s/members";
    public static final String IAM_SERVICE_ACCOUNTS_PATH_TEMPLATE = "/v2/projects/%s/service-accounts";
    public static final String IAM_SERVICE_ACCOUNT_KEYS_PATH_TEMPLATE = "/v2/projects/%s/service-accounts/%s/keys";
    public static final String IAM_SERVICE_ACCOUNT_TOKENS_PATH_TEMPLATE = "/v2/projects/%s/service-accounts/%s/tokens";
    public static final String COST_PROJECTS_PATH_TEMPLATE = "/v3/costs/%s";
    public static final String BILLING_PROJECT_INVOICES_PATH_TEMPLATE = "/v1/projects/%s/invoices";
    public static final String BILLING_ORG_INVOICES_PATH_TEMPLATE = "/v1/organizations/%s/invoices";

    // Full URL Templates (using default base URLs)
    public static final String IAM_MEMBERS_URL_TEMPLATE = DEFAULT_AUTHORIZATION_API_URL + IAM_MEMBERS_PATH_TEMPLATE;
    public static final String IAM_SERVICE_ACCOUNTS_URL_TEMPLATE = DEFAULT_SERVICE_ACCOUNT_API_URL + IAM_SERVICE_ACCOUNTS_PATH_TEMPLATE;

    // Authentication Schemes & Descriptions
    public static final String AUTH_FLOW_TOKEN_DEPRECATED = "Token Flow (Deprecated)";
    public static final String AUTH_FLOW_KEY_FLOW = "Key Flow";
    public static final String AUTH_FLOW_OIDC = "OIDC / Enterprise SSO";
    public static final String AUTH_FLOW_PLATFORM_MANAGED = "Platform Managed (Internal)";
    public static final String TOKEN_FLOW_DEPRECATED_DESCRIPTION = "The legacy model where a long-lived, static API secret acted directly as a bearer token.";

    // Resource Types
    public static final String RESOURCE_TYPE_COMPUTE = "compute";
    public static final String RESOURCE_TYPE_STORAGE = "storage";
    public static final String RESOURCE_TYPE_NETWORK = "network";
    public static final String RESOURCE_TYPE_NETWORK_VPC = "network-vpc";
    public static final String RESOURCE_TYPE_VMDISKS = "vmdisks";
    public static final String RESOURCE_TYPE_IAM = "iam";
    public static final String RESOURCE_TYPE_BILLING = "billing";
    public static final String RESOURCE_TYPE_BILLING_ORG = "billing-org";

    // Default Regions
    public static final String DEFAULT_REGION = "eu-central-1";
    public static final String ALB_DEFAULT_REGION = "eu01";
    public static final String GLOBAL_REGION = "global";

    // Status Values
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_AVAILABLE = "AVAILABLE";

    // Role & Domain Constants
    public static final String ROLE_SERVICE_ACCOUNT = "service-account";
    public static final String STORAGE_CLASS_STANDARD = "standard";
    public static final String UNKNOWN_PROJECT_ID = "unknown";

    /**
     * Formats the member access URL for a given project ID using the default authorization base URL.
     *
     * @param projectId the project UUID string
     * @return the complete URL to query project members
     */
    public static String formatMembersUrl(final String projectId) {
        return String.format(IAM_MEMBERS_URL_TEMPLATE, projectId);
    }

    /**
     * Formats the member access URL for a given base URL and project ID.
     *
     * @param baseUrl   the authorization API base URL
     * @param projectId the project UUID string
     * @return the complete URL to query project members
     */
    public static String formatMembersUrl(final String baseUrl, final String projectId) {
        final String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_AUTHORIZATION_API_URL;
        return base + String.format(IAM_MEMBERS_PATH_TEMPLATE, projectId);
    }

    /**
     * Formats the service accounts URL for a given project ID using the default service account base URL.
     *
     * @param projectId the project UUID string
     * @return the complete URL to query project service accounts
     */
    public static String formatServiceAccountsUrl(final String projectId) {
        return String.format(IAM_SERVICE_ACCOUNTS_URL_TEMPLATE, projectId);
    }

    /**
     * Formats the service accounts URL for a given base URL and project ID.
     *
     * @param baseUrl   the service accounts API base URL
     * @param projectId the project UUID string
     * @return the complete URL to query project service accounts
     */
    public static String formatServiceAccountsUrl(final String baseUrl, final String projectId) {
        final String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_SERVICE_ACCOUNT_API_URL;
        return base + String.format(IAM_SERVICE_ACCOUNTS_PATH_TEMPLATE, projectId);
    }

    public static String formatServiceAccountKeysUrl(final String baseUrl, final String projectId, final String serviceAccountId) {
        final String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_SERVICE_ACCOUNT_API_URL;
        return base + String.format(IAM_SERVICE_ACCOUNT_KEYS_PATH_TEMPLATE, projectId, serviceAccountId);
    }

    public static String formatServiceAccountTokensUrl(final String baseUrl, final String projectId, final String serviceAccountId) {
        final String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_SERVICE_ACCOUNT_API_URL;
        return base + String.format(IAM_SERVICE_ACCOUNT_TOKENS_PATH_TEMPLATE, projectId, serviceAccountId);
    }

    /**
     * Formats the relative endpoint path to fetch invoices for a given project ID.
     *
     * @param projectId the project UUID string
     * @return the endpoint path
     */
    public static String formatProjectInvoicesPath(final String projectId) {
        return String.format(BILLING_PROJECT_INVOICES_PATH_TEMPLATE, projectId);
    }

    /**
     * Formats the relative endpoint path to fetch invoices for a given organization ID.
     *
     * @param orgId the organization UUID string
     * @return the endpoint path
     */
    public static String formatOrgInvoicesPath(final String orgId) {
        return String.format(BILLING_ORG_INVOICES_PATH_TEMPLATE, orgId);
    }

    /**
     * Formats the complete Cost API URL to fetch project costs for a given customer account ID and period.
     *
     * @param baseUrl           the cost API base URL
     * @param customerAccountId the customer account / organization UUID string
     * @param fromDate          inclusive start date (YYYY-MM-DD)
     * @param toDate            inclusive end date (YYYY-MM-DD)
     * @return the complete URL to query project costs
     */
    public static String formatCostsUrl(final String baseUrl, final String customerAccountId, final String fromDate, final String toDate) {
        final String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : DEFAULT_COST_API_URL;
        return String.format("%s/v3/costs/%s?from=%s&to=%s&granularity=daily&includeZeroCosts=true", base, customerAccountId, fromDate, toDate);
    }

    /**
     * Formats the Cost API URL using the default base URL.
     *
     * @param customerAccountId the customer account / organization UUID string
     * @param fromDate          inclusive start date (YYYY-MM-DD)
     * @param toDate            inclusive end date (YYYY-MM-DD)
     * @return the complete URL to query project costs
     */
    public static String formatCostsUrl(final String customerAccountId, final String fromDate, final String toDate) {
        return formatCostsUrl(DEFAULT_COST_API_URL, customerAccountId, fromDate, toDate);
    }
}
