package com.landvoigtit.stackit.resourceexplorer;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class IntegrationTestProfile implements QuarkusTestProfile {
    @Override
    public final Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.arc.exclude-types", "com.landvoigtit.stackit.resourceexplorer.StackitSdkMockProducer");
    }
}
