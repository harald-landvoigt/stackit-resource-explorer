package com.landvoigtit.stackit.resourceexplorer;

import cloud.stackit.sdk.core.config.CoreConfiguration;
import okhttp3.OkHttpClient;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import okhttp3.Protocol;
import cloud.stackit.sdk.iaas.v1api.api.IaasApi;
import cloud.stackit.sdk.iaas.v1api.model.Server;
import cloud.stackit.sdk.iaas.v1api.model.ServerListResponse;
import cloud.stackit.sdk.iaas.v1api.model.Network;
import cloud.stackit.sdk.iaas.v1api.model.NetworkListResponse;
import cloud.stackit.sdk.iaas.v1api.model.Volume;
import cloud.stackit.sdk.iaas.v1api.model.VolumeListResponse;
import cloud.stackit.sdk.alb.v2api.api.AlbApi;
import cloud.stackit.sdk.alb.v2api.model.LoadBalancer;
import cloud.stackit.sdk.alb.v2api.model.ListLoadBalancersResponse;
import cloud.stackit.sdk.objectstorage.v1api.api.ObjectStorageApi;
import cloud.stackit.sdk.objectstorage.v1api.model.Bucket;
import cloud.stackit.sdk.objectstorage.v1api.model.ListBucketsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.api.ResourceManagerApi;
import cloud.stackit.sdk.resourcemanager.v0api.model.Project;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListProjectsResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.ListFoldersResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.GetProjectResponse;
import cloud.stackit.sdk.resourcemanager.v0api.model.Parent;
import cloud.stackit.sdk.resourcemanager.v0api.model.ParentListInner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import io.quarkus.test.Mock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import com.landvoigtit.stackit.resourceexplorer.billing.BillingApiClient;
import com.landvoigtit.stackit.resourceexplorer.config.StackitSdkConfig;

@Mock
@ApplicationScoped
public class StackitSdkMockProducer {

    private static final String credentialsPath = createDummyCredentialsJson();
    private static final CoreConfiguration mockConfig = new CoreConfiguration().serviceAccountKeyPath(credentialsPath);
    public static final UUID MOCK_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static String getCredentialsPath() {
        return credentialsPath;
    }

    private static String createDummyCredentialsJson() {
        try {
            final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            final KeyPair kp = kpg.generateKeyPair();
            final byte[] priv = kp.getPrivate().getEncoded();

            final String base64 = Base64.getMimeEncoder().encodeToString(priv);
            final String pem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
            final String escapedPem = pem.replace("\n", "\\n");

            final String json = "{\n" +
                    "  \"projectId\": \"8a784558-b50d-4553-b4e7-19e843d2e279\",\n" +
                    "  \"id\": \"00000000-0000-0000-0000-000000000000\",\n" +
                    "  \"active\": true,\n" +
                    "  \"credentials\": {\n" +
                    "    \"kid\": \"mock-key-id\",\n" +
                    "    \"iss\": \"mock-sa@sa.stackit.cloud\",\n" +
                    "    \"sub\": \"00000000-0000-0000-0000-000000000000\",\n" +
                    "    \"aud\": \"https://iaas.eu01.stackit.cloud\",\n" +
                    "    \"privateKey\": \"" + escapedPem + "\"\n" +
                    "  }\n" +
                    "}";

            final Path tempFile = Files.createTempFile("stackit-mock-creds", ".json");
            Files.writeString(tempFile, json);
            tempFile.toFile().deleteOnExit();

            return tempFile.toAbsolutePath().toString();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to generate dummy credentials JSON", e);
        }
    }

    public static class MockIaasApi extends IaasApi {
        public MockIaasApi() throws IOException {
            super(mockConfig);
        }
        @Override
        public ServerListResponse listServers(final UUID projectId, final Boolean detail, final String cursor) {
            final ServerListResponse resp = new ServerListResponse();
            final Server s = new Server(
                java.time.OffsetDateTime.now(),
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                "RUNNING",
                "RUNNING",
                java.time.OffsetDateTime.now()
            );
            s.setName("mock-vm");
            s.setMachineType("g1.small");
            s.setImageId(UUID.randomUUID());
            resp.setItems(List.of(s));
            return resp;
        }

        @Override
        public NetworkListResponse listNetworks(final UUID projectId, final String cursor) {
            final NetworkListResponse resp = new NetworkListResponse();
            final Network n = new Network();
            n.setNetworkId(UUID.randomUUID());
            n.setName("mock-network");
            n.setState("ACTIVE");
            n.setPrefixes(List.of("10.0.0.0/24"));
            n.setGateway("10.0.0.1");
            n.setRouted(true);
            resp.setItems(List.of(n));
            return resp;
        }

