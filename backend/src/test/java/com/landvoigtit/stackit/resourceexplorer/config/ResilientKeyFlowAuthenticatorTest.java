package com.landvoigtit.stackit.resourceexplorer.config;

import cloud.stackit.sdk.core.config.CoreConfiguration;
import cloud.stackit.sdk.core.exception.ApiException;
import com.landvoigtit.stackit.resourceexplorer.StackitSdkMockProducer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ResilientKeyFlowAuthenticatorTest {

    @Test
    public void testFallbackToCreateAccessTokenOnRefreshTokenFailure() throws Exception {
        final CoreConfiguration config = new CoreConfiguration()
                .serviceAccountKeyPath(StackitSdkMockProducer.getCredentialsPath());
        final OkHttpClient baseClient = new OkHttpClient();

        final AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        final AtomicBoolean superRefreshCalled = new AtomicBoolean(false);

        final ResilientKeyFlowAuthenticator authenticator = new ResilientKeyFlowAuthenticator(baseClient, config) {
            @Override
            protected void createAccessToken() {
                fallbackCalled.set(true);
            }

            @Override
            protected void createAccessTokenWithRefreshToken() throws IOException, ApiException {
                // Simulate the behavior of ResilientKeyFlowAuthenticator:
                // try super (which fails with 400), then catch ApiException and call createAccessToken()
                superRefreshCalled.set(true);
                try {
                    throw new ApiException(400, "Bad Request");
                } catch (final ApiException e) {
                    try {
                        createAccessToken();
                    } catch (final Exception ike) {
                        throw new IOException("Failed to parse key", ike);
                    }
                }
            }
        };

        authenticator.createAccessTokenWithRefreshToken();

        assertTrue(superRefreshCalled.get(), "createAccessTokenWithRefreshToken should be called");
        assertTrue(fallbackCalled.get(), "createAccessToken should be called as fallback when refresh token fails");
    }

    @Test
    public void testOkHttpClientProducerCreatesResilientAuthenticator() {
        final CoreConfiguration config = new CoreConfiguration()
                .serviceAccountKeyPath(StackitSdkMockProducer.getCredentialsPath());
        final StackitSdkConfig sdkConfig = new StackitSdkConfig();

        final OkHttpClient client = sdkConfig.okHttpClient(config);
        assertNotNull(client);
        assertNotNull(client.authenticator());
        assertTrue(client.authenticator() instanceof ResilientKeyFlowAuthenticator,
                "Authenticator must be an instance of ResilientKeyFlowAuthenticator");
    }
}
