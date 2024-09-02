package com.walmartlabs.concord.agentoperator.monitoring;

import com.walmartlabs.concord.agentoperator.crd.AgentPool;

public class NopMonitoringClient implements MonitoringClient {

    @Override
    public void onStart() {
        // do nothign
    }

    @Override
    public void onAgentPoolAdded(String resourceName, AgentPool resource) {
        // do nothign
    }

    @Override
    public void onAgentPoolDeleted(String resourceName) {
        // do nothign
    }
}
