package com.landvoigtit.stackit.resourceexplorer;

import io.quarkus.scheduler.Scheduled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class ScraperSchedulingConfigurationTest {

    @ParameterizedTest
    @ValueSource(classes = {
            com.landvoigtit.stackit.resourceexplorer.compute.ComputeResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.storage.VmDiskResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.network.PublicIpResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.network.NetworkResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.network.NetworkVpcResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.storage.StorageResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.database.DatabaseResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.iam.IamResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.dns.DnsResourceScraper.class,
            com.landvoigtit.stackit.resourceexplorer.billing.BillingResourceScraper.class
    })
    public void testScraperSchedulingAndConcurrencyGuards(final Class<?> scraperClass) throws Exception {
        // 1. Verify scrape() method exists and has @Scheduled
        final Method scrapeMethod = scraperClass.getDeclaredMethod("scrape");
        assertNotNull(scrapeMethod, "Scraper " + scraperClass.getSimpleName() + " must define a scrape() method");

        final Scheduled scheduled = scrapeMethod.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "Scraper " + scraperClass.getSimpleName() + ".scrape() must be annotated with @Scheduled");

        // 2. Verify concurrentExecution = SKIP
        assertEquals(Scheduled.ConcurrentExecution.SKIP, scheduled.concurrentExecution(),
                "Scraper " + scraperClass.getSimpleName() + " must have concurrentExecution = SKIP");

        // 3. Verify delayed is configured to prevent thundering herd
        assertFalse(scheduled.delayed().isBlank(),
                "Scraper " + scraperClass.getSimpleName() + " must define a delayed offset");

        // 4. Verify isScraping AtomicBoolean guard field exists
        final Field isScrapingField = scraperClass.getDeclaredField("isScraping");
        assertNotNull(isScrapingField, "Scraper " + scraperClass.getSimpleName() + " must have an isScraping field");
        assertEquals(AtomicBoolean.class, isScrapingField.getType(),
                "Field isScraping in " + scraperClass.getSimpleName() + " must be of type AtomicBoolean");
    }
}
