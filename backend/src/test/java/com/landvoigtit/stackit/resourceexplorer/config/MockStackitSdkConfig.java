package com.landvoigtit.stackit.resourceexplorer.config;

import com.landvoigtit.stackit.resourceexplorer.StackitSdkMockProducer;
import io.quarkus.test.Mock;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mock
@ApplicationScoped
public class MockStackitSdkConfig extends StackitSdkConfig {

    public MockStackitSdkConfig() {
        applyMockCredentials();
    }

    @PostConstruct
    void initMockConfig() {
        applyMockCredentials();
    }

    private void applyMockCredentials() {
        final String pathStr = this.serviceAccountKeyPath != null ? this.serviceAccountKeyPath.orElse(null) : null;
        if (pathStr == null || !Files.isRegularFile(Path.of(pathStr))) {
            this.serviceAccountKeyPath = Optional.of(StackitSdkMockProducer.getCredentialsPath());
        }
    }

    @Override
    public Optional<String> getServiceAccountKeyPath() {
        if (this.serviceAccountKeyPath != null && this.serviceAccountKeyPath.isPresent()
                && Files.isRegularFile(Path.of(this.serviceAccountKeyPath.get()))) {
            return this.serviceAccountKeyPath;
        }
        return Optional.of(StackitSdkMockProducer.getCredentialsPath());
    }

    @Override
    public String getDiscoveredOrganizationId() {
        return "869271ad-bde6-4e7b-99a1-0c2dab2b4171";
    }

    @Override
    public String getDiscoveredProjectId() {
        return StackitSdkMockProducer.MOCK_PROJECT_ID.toString();
    }

    @Override
    public String getServiceAccountEmail() {
        return "scraper-8mdqk4i8@sa.stackit.cloud";
    }

    @Override
    public String getServiceAccountId() {
        return "910ebfe0-201f-4a9a-9c86-cbf6936a94c2";
    }

    @Override
    public String getServiceAccountKeyAlgorithm() {
        return "RSA_2048";
    }
}
