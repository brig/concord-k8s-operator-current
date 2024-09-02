package com.walmartlabs.concord.agentoperator.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitoringClientFactory {

    private static final Logger log = LoggerFactory.getLogger(MonitoringClientFactory.class);

    public static MonitoringClient create(String baseUrl, String apiToken, String namespace) {
        String orgName = getEnv("ORG_NAME");
        String storeName = getEnv("STORE_NAME");
        String clusterAlias = getEnv("CLUSTER_ALIAS");
        String version = getEnv("VERSION");

        if (orgName == null || storeName == null || clusterAlias == null || version == null) {
            log.info("using NopMonitoringClient");
            return new NopMonitoringClient();
        } else {
            log.info("using DefaultMonitoringClient");
            return new DefaultMonitoringClient(baseUrl, apiToken, namespace, orgName, storeName, clusterAlias, version);
        }
    }

    private static String getEnv(String key) {
        return System.getenv(key);
    }
}
