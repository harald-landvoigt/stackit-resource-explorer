package com.landvoigtit.stackit.resourceexplorer.database;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landvoigtit.stackit.resourceexplorer.config.ResilientKeyFlowAuthenticator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resilient HTTP client for querying STACKIT Managed Database and messaging APIs
 * across multiple database engines with automatic pagination and multi-version support.
 */
@Slf4j
public class DatabaseApiClient {

    public static final String DEFAULT_POSTGRES_FLEX_API_URL = "https://postgres-flex-service.api.stackit.cloud";
    public static final String DEFAULT_MONGODB_FLEX_API_URL = "https://mongodb-flex-service.api.stackit.cloud";
    public static final String DEFAULT_OPENSEARCH_API_URL = "https://opensearch.api.stackit.cloud";
    public static final String DEFAULT_MARIADB_API_URL = "https://mariadb.api.stackit.cloud";
    public static final String DEFAULT_SQLSERVER_FLEX_API_URL = "https://mssql-flex-service.api.stackit.cloud";

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int DEFAULT_INITIAL_PAGE = 1;

    private final OkHttpClient httpClient;
    private final Map<String, String> engineBaseUrls;
    private final ObjectMapper objectMapper;

    public DatabaseApiClient(final OkHttpClient httpClient) {
        this(httpClient, null, Collections.emptyMap(), new ObjectMapper());
    }

    public DatabaseApiClient(final OkHttpClient httpClient, final ResilientKeyFlowAuthenticator authenticator) {
        this(httpClient, authenticator, Collections.emptyMap(), new ObjectMapper());
    }

    public DatabaseApiClient(final OkHttpClient httpClient,
                             final ResilientKeyFlowAuthenticator authenticator,
                             final Map<String, String> customEngineUrls) {
        this(httpClient, authenticator, customEngineUrls, new ObjectMapper());
    }

    public DatabaseApiClient(final OkHttpClient httpClient,
                             final ResilientKeyFlowAuthenticator authenticator,
                             final Map<String, String> customEngineUrls,
                             final ObjectMapper objectMapper) {
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient must not be null");
        }
        this.httpClient = authenticator != null
                ? httpClient.newBuilder().authenticator(authenticator).build()
                : httpClient;
        this.engineBaseUrls = initEngineBaseUrls(customEngineUrls);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public List<Map<String, Object>> listInstances(final String engine, final String projectId, final String region) throws IOException {
        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("engine must not be null or blank");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be null or blank");
        }

        final List<Map<String, Object>> allInstances = new ArrayList<>();
        int page = DEFAULT_INITIAL_PAGE;
        int totalPages = page;

        do {
            final HttpUrl url = buildUrl(engine, projectId, region, page, totalPages > 1 ? DEFAULT_PAGE_SIZE : null);
            final Object responseObj = executeGetAndParse(url, engine + " instances");

            if (responseObj == null) {
                break;
            }

            final List<Map<String, Object>> pageItems = extractInstances(responseObj);
            allInstances.addAll(pageItems);

            if (responseObj instanceof Map<?, ?> responseMap) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> map = (Map<String, Object>) responseMap;
                totalPages = extractTotalPages(map, totalPages);
            }

