package com.landvoigtit.stackit.resourceexplorer;

import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitResourceRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
public class StackitResourceControllerTest {

    @Inject
    StackitResourceRepository repository;

    @Test
    public final void testGetResourceById() {
        final UUID id = UUID.randomUUID();
        final StackitEntity entity = new StackitEntity();
        entity.setId(id);
        entity.setResourceId("stackit-vm-999");
        entity.setName("staging-database");
        entity.setType("database-instance");
        entity.setStatus("RUNNING");
        entity.setRegion("eu-east-1");
        entity.setProjectId("project-999");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setTags(Map.of("env", "staging"));
        entity.setData(Map.of("version", "15.2"));
        repository.persistOrUpdate(entity);

        // Test GET /resources/{id}
        given()
            .when()
            .get("/resources/" + id)
            .then()
            .statusCode(200)
            .body("id", is(id.toString()))
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
    public final void testPostResourcesNotAllowed() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "staging-database"))
            .when()
            .post("/resources")
            .then()
            .statusCode(405);
    }

    @Test
    public final void testListAllWithSearchQueryParameter() {
        final UUID id = UUID.randomUUID();
        final StackitEntity entity = new StackitEntity();
        entity.setId(id);
        entity.setResourceId("vm-search-ctrl-1");
        entity.setName("gateway-api-service");
        entity.setType("gateway");
        entity.setStatus("ACTIVE");
        entity.setRegion("eu01");
        entity.setProjectId("proj-search-ctrl");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.persistOrUpdate(entity);

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
            .body("statusAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("projectAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));

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
            .body("statusAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1))
            .body("projectAggregations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }

    @Test
    public final void testListAllIncludesSoftDeletedResource() {
        final UUID id = UUID.randomUUID();
        final StackitEntity entity = new StackitEntity();
        entity.setId(id);
        entity.setResourceId("del-ctrl-" + id);
        entity.setName("deleted-service-" + id);
        entity.setType("compute");
        entity.setStatus("STOPPED");
        entity.setRegion("eu01");
        entity.setProjectId("proj-del-ctrl");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setDeletedAt(Instant.now());
        repository.persistOrUpdate(entity);

        given()
            .when()
            .queryParam("q", "deleted-service-" + id)
            .get("/resources")
            .then()
            .statusCode(200)
            .body("resources.find { it.id == '" + id + "' }.deletedAt", org.hamcrest.Matchers.notNullValue())
            .body("resources.find { it.id == '" + id + "' }.name", org.hamcrest.Matchers.equalTo("deleted-service-" + id));
    }

    @Test
    public final void testGetAccessIssuesEndpoint() {
        given()
            .when()
            .get("/resources/access-issues")
            .then()
            .statusCode(200)
            .body("totalIssues", notNullValue())
            .body("totalProjectsChecked", notNullValue())
            .body("affectedProjectsCount", notNullValue())
            .body("matrix", notNullValue())
            .body("issues", notNullValue());
    }
}