        @Override
        public VolumeListResponse listVolumes(final UUID projectId, final String cursor) {
            final VolumeListResponse resp = new VolumeListResponse();
            final Volume v = new Volume(
                java.time.OffsetDateTime.now(),
                false,
                UUID.randomUUID(),
                null,
                null,
                "AVAILABLE",
                java.time.OffsetDateTime.now()
            );
            v.setName("mock-volume");
            v.setSize(20L);
            v.setPerformanceClass("storage_premium_perf1");
            v.setAvailabilityZone("eu01-1");
            v.setBootable(false);
            resp.setItems(List.of(v));
            return resp;
        }
    }

    public static class MockObjectStorageApi extends ObjectStorageApi {
        public MockObjectStorageApi() throws IOException {
            super(mockConfig);
        }
        @Override
        public ListBucketsResponse listBuckets(final String projectId) {
            final ListBucketsResponse resp = new ListBucketsResponse();
            final Bucket b = new Bucket();
            b.setName("mock-bucket");
            b.setRegion("eu-central-1");
            resp.setBuckets(List.of(b));
            return resp;
        }
    }

    public static class MockAlbApi extends AlbApi {
        public MockAlbApi() throws IOException {
            super(mockConfig);
        }
        @Override
        public ListLoadBalancersResponse listLoadBalancers(final String projectId, final String region, final String pageSize, final String pageId) {
            final ListLoadBalancersResponse resp = new ListLoadBalancersResponse();
            final LoadBalancer lb = new LoadBalancer();
            lb.setName("mock-lb");
            lb.setExternalAddress("192.168.1.1");
            resp.setLoadBalancers(List.of(lb));
            return resp;
        }
    }

    public static class MockResourceManagerApi extends ResourceManagerApi {
        public MockResourceManagerApi() throws IOException {
            super(mockConfig);
        }
        @Override
        public ListProjectsResponse listProjects(final String containerId, final List<String> projectIds, final String query, final java.math.BigDecimal limit, final java.math.BigDecimal offset, final java.time.OffsetDateTime creationTime) {
            final ListProjectsResponse resp = new ListProjectsResponse();
            final Project p = new Project();
            p.setProjectId(MOCK_PROJECT_ID);
            p.setName("mock-project");
            p.setContainerId("container-123");
            resp.setItems(List.of(p));
            return resp;
        }
        @Override
        public ListFoldersResponse listFolders(final String containerId, final List<String> folderIds, final String query, final java.math.BigDecimal limit, final java.math.BigDecimal offset, final java.time.OffsetDateTime creationTime) {
            final ListFoldersResponse resp = new ListFoldersResponse();
            resp.setItems(List.of());
            return resp;
        }
        @Override
        public GetProjectResponse getProject(final String projectId, final Boolean detail) {
            final GetProjectResponse resp = new GetProjectResponse();
            resp.setProjectId(UUID.fromString(projectId));
            resp.setName("mock-project");
            resp.setContainerId("container-123");
            final Parent parent = new Parent();
            parent.setContainerId("org-123");
            parent.setType(Parent.TypeEnum.ORGANIZATION);
            parent.setId(UUID.fromString("869271ad-bde6-4e7b-99a1-0c2dab2b4171"));
            resp.setParent(parent);
            final ParentListInner parentListInner = new ParentListInner();
            parentListInner.setContainerId("org-123");
            parentListInner.setType(ParentListInner.TypeEnum.ORGANIZATION);
            parentListInner.setId(UUID.fromString("869271ad-bde6-4e7b-99a1-0c2dab2b4171"));
            resp.setParents(List.of(parentListInner));
            return resp;
        }
    }

    @Produces
    @Singleton
    public IaasApi iaasApi() throws IOException {
        return new MockIaasApi();
    }

    @Produces
    @Singleton
    public ObjectStorageApi objectStorageApi() throws IOException {
        return new MockObjectStorageApi();
    }

    @Produces
    @Singleton
    public AlbApi albApi() throws IOException {
        return new MockAlbApi();
    }

    @Produces
    @Singleton
    public ResourceManagerApi resourceManagerApi() throws IOException {
        return new MockResourceManagerApi();
    }

