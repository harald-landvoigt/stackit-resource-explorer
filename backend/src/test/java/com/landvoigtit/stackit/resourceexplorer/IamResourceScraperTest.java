package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.iam.IamResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class IamResourceScraperTest {

    @Inject
    IamResourceScraper scraper;

    @Inject
    StackitResourceRepository repository;

    @BeforeEach
    @Transactional
    public void cleanUp() {
        repository.deleteAll();
    }

    @Test
    public final void testIamScrape() {
        // Run scraper
        scraper.scrape();

        // Retrieve scraped resources
        final List<StackitEntity> entities = repository.list("type = 'iam'");
        assertEquals(4, entities.size());

        // Validate first scraped member
        final StackitEntity m1 = entities.stream()
                .filter(e -> "sa-1@stackit.de".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(m1);
        assertEquals("sa-1@stackit.de", m1.getName());
        assertEquals("iam", m1.getType());
        assertEquals("ACTIVE", m1.getStatus());
        assertEquals("global", m1.getRegion());
        assertEquals("00000000-0000-0000-0000-000000000000", m1.getProjectId());
        assertNull(m1.getDeletedAt());
        assertEquals("project.owner", m1.getData().get("role"));
        assertEquals("User (Human)", m1.getData().get("identityType"));
        assertEquals("OIDC / Enterprise SSO", m1.getData().get("authScheme"));
        assertEquals("OIDC / SSO", m1.getData().get("authFlow"));
        assertEquals("stackit.de", m1.getData().get("idpDomain"));
        assertEquals("OIDC / Enterprise SSO", m1.getTags().get("auth-scheme"));

        // Validate second scraped member
        final StackitEntity m2 = entities.stream()
                .filter(e -> "sa-2@stackit.de".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(m2);
        assertEquals("sa-2@stackit.de", m2.getName());
        assertEquals("iam", m2.getType());
        assertEquals("ACTIVE", m2.getStatus());
        assertEquals("global", m2.getRegion());
        assertEquals("00000000-0000-0000-0000-000000000000", m2.getProjectId());
        assertNull(m2.getDeletedAt());
        assertEquals("project.member", m2.getData().get("role"));
        assertEquals("User (Human)", m2.getData().get("identityType"));
        assertEquals("OIDC / Enterprise SSO", m2.getData().get("authScheme"));

        // Validate service account definition with Key Flow
        final StackitEntity sa = entities.stream()
                .filter(e -> "scraper-8mdqk4i8@sa.stackit.cloud".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(sa);
        assertEquals("scraper-8mdqk4i8@sa.stackit.cloud", sa.getName());
        assertEquals("iam", sa.getType());
        assertEquals("ACTIVE", sa.getStatus());
        assertEquals("00000000-0000-0000-0000-000000000000", sa.getProjectId());
        assertNull(sa.getDeletedAt());
        assertEquals("service-account", sa.getData().get("role"));
        assertEquals("910ebfe0-201f-4a9a-9c86-cbf6936a94c2", sa.getData().get("serviceAccountId"));
        assertEquals(false, sa.getData().get("internal"));
        assertEquals("Service Account", sa.getData().get("identityType"));
        assertEquals("Key Flow (RSA_2048)", sa.getData().get("authScheme"));
        assertEquals("Key Flow", sa.getData().get("authFlow"));
        assertNull(sa.getData().get("deprecated"));
        assertEquals(1, sa.getData().get("activeKeys"));
        assertEquals("RSA_2048", sa.getData().get("keyAlgorithm"));
        assertEquals("Key Flow (RSA_2048)", sa.getTags().get("auth-scheme"));

        // Validate service account definition with Token Flow (Deprecated)
        final StackitEntity legacySa = entities.stream()
                .filter(e -> "legacy-sa@sa.stackit.cloud".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(legacySa);
        assertEquals("legacy-sa@sa.stackit.cloud", legacySa.getName());
        assertEquals("iam", legacySa.getType());
        assertEquals("ACTIVE", legacySa.getStatus());
        assertEquals("Service Account", legacySa.getData().get("identityType"));
        assertEquals("Token Flow (Deprecated)", legacySa.getData().get("authScheme"));
        assertEquals("Token Flow (Deprecated)", legacySa.getData().get("authFlow"));
        assertEquals(true, legacySa.getData().get("deprecated"));
        assertEquals("The legacy model where a long-lived, static API secret acted directly as a bearer token.", legacySa.getData().get("legacyModel"));
        assertEquals(1, legacySa.getData().get("staticTokenCount"));
        assertEquals("Token Flow (Deprecated)", legacySa.getTags().get("auth-scheme"));
        assertEquals("true", legacySa.getTags().get("deprecated"));
        assertEquals("token-flow-deprecated", legacySa.getTags().get("auth-flow"));
    }

    @Test
    @Transactional
    public final void testIamScrapeWithSoftDelete() {
        // Persist a dummy iam resource that should be soft-deleted during scrape
        final StackitEntity staleResource = new StackitEntity();
        staleResource.setId(UUID.randomUUID());
        staleResource.setResourceId("stale-sa@stackit.de");
        staleResource.setName("stale-sa@stackit.de");
        staleResource.setType("iam");
        staleResource.setStatus("ACTIVE");
        staleResource.setRegion("global");
        staleResource.setProjectId("00000000-0000-0000-0000-000000000000");
        staleResource.setCreatedAt(Instant.now());
        staleResource.setUpdatedAt(Instant.now());
        repository.persist(staleResource);

        // Run scraper
        scraper.scrape();

        // Retrieve and check all iam resources
        final List<StackitEntity> entities = repository.list("type = 'iam'");
        assertEquals(5, entities.size());

        // Verify stale resource was soft deleted
        final StackitEntity retrievedStale = entities.stream()
                .filter(e -> "stale-sa@stackit.de".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(retrievedStale);
        assertNotNull(retrievedStale.getDeletedAt());

        // Verify scraped active members have not been soft deleted
        final StackitEntity activeMember = entities.stream()
                .filter(e -> "sa-1@stackit.de".equals(e.getResourceId()))
                .findFirst()
                .orElse(null);
        assertNotNull(activeMember);
        assertNull(activeMember.getDeletedAt());
    }
}
