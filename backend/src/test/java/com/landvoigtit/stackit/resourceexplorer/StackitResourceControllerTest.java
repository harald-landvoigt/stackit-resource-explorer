package com.landvoigtit.stackit.resourceexplorer;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class StackitResourceControllerTest {

    @Test
    public final void testCreateAndGetResource() {
        final String id = UUID.randomUUID().toString();
        final Map<String, Object> payload = Map.of(
            "id", id,
            "resourceId", "stackit-vm-999",
            "name", "staging-database",
            "type", "database-instance",
            "status", "RUNNING",
            "region", "eu-east-1",
            "projectId", "project-999",
            "tags", Map.of("env", "staging"),
            "data", Map.of("version", "15.2")
        );

        // Test POST /resources
        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/resources")
            .then()
            .statusCode(201)
            .body("id", is(id))
            .body("name", is("staging-database"))
            .body("createdAt", notNullValue());

        // Test GET /resources/{id}
        given()
            .when()
            .get("/resources/" + id)
            .then()
            .statusCode(200)
            .body("id", is(id))
            .body("resourceId", is("stackit-vm-999"))
            .body("name", is("staging-database"))
            .body("type", is("database-instance"))
            .body("status", is("RUNNING"))
            .body("region", is("eu-east-1"))
            .body("projectId", is("project-999"))
            .body("tags.env", is("staging"))
            .body("data.version", is("15.2"));
    }

    @Test
    public final void testCreateValidationFailure() {
        final Map<String, Object> invalidPayload = Map.of(
            "id", "not-a-uuid",
            "resourceId", "stackit-vm-999",
            "name", "",
            "type", "database-instance",
            "status", "RUNNING",
            "region", "eu-east-1",
            "projectId", "project-999"
        );

        given()
            .contentType(ContentType.JSON)
            .body(invalidPayload)
            .when()
            .post("/resources")
            .then()
            .statusCode(400);
    }

    @Test
    public final void testListAllWithSearchQueryParameter() {
        final String id = UUID.randomUUID().toString();
        final Map<String, Object> payload = Map.of(
            "id", id,
            "resourceId", "vm-search-ctrl-1",
            "name", "gateway-api-service",
            "type", "gateway",
            "status", "ACTIVE",
            "region", "eu01",
            "projectId", "proj-search-ctrl"
        );

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/resources")
            .then()
            .statusCode(201);

        // Test GET /resources?q=gateway
        given()
            .when()
            .queryParam("q", "gateway")
            .get("/resources")
            .then()
            .statusCode(200)
            .body("resources.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("totalCount", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("typeAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("regionAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("statusAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));

        // Test GET /resources without query param
        given()
            .when()
            .get("/resources")
            .then()
            .statusCode(200)
            .body("resources.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("totalCount", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("typeAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("regionAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("statusAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }
}
