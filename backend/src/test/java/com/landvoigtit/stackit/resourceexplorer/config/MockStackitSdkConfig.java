package com.landvoigtit.stackit.resourceexplorer.config;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

@Mock
@ApplicationScoped
public class MockStackitSdkConfig extends StackitSdkConfig {
    public MockStackitSdkConfig() {
        try {
            final java.lang.reflect.Field field = StackitSdkConfig.class.getDeclaredField("serviceAccountKeyPath");
            field.setAccessible(true);
            field.set(this, java.util.Optional.of(com.landvoigtit.stackit.resourceexplorer.StackitSdkMockProducer.getCredentialsPath()));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
