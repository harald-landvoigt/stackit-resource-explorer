package com.landvoigtit.stackit.resourceexplorer.billing;

import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

@Slf4j
public class BillingApiClient {

    private final OkHttpClient httpClient;
    private final String apiUrl;

    public BillingApiClient(final OkHttpClient httpClient, final String apiUrl) {
        this.httpClient = httpClient;
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : StackitConstants.DEFAULT_BILLING_API_URL;
    }

    public String getProjectCosts(final String customerAccountId, final String from, final String to) throws IOException {
        final String url = StackitConstants.formatCostsUrl(apiUrl, customerAccountId, from, to);
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (final Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException(String.format("Failed to fetch project costs: HTTP %d %s - Response body: %s",
                        response.code(), response.message(), errorBody));
            }
            if (response.body() == null) {
                throw new IOException("Empty response body from Cost API");
            }
            return response.body().string();
        }
    }

    public String getInvoices(final String projectId, final String cursor) throws IOException {
        final HttpUrl.Builder urlBuilder = HttpUrl.parse(apiUrl + StackitConstants.formatProjectInvoicesPath(projectId)).newBuilder();
        if (cursor != null && !cursor.trim().isEmpty()) {
            urlBuilder.addQueryParameter("cursor", cursor);
        }
        final Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        try (final Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String errorBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("Failed to fetch invoices: HTTP " + response.code() + " " + response.message() + " - Response body: " + errorBody);
            }
            if (response.body() == null) {
                throw new IOException("Empty response body from Billing API");
            }
            return response.body().string();
        }
    }

    public String getOrgInvoices(final String orgId, final String cursor) throws IOException {
        final HttpUrl.Builder urlBuilder = HttpUrl.parse(apiUrl + StackitConstants.formatOrgInvoicesPath(orgId)).newBuilder();
        if (cursor != null && !cursor.trim().isEmpty()) {
            urlBuilder.addQueryParameter("cursor", cursor);
        }
        final Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        try (final Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String errorBody = response.body() != null ? response.body().string() : "null";
                throw new IOException("Failed to fetch organization invoices: HTTP " + response.code() + " " + response.message() + " - Response body: " + errorBody);
            }
            if (response.body() == null) {
                throw new IOException("Empty response body from Billing API");
            }
            return response.body().string();
        }
    }
}