            page++;
        } while (page <= totalPages);

        return allInstances;
    }

    public Map<String, Object> getInstance(final String engine,
                                           final String projectId,
                                           final String region,
                                           final String instanceId) throws IOException {
        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("engine must not be null or blank");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be null or blank");
        }
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId must not be null or blank");
        }

        final String normalized = normalizeEngine(engine);
        final String baseUrl = resolveBaseUrl(engine);
        final String primaryPath = formatInstanceDetailPath(engine, projectId, region, instanceId);
        final HttpUrl primaryUrl = HttpUrl.parse(baseUrl + primaryPath);

        Map<String, Object> primaryDetail = Collections.emptyMap();
        IOException primaryException = null;
        try {
            final Object responseObj = executeGetAndParse(primaryUrl, engine + " instance " + instanceId);
            primaryDetail = extractInstanceDetail(responseObj, instanceId);
        } catch (final IOException e) {
            primaryException = e;
        }

        if ("postgresql".equals(normalized) || "sqlserver".equals(normalized)) {
            final String reg = (region != null && !region.isBlank()) ? region : "eu01";
            final String v3Path = String.format("/v3/projects/%s/regions/%s/instances/%s", projectId, reg, instanceId);
            final HttpUrl v3Url = HttpUrl.parse(baseUrl + v3Path);
            try {
                final Object v3ResponseObj = executeGetAndParse(v3Url, engine + " v3 instance " + instanceId);
                final Map<String, Object> v3Detail = extractInstanceDetail(v3ResponseObj, instanceId);
                return mergeDetailMaps(primaryDetail, v3Detail);
            } catch (final IOException v3Ex) {
                if (primaryException != null) {
                    throw primaryException;
                }
                log.debug("{} v3 instance detail endpoint unavailable for {}: {}", engine, instanceId, v3Ex.getMessage());
            }
        } else if (primaryException != null) {
            throw primaryException;
        }

        return primaryDetail;
    }

    public String resolveBaseUrl(final String engine) {
        if (engine == null) {
            return DEFAULT_POSTGRES_FLEX_API_URL;
        }
        final String normalized = normalizeEngine(engine);
        return engineBaseUrls.getOrDefault(normalized, DEFAULT_POSTGRES_FLEX_API_URL);
    }

    public String formatEndpointPath(final String engine, final String projectId, final String region) {
        final String normalized = normalizeEngine(engine);
        if ("postgresql".equals(normalized) || "sqlserver".equals(normalized) || "mongodb".equals(normalized)) {
            final String reg = (region != null && !region.isBlank()) ? region : "eu01";
            return String.format("/v2/projects/%s/regions/%s/instances", projectId, reg);
        }
        return String.format("/v1/projects/%s/instances", projectId);
    }

    public String formatInstanceDetailPath(final String engine,
                                           final String projectId,
                                           final String region,
                                           final String instanceId) {
        return formatEndpointPath(engine, projectId, region) + "/" + instanceId;
    }

    private HttpUrl buildUrl(final String engine,
                             final String projectId,
                             final String region,
                             final int page,
                             final Integer pageSize) {
        final String baseUrl = resolveBaseUrl(engine);
        final String path = formatEndpointPath(engine, projectId, region);
        final HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();

        if (pageSize != null || page > 1 || isPagedEngine(engine)) {
            urlBuilder.addQueryParameter("page", String.valueOf(page));
            urlBuilder.addQueryParameter("size", String.valueOf(pageSize != null ? pageSize : DEFAULT_PAGE_SIZE));
        }

        return urlBuilder.build();
    }

    private boolean isPagedEngine(final String engine) {
        final String normalized = normalizeEngine(engine);
        return "postgresql".equals(normalized) || "sqlserver".equals(normalized) || "mongodb".equals(normalized);
    }

    private Object executeGetAndParse(final HttpUrl url, final String resourceDescription) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }
        final Request request = new Request.Builder().url(url).get().build();

        try (final Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException(String.format("Failed to fetch %s: HTTP %d %s - Response body: %s",
                        resourceDescription, response.code(), response.message(), errorBody));
            }
            if (response.body() == null) {
                return null;
            }
            final String bodyString = response.body().string().trim();
            if (bodyString.isEmpty()) {
                return null;
            }
            if (bodyString.startsWith("[")) {
                return objectMapper.readValue(bodyString, new TypeReference<List<Map<String, Object>>>() {});
            }
            return objectMapper.readValue(bodyString, new TypeReference<Map<String, Object>>() {});
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractInstances(final Object responseObj) {
        if (responseObj instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        if (responseObj instanceof Map<?, ?> map) {
            if (map.containsKey("instances") && map.get("instances") instanceof List<?> list) {
                return (List<Map<String, Object>>) (List<?>) list;
            }
            if (map.containsKey("items") && map.get("items") instanceof List<?> list) {
                return (List<Map<String, Object>>) (List<?>) list;
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractInstanceDetail(final Object responseObj, final String instanceId) {
        if (responseObj instanceof Map<?, ?> rawMap) {
            final Map<String, Object> map = (Map<String, Object>) rawMap;
            if (map.get("item") instanceof Map<?, ?> itemMap) {
                return (Map<String, Object>) itemMap;
            }
            if (map.containsKey("items") || map.containsKey("instances")) {
                final List<Map<String, Object>> list = extractInstances(responseObj);
                for (final Map<String, Object> candidate : list) {
                    if (candidate != null) {
                        final Object candidateId = candidate.get("id") != null
                                ? candidate.get("id")
                                : candidate.get("instanceId");
                        if (instanceId.equals(String.valueOf(candidateId))) {
                            return candidate;
                        }
                    }
                }
                return Collections.emptyMap();
            }
            return map;
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> mergeDetailMaps(final Map<String, Object> primary, final Map<String, Object> secondary) {
        if (primary == null || primary.isEmpty()) {
            return secondary != null ? secondary : Collections.emptyMap();
        }
        if (secondary == null || secondary.isEmpty()) {
            return primary;
        }
        final Map<String, Object> merged = new LinkedHashMap<>(primary);
        secondary.forEach((key, value) -> {
            if (value != null) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    private int extractTotalPages(final Map<String, Object> map, final int currentTotal) {
        if (map == null || !map.containsKey("pagination")) {
            return currentTotal;
        }
        final Object paginationObj = map.get("pagination");
        if (paginationObj instanceof Map<?, ?> paginationMap) {
            final Object totalPagesObj = paginationMap.get("totalPages");
            if (totalPagesObj instanceof Number num) {
                return num.intValue();
            } else if (totalPagesObj != null) {
                try {
                    return Integer.parseInt(totalPagesObj.toString());
                } catch (final NumberFormatException ignored) {
                    return currentTotal;
                }
            }
        }
        return currentTotal;
    }

    private static Map<String, String> initEngineBaseUrls(final Map<String, String> custom) {
        final Map<String, String> urls = new HashMap<>();
        urls.put("postgresql", DEFAULT_POSTGRES_FLEX_API_URL);
        urls.put("mongodb", DEFAULT_MONGODB_FLEX_API_URL);
        urls.put("opensearch", DEFAULT_OPENSEARCH_API_URL);
        urls.put("mariadb", DEFAULT_MARIADB_API_URL);
        urls.put("sqlserver", DEFAULT_SQLSERVER_FLEX_API_URL);

        if (custom != null) {
            custom.forEach((k, v) -> {
                if (k != null && v != null) {
                    urls.put(normalizeEngine(k), v.replaceAll("/+$", ""));
                }
            });
        }
        return Collections.unmodifiableMap(urls);
    }

    private static String normalizeEngine(final String engine) {
        if (engine == null) {
            return "postgresql";
        }
        final String lower = engine.toLowerCase().trim();
        if (lower.startsWith("postgres")) {
            return "postgresql";
        }
        if (lower.startsWith("mongo")) {
            return "mongodb";
        }
        if (lower.startsWith("sqlserver")) {
            return "sqlserver";
        }
        return lower;
    }
}
