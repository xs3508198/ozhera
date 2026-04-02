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
package org.apache.ozhera.mind.gateway.task;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.gateway.router.AgentRouterService;
import org.apache.ozhera.mind.gateway.service.WorkerDiscoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled task to clean up invalid user-worker mappings in Redis.
 * This handles cases where workers are restarted/redeployed and their IPs change.
 */
@Slf4j
@Component
public class WorkerCleanupTask {

    @Resource
    private AgentRouterService agentRouterService;

    @Resource
    private WorkerDiscoveryService workerDiscoveryService;

    /**
     * Run cleanup every 5 minutes.
     * Removes mappings for users bound to dead workers and recalculates worker user counts.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void cleanupInvalidMappings() {
        log.info("Starting worker cleanup task");

        try {
            List<String> aliveWorkers = workerDiscoveryService.getAvailableWorkers();
            Map<String, String> allMappings = agentRouterService.getAllUserWorkerMappings();

            if (allMappings.isEmpty()) {
                log.debug("No user-worker mappings found");
                return;
            }

            // Track user counts per worker for recalculation
            Map<String, Long> workerUserCounts = new HashMap<>();
            int removedCount = 0;

            for (Map.Entry<String, String> entry : allMappings.entrySet()) {
                String username = entry.getKey();
                String workerUrl = entry.getValue();

                if (!aliveWorkers.contains(workerUrl)) {
                    // Worker is dead, unbind user
                    log.info("Removing invalid mapping: user {} -> dead worker {}", username, workerUrl);
                    agentRouterService.unbindUser(username);
                    removedCount++;
                } else {
                    // Worker is alive, count this user
                    workerUserCounts.merge(workerUrl, 1L, Long::sum);
                }
            }

            // Recalculate and update worker user counts
            for (String workerUrl : aliveWorkers) {
                long count = workerUserCounts.getOrDefault(workerUrl, 0L);
                agentRouterService.resetWorkerUserCount(workerUrl, count);
            }

            // Clean up counts for dead workers
            for (Map.Entry<String, String> entry : allMappings.entrySet()) {
                String workerUrl = entry.getValue();
                if (!aliveWorkers.contains(workerUrl)) {
                    agentRouterService.deleteWorkerUserCount(workerUrl);
                }
            }

            log.info("Worker cleanup completed: removed {} invalid mappings, {} alive workers",
                    removedCount, aliveWorkers.size());

        } catch (Exception e) {
            log.error("Worker cleanup task failed", e);
        }
    }
}
