package com.walmartlabs.concord.agentoperator.processqueue;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.sdk.Constants;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.net.URLEncoder;

public class ProcessQueueClient {

    private static final TypeReference<List<ProcessQueueEntry>> LIST_OF_PROCESS_QUEUE_ENTRIES = new TypeReference<List<ProcessQueueEntry>>() {
    };

    private final String baseUrl;
    private final String apiToken;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public ProcessQueueClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.client = initClient();
        this.objectMapper = new ObjectMapper();
    }

    public List<ProcessQueueEntry> query(String processStatus, int limit, String flavor, String clusterAlias) throws IOException {
        String queryUrl = baseUrl + "/api/v2/process/requirements?status=" + processStatus + "&limit=" + limit + "&startAt.len=";
        if (flavor != null) {
            queryUrl = queryUrl + "&requirements.agent.flavor.eq=" + flavor;
        }
        if (clusterAlias != null) {
            queryUrl = queryUrl + "&requirements.agent.clusterAlias.regexp=" + URLEncoder.encode(clusterAlias, StandardCharsets.UTF_8);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(queryUrl))
                .header("Authorization", apiToken)
                .header("User-Agent", "k8s-agent-operator")
                .header(Constants.Headers.ENABLE_HTTP_SESSION, "true")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching the queue data", e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Error while fetching the process queue data: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), LIST_OF_PROCESS_QUEUE_ENTRIES);
    }


    private static HttpClient initClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .cookieHandler(cookieManager)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Error while initializing the HTTP client", e);
        }
    }
}
