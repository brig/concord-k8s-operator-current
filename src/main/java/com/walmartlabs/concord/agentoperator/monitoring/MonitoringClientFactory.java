package com.walmartlabs.concord.agentoperator.monitoring;

public class MonitoringClientFactory {

    public static MonitoringClient create(String baseUrl, String apiToken, String namespace) {
        String orgName = getEnv("ORG_NAME");
        String storeName = getEnv("STORE_NAME");
        String clusterAlias = getEnv("CLUSTER_ALIAS");
        String version = getEnv("VERSION");

        if (orgName == null) {
            return new NopMonitoringClient();
        } else {
            return new DefaultMonitoringClient(baseUrl, apiToken, namespace, orgName, storeName, clusterAlias, version);
        }
    }

    private static String getEnv(String key) {
        return System.getenv(key);
    }
}
