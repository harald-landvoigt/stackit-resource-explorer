package com.landvoigtit.stackit.resourceexplorer.config;

import cloud.stackit.sdk.core.KeyFlowAuthenticator;
import cloud.stackit.sdk.core.config.CoreConfiguration;
import cloud.stackit.sdk.iaas.v1api.api.IaasApi;
import cloud.stackit.sdk.alb.v2api.api.AlbApi;
import cloud.stackit.sdk.objectstorage.v1api.api.ObjectStorageApi;
import cloud.stackit.sdk.resourcemanager.v0api.api.ResourceManagerApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import okhttp3.OkHttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.landvoigtit.stackit.resourceexplorer.billing.BillingApiClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class StackitSdkConfig {

    @ConfigProperty(name = "stackit.sdk.service-account-key-path")
    Optional<String> serviceAccountKeyPath;

    @ConfigProperty(name = "stackit.billing.api-url", defaultValue = StackitConstants.DEFAULT_BILLING_API_URL)
    String billingApiUrl;

    @ConfigProperty(name = "stackit.authorization.api-url", defaultValue = StackitConstants.DEFAULT_AUTHORIZATION_API_URL)
    String authorizationApiUrl;

    @ConfigProperty(name = "stackit.service-account.api-url", defaultValue = StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL)
    String serviceAccountApiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile ResilientKeyFlowAuthenticator authenticator;

    public String getBillingApiUrl() {
        return (billingApiUrl != null && !billingApiUrl.isBlank())
                ? billingApiUrl
                : StackitConstants.DEFAULT_BILLING_API_URL;
    }

    public String getAuthorizationApiUrl() {
        return (authorizationApiUrl != null && !authorizationApiUrl.isBlank())
                ? authorizationApiUrl
                : StackitConstants.DEFAULT_AUTHORIZATION_API_URL;
    }

    public String getServiceAccountApiUrl() {
        return (serviceAccountApiUrl != null && !serviceAccountApiUrl.isBlank())
                ? serviceAccountApiUrl
                : StackitConstants.DEFAULT_SERVICE_ACCOUNT_API_URL;
    }

    public String getMembersUrl(final String projectId) {
        return StackitConstants.formatMembersUrl(getAuthorizationApiUrl(), projectId);
    }

    public String getServiceAccountsUrl(final String projectId) {
        return StackitConstants.formatServiceAccountsUrl(getServiceAccountApiUrl(), projectId);
    }

    public String getServiceAccountKeysUrl(final String projectId, final String serviceAccountId) {
        return StackitConstants.formatServiceAccountKeysUrl(getServiceAccountApiUrl(), projectId, serviceAccountId);
    }

    public String getServiceAccountTokensUrl(final String projectId, final String serviceAccountId) {
        return StackitConstants.formatServiceAccountTokensUrl(getServiceAccountApiUrl(), projectId, serviceAccountId);
    }

    public synchronized ResilientKeyFlowAuthenticator resilientKeyFlowAuthenticator(final CoreConfiguration config) {
        if (authenticator != null) {
            return authenticator;
        }
        if (config == null || config.getServiceAccountKeyPath() == null || config.getServiceAccountKeyPath().isBlank()
                || !Files.exists(Path.of(config.getServiceAccountKeyPath()))) {
            return null;
        }
        try {
            final OkHttpClient baseClient = new OkHttpClient();
            authenticator = new ResilientKeyFlowAuthenticator(baseClient, config);
            return authenticator;
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize ResilientKeyFlowAuthenticator", e);
        }
    }

    public synchronized ResilientKeyFlowAuthenticator getOrCreateAuthenticator() {
        if (authenticator == null) {
            if (serviceAccountKeyPath == null || serviceAccountKeyPath.isEmpty() || serviceAccountKeyPath.get().isBlank()
                    || !Files.exists(Path.of(serviceAccountKeyPath.get()))) {
                return null;
            }
            final CoreConfiguration config = coreConfiguration();
            return resilientKeyFlowAuthenticator(config);
        }
        return authenticator;
    }

    public final JsonNode getAccessTokenClaims() {
        try {
            final ResilientKeyFlowAuthenticator auth = getOrCreateAuthenticator();
            if (auth == null) {
                return null;
            }
            final String token = auth.getAccessToken();
            if (token == null || token.isBlank()) {
                return null;
            }
            final String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                final byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                return objectMapper.readTree(decoded);
            }
        } catch (final Exception e) {
            // ignore network or decoding errors during dynamic discovery
        }
        return null;
    }

    public final String getDiscoveredOrganizationId() {
        final JsonNode claims = getAccessTokenClaims();
        if (claims != null && claims.has("organizations") && claims.get("organizations").isArray()) {
            final JsonNode orgs = claims.get("organizations");
            if (!orgs.isEmpty()) {
                return orgs.get(0).asText();
            }
        }
        return null;
    }

    public final String getDiscoveredProjectId() {
        final JsonNode claims = getAccessTokenClaims();
        if (claims != null && claims.has("stackit/project/project.id")) {
            final String pid = claims.get("stackit/project/project.id").asText();
            if (pid != null && !pid.isBlank()) {
                return pid;
            }
        }
        return null;
    }

    public final String getServiceAccountEmail() {
        final JsonNode claims = getAccessTokenClaims();
        if (claims != null && claims.has("email") && !claims.get("email").asText().isBlank()) {
            return claims.get("email").asText();
        }
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isEmpty()) {
            return null;
        }
        try {
            final String content = Files.readString(Path.of(serviceAccountKeyPath.get()));
            final JsonNode node = objectMapper.readTree(content);
            if (node.has("credentials") && node.get("credentials").has("iss")) {
                return node.get("credentials").get("iss").asText();
            }
            if (node.has("email")) {
                return node.get("email").asText();
            }
        } catch (final Exception e) {
            // ignore
        }
        return null;
    }

    public final String getServiceAccountId() {
        final JsonNode claims = getAccessTokenClaims();
        if (claims != null && claims.has("sub") && !claims.get("sub").asText().isBlank()) {
            return claims.get("sub").asText();
        }
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isEmpty()) {
            return null;
        }
        try {
            final String content = Files.readString(Path.of(serviceAccountKeyPath.get()));
            final JsonNode node = objectMapper.readTree(content);
            if (node.has("credentials") && node.get("credentials").has("sub")) {
                return node.get("credentials").get("sub").asText();
            }
            if (node.has("id")) {
                return node.get("id").asText();
            }
        } catch (final Exception e) {
            // ignore
        }
        return null;
    }

    public final String getServiceAccountKeyAlgorithm() {
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isEmpty()) {
            return null;
        }
        try {
            final String content = Files.readString(Path.of(serviceAccountKeyPath.get()));
            final JsonNode node = objectMapper.readTree(content);
            if (node.has("keyAlgorithm")) {
                return node.get("keyAlgorithm").asText();
            }
        } catch (final Exception e) {
            // ignore
        }
        return null;
    }

    @Produces
    @Singleton
    public CoreConfiguration coreConfiguration() {
        final CoreConfiguration config = new CoreConfiguration();
        serviceAccountKeyPath.ifPresent(config::serviceAccountKeyPath);
        return config;
    }

    @Produces
    @Singleton
    public OkHttpClient okHttpClient(final CoreConfiguration config) {
        final ResilientKeyFlowAuthenticator auth = resilientKeyFlowAuthenticator(config);
        final OkHttpClient baseClient = new OkHttpClient();
        if (auth != null) {
            return baseClient.newBuilder()
                    .authenticator(auth)
                    .build();
        }
        return baseClient;
    }

    @Produces
    @Singleton
    public IaasApi iaasApi(final OkHttpClient httpClient, final CoreConfiguration config) {
        try {
            return new IaasApi(httpClient, config);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize IaasApi", e);
        }
    }

    @Produces
    @Singleton
    public AlbApi albApi(final OkHttpClient httpClient, final CoreConfiguration config) {
        try {
            return new AlbApi(httpClient, config);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize AlbApi", e);
        }
    }

    @Produces
    @Singleton
    public ObjectStorageApi objectStorageApi(final OkHttpClient httpClient, final CoreConfiguration config) {
        try {
            return new ObjectStorageApi(httpClient, config);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize ObjectStorageApi", e);
        }
    }

    @Produces
    @Singleton
    public ResourceManagerApi resourceManagerApi(final OkHttpClient httpClient, final CoreConfiguration config) {
        try {
            return new ResourceManagerApi(httpClient, config);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to initialize ResourceManagerApi", e);
        }
    }

    @Produces
    @Singleton
    public BillingApiClient billingApiClient(final OkHttpClient httpClient) {
        return new BillingApiClient(httpClient, getBillingApiUrl());
    }
}

