package com.walmartlabs.concord.agentoperator.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.net.http.HttpClient.Redirect.NEVER;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DefaultMonitoringClient implements MonitoringClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMonitoringClient.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String apiToken;
    private final HttpClient client;

    private final String orgName;
    private final String storeName;
    private final String clusterAlias;
    private final String namespace;
    private final String version;

    public DefaultMonitoringClient(String baseUrl, String apiToken,
                            String orgName, String storeName, String clusterAlias, String namespace, String version) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.orgName = orgName;
        this.storeName = storeName;
        this.clusterAlias = clusterAlias;
        this.namespace = namespace;
        this.version = version;
    }

    @Override
    public void onStart() {
        String key = clusterAlias + "/" + namespace;

        Map<String, Object> data = new HashMap<>();
        data.put("clusterAlias", clusterAlias);
        data.put("namespace", namespace);
        data.put("version", version);
        data.put("startedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));

        withRetry( () -> {
            putData(orgName, storeName, key, data);
            return null;
        });
    }

    @Override
    public void onAgentPoolAdded(String resourceName, AgentPool resource) {
        String key = clusterAlias + "/" + namespace + "/" + resourceName;
        Map<String, Object> data;
        try {
            data = objectMapper.convertValue(resource, Map.class);
        } catch (Exception e) {
            log.error("onAgentPoolAdded ['{}'] -> error convert to map", resourceName, e);
            return;
        }

        withRetry(() -> {
            putData(orgName, storeName, key, data);
            return null;
        });
    }

    @Override
    public void onAgentPoolDeleted(String resourceName) {
        String key = clusterAlias + "/" + namespace + "/" + resourceName;

        withRetry(() -> {
            deleteData(orgName, storeName, key);
            return null;
        });
    }

    private void putData(String orgName, String storeName, String itemPath, Map<String, Object> data) throws Exception {
        String localVarPath = "/api/v1/org/{orgName}/jsonstore/{storeName}/item/{itemPath}"
                .replace("{orgName}", urlEncode(orgName))
                .replace("{storeName}", urlEncode(storeName))
                .replace("{itemPath}", urlEncode(itemPath));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + localVarPath))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(data)))
                .header("Authorization", apiToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, application/vnd.siesta-validation-errors-v1+json")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(response);
        }
    }

    private void deleteData(String orgName, String storeName, String itemPath) throws Exception {
        String localVarPath = "/api/v1/org/{orgName}/jsonstore/{storeName}/item/{itemPath}"
                .replace("{orgName}", urlEncode(orgName))
                .replace("{storeName}", urlEncode(storeName))
                .replace("{itemPath}", urlEncode(itemPath));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + localVarPath))
                .DELETE()
                .header("Authorization", apiToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, application/vnd.siesta-validation-errors-v1+json")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(response);
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, UTF_8).replaceAll("\\+", "%20");
    }

    private static <T> T withRetry(Callable<T> c) {
        int tryCount = 0;
        while (!Thread.currentThread().isInterrupted() && tryCount < RETRY_COUNT + 1) {
            try {
                return c.call();
            } catch (ApiException e) {
                if (e.getCode() >= 400 && e.getCode() < 500) {
                    break;
                }
                log.warn("call error: '{}'", e.getResponseBody());
            } catch (ClosedChannelException e) {
                log.error("call error: closed channel");
            } catch (Exception e) {
                log.error("call error", e);
            }
            log.info("retry after {} sec", RETRY_INTERVAL / 1000);
            sleep(RETRY_INTERVAL);
            tryCount++;
        }

        return null;
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ApiException extends Exception {

        private final HttpResponse<String> response;

        public ApiException(HttpResponse<String> response) {
            this.response = response;
        }

        public int getCode() {
            return response.statusCode();
        }

        public String getResponseBody() {
            return response.body();
        }
    }
}