    public static class MockBillingApiClient extends BillingApiClient {
        public MockBillingApiClient() throws IOException {
            super(new okhttp3.OkHttpClient(), "http://localhost");
        }
        @Override
        public String getInvoices(final String projectId, final String cursor) {
            return "{\n" +
                    "  \"invoices\": [\n" +
                    "    {\n" +
                    "      \"id\": \"inv-123\",\n" +
                    "      \"invoiceNumber\": \"INV-2026-001\",\n" +
                    "      \"amount\": 1250.50,\n" +
                    "      \"currency\": \"EUR\",\n" +
                    "      \"status\": \"PAID\",\n" +
                    "      \"billingDate\": \"2026-08-01T00:00:00Z\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"nextCursor\": null\n" +
                    "}";
        }
        @Override
        public String getOrgInvoices(final String orgId, final String cursor) {
            return "{\n" +
                    "  \"invoices\": [\n" +
                    "    {\n" +
                    "      \"id\": \"org-inv-123\",\n" +
                    "      \"invoiceNumber\": \"ORG-INV-2026-001\",\n" +
                    "      \"amount\": 5000.00,\n" +
                    "      \"currency\": \"EUR\",\n" +
                    "      \"status\": \"PAID\",\n" +
                    "      \"billingDate\": \"2026-08-01T00:00:00Z\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"nextCursor\": null\n" +
                    "}";
        }

        @Override
        public String getProjectCosts(final String customerAccountId, final String from, final String to) {
            return "[\n" +
                    "  {\n" +
                    "    \"customerAccountId\": \"" + customerAccountId + "\",\n" +
                    "    \"projectId\": \"project-123\",\n" +
                    "    \"projectName\": \"test-project\",\n" +
                    "    \"totalCharge\": 125050.0,\n" +
                    "    \"totalDiscount\": 0.0\n" +
                    "  }\n" +
                    "]";
        }
    }

    @Produces
    @Singleton
    public BillingApiClient billingApiClient() throws IOException {
        return new MockBillingApiClient();
    }

    @Produces
    @Singleton
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(final Chain chain) throws IOException {
                        final String url = chain.request().url().toString();
                        if (url.contains("/members")) {
                            final String json = "{\n" +
                                    "  \"resourceId\": \"00000000-0000-0000-0000-000000000000\",\n" +
                                    "  \"resourceType\": \"project\",\n" +
                                    "  \"members\": [\n" +
                                    "    {\n" +
                                    "      \"subject\": \"sa-1@stackit.de\",\n" +
                                    "      \"role\": \"project.owner\"\n" +
                                    "    },\n" +
                                    "    {\n" +
                                    "      \"subject\": \"sa-2@stackit.de\",\n" +
                                    "      \"role\": \"project.member\"\n" +
                                    "    }\n" +
                                    "  ]\n" +
                                    "}";
                            return new Response.Builder()
                                    .request(chain.request())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                    .build();
                        } else if (url.contains("/tokens")) {
                            if (url.contains("legacy")) {
                                final String json = "{\n" +
                                        "  \"items\": [\n" +
                                        "    {\n" +
                                        "      \"id\": \"tok-legacy-01\",\n" +
                                        "      \"validUntil\": \"2027-12-31T23:59:59Z\",\n" +
                                        "      \"createdAt\": \"2024-01-01T00:00:00Z\"\n" +
                                        "    }\n" +
                                        "  ]\n" +
                                        "}";
                                return new Response.Builder()
                                        .request(chain.request())
                                        .protocol(Protocol.HTTP_1_1)
                                        .code(200)
                                        .message("OK")
                                        .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                        .build();
                            } else {
                                final String json = "{\n" +
                                        "  \"items\": []\n" +
                                        "}";
                                return new Response.Builder()
                                        .request(chain.request())
                                        .protocol(Protocol.HTTP_1_1)
                                        .code(200)
                                        .message("OK")
                                        .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                        .build();
                            }
                        } else if (url.contains("/keys")) {
                            final String json = "{\n" +
                                    "  \"items\": []\n" +
                                    "}";
                            return new Response.Builder()
                                    .request(chain.request())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                    .build();
                        } else if (url.contains("/service-accounts")) {
                            final String json = "{\n" +
                                    "  \"items\": [\n" +
                                    "    {\n" +
                                    "      \"email\": \"scraper-8mdqk4i8@sa.stackit.cloud\",\n" +
                                    "      \"id\": \"910ebfe0-201f-4a9a-9c86-cbf6936a94c2\",\n" +
                                    "      \"internal\": false,\n" +
                                    "      \"projectId\": \"00000000-0000-0000-0000-000000000000\"\n" +
                                    "    },\n" +
                                    "    {\n" +
                                    "      \"email\": \"legacy-sa@sa.stackit.cloud\",\n" +
                                    "      \"id\": \"legacy-sa-id\",\n" +
                                    "      \"internal\": false,\n" +
                                    "      \"projectId\": \"00000000-0000-0000-0000-000000000000\"\n" +
                                    "    }\n" +
                                    "  ]\n" +
                                    "}";
                            return new Response.Builder()
                                    .request(chain.request())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(ResponseBody.create(json, MediaType.parse("application/json")))
                                    .build();
                        }
                        return chain.proceed(chain.request());
                    }
                })
                .build();
    }
}
