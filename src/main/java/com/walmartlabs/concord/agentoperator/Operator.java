package com.walmartlabs.concord.agentoperator;

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


import com.walmartlabs.concord.agentoperator.agent.AgentClientFactory;
import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import com.walmartlabs.concord.agentoperator.crd.AgentPoolList;
import com.walmartlabs.concord.agentoperator.monitoring.MonitoringClientFactory;
import com.walmartlabs.concord.agentoperator.scheduler.AutoScalerFactory;
import com.walmartlabs.concord.agentoperator.scheduler.Scheduler;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;

import static com.walmartlabs.concord.agentoperator.scheduler.Event.Type.DELETED;
import static com.walmartlabs.concord.agentoperator.scheduler.Event.Type.MODIFIED;

public class Operator {

    private static final Logger log = LoggerFactory.getLogger(Operator.class);

    public static void main(String[] args) {
        // TODO support overloading the CRD with an external file?

        var namespace = getEnv("WATCH_NAMESPACE", "default");

        var baseUrl = getEnv("CONCORD_BASE_URL", "http://192.168.99.1:8001"); // use minikube/vbox host's default address
        var apiToken = getEnv("CONCORD_API_TOKEN", null);

        var monitoringClient = MonitoringClientFactory.create(baseUrl, apiToken, namespace);
        monitoringClient.onStart();

        // TODO use secrets for the token?
        var cfg = new Scheduler.Configuration(baseUrl, apiToken);
        var client = new DefaultKubernetesClient().inNamespace(namespace);
        var autoScalerFactory = new AutoScalerFactory(cfg, client);
        var agentClientFactory = new AgentClientFactory(true);
        var executor = Executors.newCachedThreadPool();

        var scheduler = new Scheduler(autoScalerFactory, client, monitoringClient, agentClientFactory);
        scheduler.start();

        // TODO retries
        log.info("main -> my watch begins... (namespace={})", namespace);
        var informer = client.resources(AgentPool.class, AgentPoolList.class).inAnyNamespace()
                .inform(new ResourceEventHandler<>() {

                    @Override
                    public void onAdd(AgentPool resource) {
                        executor.submit(() -> scheduler.onEvent(MODIFIED, resource));
                    }

                    @Override
                    public void onUpdate(AgentPool oldResource, AgentPool newResource) {
                        if (oldResource == newResource) {
                            return;
                        }
                        
                        executor.submit(() -> scheduler.onEvent(MODIFIED, newResource));
                    }

                    @Override
                    public void onDelete(AgentPool resource, boolean deletedFinalStateUnknown) {
                        executor.submit(() -> scheduler.onEvent(DELETED, resource));
                    }
                }, 0);

        try {
            informer.run();
        } catch (Exception e) {
            log.error("Error while watching for CRs (namespace={})", namespace, e);
            System.exit(2);
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String s = System.getenv(key);
        if (s == null) {
            return defaultValue;
        }

        return s;
    }
}
