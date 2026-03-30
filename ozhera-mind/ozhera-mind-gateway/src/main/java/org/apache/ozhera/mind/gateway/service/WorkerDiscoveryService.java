/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ozhera.mind.gateway.service;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.gateway.router.AgentRouterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Discovers available Worker instances from Nacos.
 */
@Slf4j
@Service
public class WorkerDiscoveryService {

    @Value("${mind.worker.service.name:ozhera-mind-worker}")
    private String workerServiceName;

    @Resource(name = "nacosNamingService")
    private NamingService namingService;

    @Resource
    private AgentRouterService agentRouterService;

    /**
     * Get all healthy worker URLs
     */
    public List<String> getAvailableWorkers() {
        try {
            List<Instance> instances = namingService.selectInstances(workerServiceName, true);
            return instances.stream()
                    .map(i -> "http://" + i.getIp() + ":" + i.getPort())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get workers from Nacos", e);
            return Collections.emptyList();
        }
    }

    /**
     * Select the best worker for creating a new agent (least connections)
     */
    public String selectWorkerForNewAgent() {
        List<String> workers = getAvailableWorkers();
        if (workers.isEmpty()) {
            throw new RuntimeException("No available workers");
        }

        String selectedWorker = null;
        long minAgentCount = Long.MAX_VALUE;

        for (String workerUrl : workers) {
            long agentCount = agentRouterService.getWorkerAgentCount(workerUrl);
            if (agentCount < minAgentCount) {
                minAgentCount = agentCount;
                selectedWorker = workerUrl;
            }
        }

        log.info("Selected worker {} with {} agents", selectedWorker, minAgentCount);
        return selectedWorker;
    }
}
