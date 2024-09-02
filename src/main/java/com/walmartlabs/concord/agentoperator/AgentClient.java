package com.walmartlabs.concord.agentoperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static java.net.http.HttpClient.Redirect.NEVER;

public class AgentClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient client;

    public AgentClient() {
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void enableMaintenanceMode(String podIp) throws Exception {
        if (podIp == null) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://%s:8010/maintenance-mode".formatted(podIp)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public boolean isNoWorkers(String podIp) throws Exception {
        if (podIp == null) {
            return true;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://%s:8010/maintenance-mode".formatted(podIp)))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        MaintenanceMode entity = objectMapper.readValue(response.body(), MaintenanceMode.class);
        return entity.maintenanceMode() && entity.workersAlive() == 0;
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableMaintenanceMode.class)
    @JsonDeserialize(as = ImmutableMaintenanceMode.class)
    public interface MaintenanceMode {

        boolean maintenanceMode();

        int workersAlive();
    }
}
