package com.landvoigtit.stackit.resourceexplorer.dns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.landvoigtit.stackit.resourceexplorer.config.ResilientKeyFlowAuthenticator;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
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

public class DnsApiClientTest {

    private static final String BASE_URL = "https://dns.api.stackit.cloud";

    @Test
    public void testListZonesWithAutomaticPagination() throws IOException {
        final List<String> requestedUrls = new ArrayList<>();

        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        final Request request = chain.request();
                        final String url = request.url().toString();
                        requestedUrls.add(url);

                        final String page = request.url().queryParameter("page");
                        final String json;
                        if ("1".equals(page)) {
                            json = "{\n" +
                                    "  \"itemsPerPage\": 100,\n" +
                                    "  \"totalItems\": 2,\n" +
                                    "  \"totalPages\": 2,\n" +
                                    "  \"zones\": [{\"id\": \"z-1\", \"name\": \"zone-1\", \"dnsName\": \"example1.com.\"}]\n" +
                                    "}";
                        } else {
                            json = "{\n" +
                                    "  \"itemsPerPage\": 100,\n" +
                                    "  \"totalItems\": 2,\n" +
                                    "  \"totalPages\": 2,\n" +
                                    "  \"zones\": [{\"id\": \"z-2\", \"name\": \"zone-2\", \"dnsName\": \"example2.com.\"}]\n" +
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

        final DnsApiClient apiClient = new DnsApiClient(client, BASE_URL);
        final List<Map<String, Object>> zones = apiClient.listZones("proj-123");

        assertNotNull(zones);
        assertEquals(2, zones.size());
        assertEquals("z-1", zones.get(0).get("id"));
        assertEquals("z-2", zones.get(1).get("id"));

        assertEquals(2, requestedUrls.size());
        assertTrue(requestedUrls.get(0).contains("/v1/projects/proj-123/zones?page=1&pageSize=100"));
        assertTrue(requestedUrls.get(1).contains("/v1/projects/proj-123/zones?page=2&pageSize=100"));
    }

    @Test
    public void testListRecordSetsWithAutomaticPagination() throws IOException {
        final List<String> requestedUrls = new ArrayList<>();

        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        final Request request = chain.request();
                        final String url = request.url().toString();
                        requestedUrls.add(url);

                        final String page = request.url().queryParameter("page");
                        final String json;
                        if ("1".equals(page)) {
                            json = "{\n" +
                                    "  \"itemsPerPage\": 50,\n" +
                                    "  \"totalItems\": 2,\n" +
                                    "  \"totalPages\": 2,\n" +
                                    "  \"rrSets\": [{\"id\": \"rs-1\", \"name\": \"api.example.com.\", \"type\": \"A\"}]\n" +
                                    "}";
                        } else {
                            json = "{\n" +
                                    "  \"itemsPerPage\": 50,\n" +
                                    "  \"totalItems\": 2,\n" +
                                    "  \"totalPages\": 2,\n" +
                                    "  \"rrSets\": [{\"id\": \"rs-2\", \"name\": \"mail.example.com.\", \"type\": \"MX\"}]\n" +
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

        final DnsApiClient apiClient = new DnsApiClient(client, BASE_URL);
        final List<Map<String, Object>> recordSets = apiClient.listRecordSets("proj-123", "zone-456", 1, 50);

        assertNotNull(recordSets);
        assertEquals(2, recordSets.size());
        assertEquals("rs-1", recordSets.get(0).get("id"));
        assertEquals("rs-2", recordSets.get(1).get("id"));

        assertEquals(2, requestedUrls.size());
        assertTrue(requestedUrls.get(0).contains("/v1/projects/proj-123/zones/zone-456/rrsets?page=1&pageSize=50"));
        assertTrue(requestedUrls.get(1).contains("/v1/projects/proj-123/zones/zone-456/rrsets?page=2&pageSize=50"));
    }

    @Test
    public void testAlternativeKeysAndEmptyResponses() throws IOException {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        final Request request = chain.request();
                        final String url = request.url().toString();
                        final String json;
                        if (url.contains("/zones/z-1/rrsets")) {
                            json = "{\n" +
                                    "  \"totalPages\": 1,\n" +
                                    "  \"recordSets\": [{\"id\": \"rs-alt-1\", \"name\": \"alt.example.com.\", \"type\": \"TXT\"}]\n" +
                                    "}";
                        } else if (url.contains("/zones/z-2/rrsets")) {
                            json = "{\n" +
                                    "  \"totalPages\": 1,\n" +
                                    "  \"items\": [{\"id\": \"rs-alt-2\", \"name\": \"alt2.example.com.\", \"type\": \"CNAME\"}]\n" +
                                    "}";
                        } else {
                            json = "{\n" +
                                    "  \"totalPages\": 1,\n" +
                                    "  \"items\": [{\"id\": \"z-1\", \"name\": \"zone-1\"}, {\"id\": \"z-2\", \"name\": \"zone-2\"}]\n" +
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

        final DnsApiClient apiClient = new DnsApiClient(client, BASE_URL);

        final List<Map<String, Object>> zones = apiClient.listZones("p-1");
        assertEquals(2, zones.size());

        final List<Map<String, Object>> rs1 = apiClient.listRecordSets("p-1", "z-1");
        assertEquals(1, rs1.size());
        assertEquals("rs-alt-1", rs1.get(0).get("id"));

        final List<Map<String, Object>> rs2 = apiClient.listRecordSets("p-1", "z-2");
        assertEquals(1, rs2.size());
        assertEquals("rs-alt-2", rs2.get(0).get("id"));
    }

    @Test
    public void testHttpErrorHandling() {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) {
                        return new Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(403)
                                .message("Forbidden")
                                .body(ResponseBody.create("{\"message\": \"User not authorized\"}", MediaType.parse("application/json")))
                                .build();
                    }
                })
                .build();

        final DnsApiClient apiClient = new DnsApiClient(client, BASE_URL);

        final IOException ex = assertThrows(IOException.class, () -> apiClient.listZones("p-1"));
        assertTrue(ex.getMessage().contains("HTTP 403"));
        assertTrue(ex.getMessage().contains("User not authorized"));
    }

    @Test
    public void testIllegalArgumentValidation() {
        final DnsApiClient apiClient = new DnsApiClient(new OkHttpClient(), BASE_URL);

        assertThrows(IllegalArgumentException.class, () -> apiClient.fetchZonesPage(null, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> apiClient.fetchZonesPage("  ", 1, 100));
        assertThrows(IllegalArgumentException.class, () -> apiClient.fetchRecordSetsPage(null, "z-1", 1, 100));
        assertThrows(IllegalArgumentException.class, () -> apiClient.fetchRecordSetsPage("p-1", null, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> apiClient.fetchRecordSetsPage("p-1", "  ", 1, 100));
        assertThrows(IllegalArgumentException.class, () -> new DnsApiClient(null, BASE_URL));
    }

    @Test
    public void testConstructorWithAuthenticator() {
        final ResilientKeyFlowAuthenticator authMock = mock(ResilientKeyFlowAuthenticator.class);
        final OkHttpClient baseClient = new OkHttpClient();
        final DnsApiClient apiClient = new DnsApiClient(baseClient, authMock, null, new ObjectMapper());

        assertNotNull(apiClient);
    }
}
