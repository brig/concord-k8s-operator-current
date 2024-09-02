package com.walmartlabs.concord.agentoperator.monitoring;

import com.walmartlabs.concord.agentoperator.crd.AgentPool;

public interface MonitoringClient {

    void onStart();

    void onAgentPoolAdded(String resourceName, AgentPool resource);

    void onAgentPoolDeleted(String resourceName);
}
