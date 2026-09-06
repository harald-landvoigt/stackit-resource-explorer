package com.landvoigtit.stackit.resourceexplorer.config;

import cloud.stackit.sdk.core.KeyFlowAuthenticator;
import cloud.stackit.sdk.core.config.CoreConfiguration;
import cloud.stackit.sdk.core.exception.ApiException;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Resilient implementation of {@link KeyFlowAuthenticator} that gracefully recovers
 * when a refresh token expires or is rejected (e.g. STACKIT returning HTTP 400 Bad Request
 * for expired or rotated refresh tokens after 1 hour).
 *
 * <p>Instead of permanently failing, it falls back to creating a fresh access token using
 * the service account private key via {@link #createAccessToken()}.
 */
@Slf4j
public class ResilientKeyFlowAuthenticator extends KeyFlowAuthenticator {

    public ResilientKeyFlowAuthenticator(final OkHttpClient httpClient, final CoreConfiguration cfg) throws IOException {
        super(httpClient, cfg);
    }

    @Override
    protected void createAccessTokenWithRefreshToken() throws IOException, ApiException {
        try {
            super.createAccessTokenWithRefreshToken();
        } catch (final ApiException e) {
            log.warn("Failed to refresh access token with refresh_token (HTTP {}). Re-authenticating with service account key...", e.getCode());
            try {
                createAccessToken();
            } catch (final InvalidKeySpecException ike) {
                throw new IOException("Failed to parse service account private key during re-authentication", ike);
            }
        }
    }
}
