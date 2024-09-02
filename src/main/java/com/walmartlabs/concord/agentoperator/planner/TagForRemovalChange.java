package com.walmartlabs.concord.agentoperator.planner;

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

import com.walmartlabs.concord.agentoperator.AgentClient;
import com.walmartlabs.concord.agentoperator.PodUtils;
import com.walmartlabs.concord.agentoperator.resources.AgentPod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TagForRemovalChange implements Change {

    private static final Logger log = LoggerFactory.getLogger(TagForRemovalChange.class);

    private final String podName;
    private final String podIp;
    private final AgentClient agentClient;

    public TagForRemovalChange(String podName, AgentClient agentClient, String podIp) {
        this.podName = podName;
        this.podIp = podIp;
        this.agentClient = agentClient;
    }

    @Override
    public void apply(KubernetesClient client) {
        try {
            agentClient.enableMaintenanceMode(podIp);
            log.info("Maintenance mode for pod '{}'@'{}' enabled", podName, podIp);
        } catch (Exception e) {
            log.error("Error while maintenance mode for pod '{}'@'{}'", podName, podIp, e);
            return;
        }

        PodUtils.applyTag(client, podName, AgentPod.TAGGED_FOR_REMOVAL_LABEL, "true");
    }

    @Override
    public String toString() {
        return "TagForRemovalChange{" +
                "podName='" + podName + '\'' +
                '}';
    }
}
