package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.billing.BillingApiClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class BillingApiClientTest {

    @Inject
    BillingApiClient billingApiClient;

    @Test
    public final void testInjection() {
        assertNotNull(billingApiClient, "BillingApiClient should be successfully injected");
    }

    @Test
    public final void testGetInvoices() throws IOException {
        final String invoicesJson = billingApiClient.getInvoices("project-123", null);
        assertNotNull(invoicesJson);
        assertTrue(invoicesJson.contains("inv-123"));
        assertTrue(invoicesJson.contains("amount"));
    }

    @Test
    public final void testGetProjectCosts() throws IOException {
        final String costsJson = billingApiClient.getProjectCosts("org-123", "2026-09-01", "2026-09-30");
        assertNotNull(costsJson);
        assertTrue(costsJson.contains("project-123"));
        assertTrue(costsJson.contains("totalCharge"));
    }
}
