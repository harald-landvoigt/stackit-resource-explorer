package com.landvoigtit.stackit.resourceexplorer.database;

import com.landvoigtit.stackit.resourceexplorer.config.ResilientKeyFlowAuthenticator;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class DatabaseApiClientTest {

    @Test
    public void testListInstancesUnpagedItemsPayload() throws IOException {
        final List<String> requestedUrls = new ArrayList<>();

        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        final Request request = chain.request();
                        requestedUrls.add(request.url().toString());

                        final String json = "{\n" +
                                "  \"items\": [\n" +
                                "    {\"id\": \"os-1\", \"name\": \"cluster-opensearch\", \"status\": \"Ready\"},\n" +
                                "    {\"id\": \"os-2\", \"name\": \"search-opensearch\", \"status\": \"Ready\"}\n" +
                                "  ],\n" +
                                "  \"count\": 2\n" +
                                "}";

                        return new Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                .build();
                    }
                })
                .build();

        final DatabaseApiClient apiClient = new DatabaseApiClient(client);
        final List<Map<String, Object>> instances = apiClient.listInstances("opensearch", "proj-123", "eu01");

        assertNotNull(instances);
        assertEquals(2, instances.size());
        assertEquals("os-1", instances.get(0).get("id"));
        assertEquals("os-2", instances.get(1).get("id"));
        assertEquals(1, requestedUrls.size());
        assertTrue(requestedUrls.get(0).contains("/v1/projects/proj-123/instances"));
    }

    @Test
    public void testListInstancesPagedV3AutomaticPagination() throws IOException {
        final List<String> requestedUrls = new ArrayList<>();

        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        final Request request = chain.request();
                        requestedUrls.add(request.url().toString());

                        final String page = request.url().queryParameter("page");
                        final String json;
                        if ("1".equals(page) || page == null) {
                            json = "{\n" +
                                    "  \"instances\": [{\"id\": \"pg-1\", \"name\": \"postgres-master\"}],\n" +
                                    "  \"pagination\": {\n" +
                                    "    \"page\": 1,\n" +
                                    "    \"size\": 1,\n" +
                                    "    \"totalPages\": 2,\n" +
                                    "    \"totalRows\": 2\n" +
                                    "  }\n" +
                                    "}";
                        } else {
                            json = "{\n" +
                                    "  \"instances\": [{\"id\": \"pg-2\", \"name\": \"postgres-replica\"}],\n" +
                                    "  \"pagination\": {\n" +
                                    "    \"page\": 2,\n" +
                                    "    \"size\": 1,\n" +
                                    "    \"totalPages\": 2,\n" +
                                    "    \"totalRows\": 2\n" +
                                    "  }\n" +
                                    "}";
                        }

                        return new Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                .build();
                    }
                })
                .build();

        final DatabaseApiClient apiClient = new DatabaseApiClient(client);
        final List<Map<String, Object>> instances = apiClient.listInstances("postgresql", "proj-456", "eu01");

        assertNotNull(instances);
        assertEquals(2, instances.size());
        assertEquals("pg-1", instances.get(0).get("id"));
        assertEquals("pg-2", instances.get(1).get("id"));
        assertEquals(2, requestedUrls.size());
        assertTrue(requestedUrls.get(0).contains("page=1"));
        assertTrue(requestedUrls.get(1).contains("page=2"));
        assertTrue(requestedUrls.get(0).contains("/v2/projects/proj-456/regions/eu01/instances"));
    }

    @Test
    public void testHttpErrorThrowsIOException() {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(403)
                                .message("Forbidden")
                                .body(ResponseBody.create("{\"error\":\"Access denied\"}", MediaType.parse("application/json")))
                                .build();
                    }
                })
                .build();

        final DatabaseApiClient apiClient = new DatabaseApiClient(client);
        final IOException ex = assertThrows(IOException.class, () ->
                apiClient.listInstances("mongodb", "proj-999", "eu01")
        );
        assertTrue(ex.getMessage().contains("403"));
        assertTrue(ex.getMessage().contains("Access denied"));
    }

    @Test
    public void testConstructorWithAuthenticator() {
        final OkHttpClient baseClient = new OkHttpClient();
        final ResilientKeyFlowAuthenticator authenticator = mock(ResilientKeyFlowAuthenticator.class);
        final DatabaseApiClient client = new DatabaseApiClient(baseClient, authenticator, Map.of("opensearch", "https://custom-opensearch.local"));
        assertNotNull(client);
        assertEquals("https://custom-opensearch.local", client.resolveBaseUrl("opensearch"));
    }

    @Test
    public void testGetInstancePostgresqlMergesV2AndV3Details() throws IOException {
        final List<String> requestedUrls = new ArrayList<>();

        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        final Request request = chain.request();
                        final String url = request.url().toString();
                        requestedUrls.add(url);

                        final String json;
                        if (url.contains("/v2/projects/")) {
                            json = "{\n" +
                                    "  \"item\": {\n" +
                                    "    \"id\": \"e81e1e10-27b2-47df-9654-dc1388461787\",\n" +
                                    "    \"name\": \"postgres-dvqco116br3j\",\n" +
                                    "    \"version\": \"17\",\n" +
                                    "    \"replicas\": 1,\n" +
                                    "    \"flavor\": {\n" +
                                    "      \"id\": \"2.4\",\n" +
                                    "      \"cpu\": 2,\n" +
                                    "      \"memory\": 4,\n" +
                                    "      \"description\": \"Small, Compute optimized\"\n" +
                                    "    },\n" +
                                    "    \"status\": \"Ready\"\n" +
                                    "  }\n" +
                                    "}";
                        } else {
                            json = "{\n" +
                                    "  \"id\": \"e81e1e10-27b2-47df-9654-dc1388461787\",\n" +
                                    "  \"name\": \"postgres-dvqco116br3j\",\n" +
                                    "  \"state\": \"READY\",\n" +
                                    "  \"flavorId\": \"2.4\",\n" +
                                    "  \"connectionInfo\": {\n" +
                                    "    \"write\": {\n" +
                                    "      \"host\": \"e81e1e10-27b2-47df-9654-dc1388461787.postgresql.eu02.onstackit.cloud\",\n" +
                                    "      \"port\": 5432\n" +
                                    "    }\n" +
                                    "  },\n" +
                                    "  \"network\": {\n" +
                                    "    \"accessScope\": \"PUBLIC\",\n" +
                                    "    \"acl\": [\"193.148.160.0/19\", \"45.129.40.0/21\"]\n" +
                                    "  },\n" +
                                    "  \"labels\": {\n" +
                                    "    \"cost_center\": \"4711\",\n" +
                                    "    \"owner\": \"harald.landvoigt\"\n" +
                                    "  }\n" +
                                    "}";
                        }

                        return new Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                .build();
                    }
                })
                .build();

        final DatabaseApiClient apiClient = new DatabaseApiClient(client);
        final Map<String, Object> detail = apiClient.getInstance(
                "postgresql",
                "c2cc46ae-e432-4e4a-b4df-42a4066c344b",
                "eu02",
                "e81e1e10-27b2-47df-9654-dc1388461787"
        );

        assertNotNull(detail);
        assertEquals(2, requestedUrls.size());
        assertEquals("e81e1e10-27b2-47df-9654-dc1388461787", detail.get("id"));
        assertEquals(1, detail.get("replicas"));
        assertTrue(detail.containsKey("connectionInfo"));
        assertTrue(detail.containsKey("network"));
        assertTrue(detail.containsKey("labels"));
        assertTrue(detail.containsKey("flavor"));
    }

    @Test
    public void testGetInstanceSqlServerMergesV2AndV3Details() throws IOException {
        final List<String> requestedUrls = new ArrayList<>();

        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        final Request request = chain.request();
                        final String url = request.url().toString();
                        requestedUrls.add(url);

                        final String json;
                        if (url.contains("/v2/projects/")) {
                            json = "{\n" +
                                    "  \"item\": {\n" +
                                    "    \"id\": \"sql-inst-1\",\n" +
                                    "    \"name\": \"mssql-prod\",\n" +
                                    "    \"version\": \"2022\",\n" +
                                    "    \"replicas\": 2,\n" +
                                    "    \"flavor\": {\"id\": \"4.16-Single\", \"cpu\": 4, \"memory\": 16, \"description\": \"Standard\"},\n" +
                                    "    \"status\": \"Ready\"\n" +
                                    "  }\n" +
                                    "}";
                        } else {
                            json = "{\n" +
                                    "  \"id\": \"sql-inst-1\",\n" +
                                    "  \"name\": \"mssql-prod\",\n" +
                                    "  \"state\": \"READY\",\n" +
                                    "  \"flavorId\": \"4.16-Single\",\n" +
                                    "  \"retentionDays\": 14,\n" +
                                    "  \"connectionInfo\": {\"write\": {\"host\": \"sql-inst-1.mssql.eu01.onstackit.cloud\", \"port\": 1433}},\n" +
                                    "  \"network\": {\"accessScope\": \"PUBLIC\", \"acl\": [\"10.10.0.0/16\"]},\n" +
                                    "  \"labels\": {\"team\": \"erp\"}\n" +
                                    "}";
                        }

                        return new Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                .build();
                    }
                })
                .build();

        final DatabaseApiClient apiClient = new DatabaseApiClient(client);
        final Map<String, Object> detail = apiClient.getInstance("sqlserver", "proj-sql", "eu01", "sql-inst-1");

        assertNotNull(detail);
        assertEquals(2, requestedUrls.size());
        assertEquals("sql-inst-1", detail.get("id"));
        assertEquals(2, detail.get("replicas"));
        assertEquals(14, detail.get("retentionDays"));
        assertTrue(detail.containsKey("connectionInfo"));
        assertTrue(detail.containsKey("network"));
        assertTrue(detail.containsKey("labels"));
    }
}
