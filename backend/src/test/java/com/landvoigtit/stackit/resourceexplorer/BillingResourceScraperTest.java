package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.billing.BillingResourceScraper;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class BillingResourceScraperTest {

    @Inject
    BillingResourceScraper billingScraper;

    @Inject
    StackitResourceRepository repository;

    @Test
    public final void testBillingScrape() {
        billingScraper.scrape();
        assertFalse(repository.list("type = 'billing'").isEmpty(), "Should have persisted scraped billing invoices");
    }
}
