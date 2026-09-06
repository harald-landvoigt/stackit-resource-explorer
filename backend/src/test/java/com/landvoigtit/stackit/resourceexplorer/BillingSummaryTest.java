package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.quarkus.narayana.jta.QuarkusTransaction;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class BillingSummaryTest {

    @Inject
    StackitResourceRepository repository;

    @BeforeEach
    public void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            repository.deleteAll();
        });
    }

    @AfterEach
    public void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            repository.deleteAll();
        });
    }

    @Test
    public void testGetBillingSummaryEndpointExists() {
        QuarkusTransaction.requiringNew().run(() -> {
            final StackitEntity dummy = createInvoiceEntity(
                "inv-exists", "billing-org", "org-1", 10.0, "EUR", Instant.now(), null
            );
            repository.persist(dummy);
        });

        given()
            .when()
            .get("/resources/billing-summary")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON);
    }

    @Test
    public void testBillingSummaryAggregation() {
        ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
        ZonedDateTime currentMonthDate = nowUtc.withDayOfMonth(15).withHour(12).withMinute(0);
        ZonedDateTime lastMonthDate = currentMonthDate.minusMonths(1);

        QuarkusTransaction.requiringNew().run(() -> {
            // 1. Current month project billing (Active)
            final StackitEntity activeProjInvoice1 = createInvoiceEntity(
                "inv-1", "billing", "proj-abc", 150.0, "EUR", currentMonthDate.toInstant(), null
            );
            repository.persist(activeProjInvoice1);

            // 2. Current month project billing 2 (Active, same project, same currency -> should sum)
            final StackitEntity activeProjInvoice2 = createInvoiceEntity(
                "inv-2", "billing", "proj-abc", 50.0, "EUR", currentMonthDate.toInstant(), null
            );
            repository.persist(activeProjInvoice2);

            // 3. Current month org billing (Active)
            final StackitEntity activeOrgInvoice = createInvoiceEntity(
                "inv-org-1", "billing-org", "org-123", 1000.0, "EUR", currentMonthDate.toInstant(), null
            );
            repository.persist(activeOrgInvoice);

            // 4. Past month billing (Should NOT be aggregated)
            final StackitEntity pastInvoice = createInvoiceEntity(
                "inv-past", "billing", "proj-abc", 200.0, "EUR", lastMonthDate.toInstant(), null
            );
            repository.persist(pastInvoice);

            // 5. Current month project billing (Soft-deleted -> Should NOT be aggregated)
            final StackitEntity softDeletedInvoice = createInvoiceEntity(
                "inv-deleted", "billing", "proj-abc", 300.0, "EUR", currentMonthDate.toInstant(), Instant.now()
            );
            repository.persist(softDeletedInvoice);
        });

        // Trigger request, log body, and assert aggregation
        given()
            .when()
            .get("/resources/billing-summary")
            .then()
            .log().body()
            .statusCode(200)
            .contentType(ContentType.JSON)
            // Assert organization billing summary is in the first row
            .body("[0].type", is("Organization"))
            .body("[0].id", is("org-123"))
            .body("[0].amount", is(1000.0f))
            .body("[0].currency", is("EUR"))
            // Assert project billing summary is in the subsequent row
            .body("[1].type", is("Project"))
            .body("[1].id", is("proj-abc"))
            .body("[1].amount", is(200.0f))
            .body("[1].currency", is("EUR"));
    }

    @Test
    public void testBillingSummaryOrdering() {
        final ZonedDateTime nowUtc = Instant.now().atZone(ZoneOffset.UTC);
        final ZonedDateTime currentMonthDate = nowUtc.withDayOfMonth(15).withHour(12).withMinute(0);

        QuarkusTransaction.requiringNew().run(() -> {
            // Persist lowest cost project first
            final StackitEntity projLow = createInvoiceEntity(
                "inv-low", "billing", "proj-low", 50.0, "EUR", currentMonthDate.toInstant(), null
            );
            repository.persist(projLow);

            // Persist organization second
            final StackitEntity orgInvoice = createInvoiceEntity(
                "inv-org", "billing-org", "org-main", 1250.0, "EUR", currentMonthDate.toInstant(), null
            );
            repository.persist(orgInvoice);

            // Persist highest cost project third
            final StackitEntity projHigh = createInvoiceEntity(
                "inv-high", "billing", "proj-high", 800.0, "EUR", currentMonthDate.toInstant(), null
            );
            repository.persist(projHigh);

            // Persist medium cost project fourth
            final StackitEntity projMid = createInvoiceEntity(
                "inv-mid", "billing", "proj-mid", 400.0, "EUR", currentMonthDate.toInstant(), null
            );
            repository.persist(projMid);
        });

        // Verify order: Organization in row 0, then projects ordered by costs descending
        given()
            .when()
            .get("/resources/billing-summary")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("size()", is(4))
            .body("[0].type", is("Organization"))
            .body("[0].id", is("org-main"))
            .body("[0].amount", is(1250.0f))
            .body("[1].type", is("Project"))
            .body("[1].id", is("proj-high"))
            .body("[1].amount", is(800.0f))
            .body("[2].type", is("Project"))
            .body("[2].id", is("proj-mid"))
            .body("[2].amount", is(400.0f))
            .body("[3].type", is("Project"))
            .body("[3].id", is("proj-low"))
            .body("[3].amount", is(50.0f));
    }

    private StackitEntity createInvoiceEntity(
        String resourceId, String type, String projectId, double amount, String currency, Instant createdAt, Instant deletedAt
    ) {
        StackitEntity entity = new StackitEntity();
        entity.setId(UUID.nameUUIDFromBytes(resourceId.getBytes()));
        entity.setResourceId(resourceId);
        entity.setName(resourceId + "-num");
        entity.setType(type);
        entity.setStatus("active");
        entity.setRegion("eu-central-1");
        entity.setProjectId(projectId);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(Instant.now());
        entity.setDeletedAt(deletedAt);
        entity.setData(Map.of(
            "amount", amount,
            "currency", currency
        ));
        return entity;
    }
}
