package com.landvoigtit.stackit.resourceexplorer.dns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.landvoigtit.stackit.resourceexplorer.config.ResilientKeyFlowAuthenticator;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class DnsApiClient {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int DEFAULT_INITIAL_PAGE = 1;

    private final OkHttpClient httpClient;
    private final String apiUrl;
    private final ObjectMapper objectMapper;

    public DnsApiClient(final OkHttpClient httpClient, final String apiUrl) {
        this(httpClient, null, apiUrl, new ObjectMapper());
    }

    public DnsApiClient(final OkHttpClient httpClient, final ResilientKeyFlowAuthenticator authenticator, final String apiUrl) {
        this(httpClient, authenticator, apiUrl, new ObjectMapper());
    }

    public DnsApiClient(final OkHttpClient httpClient,
                        final ResilientKeyFlowAuthenticator authenticator,
                        final String apiUrl,
                        final ObjectMapper objectMapper) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient must not be null");
        }
        this.httpClient = authenticator != null
                ? httpClient.newBuilder().authenticator(authenticator).build()
                : httpClient;
        final String rawUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : StackitConstants.DEFAULT_DNS_API_URL;
        this.apiUrl = rawUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public Map<String, Object> fetchZonesPage(final String projectId, final int page, final int pageSize) throws IOException {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be null or blank");
        }
        final String path = StackitConstants.formatDnsZonesPath(projectId);
        final HttpUrl url = HttpUrl.parse(apiUrl + path).newBuilder()
                .addQueryParameter("page", String.valueOf(page > 0 ? page : DEFAULT_INITIAL_PAGE))
                .addQueryParameter("pageSize", String.valueOf(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE))
                .build();

        return executeGetAndParse(url, "DNS zones");
    }

    public List<Map<String, Object>> listZones(final String projectId) throws IOException {
        return listZones(projectId, DEFAULT_INITIAL_PAGE, DEFAULT_PAGE_SIZE);
    }

    public List<Map<String, Object>> listZones(final String projectId, final int initialPage, final int pageSize) throws IOException {
        final List<Map<String, Object>> allZones = new ArrayList<>();
        int page = initialPage > 0 ? initialPage : DEFAULT_INITIAL_PAGE;
        final int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        int totalPages = page;

        while (page <= totalPages) {
            final Map<String, Object> responseMap = fetchZonesPage(projectId, page, size);
            if (responseMap == null || responseMap.isEmpty()) {
                break;
            }
            totalPages = extractTotalPages(responseMap, totalPages);
            final List<Map<String, Object>> items = extractItems(responseMap, "zones");
            if (items.isEmpty()) {
                break;
            }
            allZones.addAll(items);
            page++;
        }
        return allZones;
    }

    public Map<String, Object> fetchRecordSetsPage(final String projectId, final String zoneId, final int page, final int pageSize) throws IOException {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be null or blank");
        }
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be null or blank");
        }
        final String path = StackitConstants.formatDnsRecordSetsPath(projectId, zoneId);
        final HttpUrl url = HttpUrl.parse(apiUrl + path).newBuilder()
                .addQueryParameter("page", String.valueOf(page > 0 ? page : DEFAULT_INITIAL_PAGE))
                .addQueryParameter("pageSize", String.valueOf(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE))
                .build();

        return executeGetAndParse(url, "DNS record sets");
    }

    public List<Map<String, Object>> listRecordSets(final String projectId, final String zoneId) throws IOException {
        return listRecordSets(projectId, zoneId, DEFAULT_INITIAL_PAGE, DEFAULT_PAGE_SIZE);
    }

    public List<Map<String, Object>> listRecordSets(final String projectId, final String zoneId, final int initialPage, final int pageSize) throws IOException {
        final List<Map<String, Object>> allRecordSets = new ArrayList<>();
        int page = initialPage > 0 ? initialPage : DEFAULT_INITIAL_PAGE;
        final int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        int totalPages = page;

        while (page <= totalPages) {
            final Map<String, Object> responseMap = fetchRecordSetsPage(projectId, zoneId, page, size);
            if (responseMap == null || responseMap.isEmpty()) {
                break;
            }
            totalPages = extractTotalPages(responseMap, totalPages);
            final List<Map<String, Object>> items = extractRecordSetsItems(responseMap);
            if (items.isEmpty()) {
                break;
            }
            allRecordSets.addAll(items);
            page++;
        }
        return allRecordSets;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeGetAndParse(final HttpUrl url, final String resourceDescription) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (final Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException(String.format("Failed to fetch %s: HTTP %d %s - Response body: %s",
                        resourceDescription, response.code(), response.message(), errorBody));
            }
            if (response.body() == null) {
                throw new IOException(String.format("Empty response body from DNS API for %s", resourceDescription));
            }
            final String bodyStr = response.body().string();
            if (bodyStr.isBlank()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(bodyStr, Map.class);
        }
    }

    private int extractTotalPages(final Map<String, Object> responseMap, final int defaultPages) {
        final Object tp = responseMap.get("totalPages");
        if (tp instanceof Number num) {
            return num.intValue();
        }
        if (tp != null) {
            try {
                return Integer.parseInt(tp.toString());
            } catch (final NumberFormatException ignored) {
                return defaultPages;
            }
        }
        return defaultPages;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRecordSetsItems(final Map<String, Object> responseMap) {
        for (final String key : List.of("rrSets", "rrsets", "recordSets", "items")) {
            final Object val = responseMap.get(key);
            if (val instanceof List<?> list) {
                return extractMapList(list);
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(final Map<String, Object> responseMap, final String primaryKey) {
        for (final String key : List.of(primaryKey, "items")) {
            final Object val = responseMap.get(key);
            if (val instanceof List<?> list) {
                return extractMapList(list);
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractMapList(final List<?> rawList) {
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Object item : rawList) {
            if (item instanceof Map<?, ?> itemMap) {
                result.add((Map<String, Object>) itemMap);
            }
        }
        return result;
    }
}
