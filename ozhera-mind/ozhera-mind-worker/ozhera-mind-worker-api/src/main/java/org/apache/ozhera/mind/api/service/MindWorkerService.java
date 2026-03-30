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
package org.apache.ozhera.mind.api.service;

import org.apache.ozhera.mind.api.dto.AgentCreateRequest;
import org.apache.ozhera.mind.api.dto.AgentCreateResponse;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;

/**
 * Dubbo interface for Mind Worker.
 * Gateway calls Worker through this interface.
 */
public interface MindWorkerService {

    /**
     * Create a new agent on this worker
     *
     * @param request agent creation request
     * @return agent creation response with agentId
     */
    AgentCreateResponse createAgent(AgentCreateRequest request);

    /**
     * Send a chat message to an existing agent
     *
     * @param request chat request with agentId and message
     * @return chat response
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Destroy an agent
     *
     * @param agentId the agent ID to destroy
     * @return true if successful
     */
    boolean destroyAgent(String agentId);

    /**
     * Check if an agent exists on this worker
     *
     * @param agentId the agent ID to check
     * @return true if exists
     */
    boolean agentExists(String agentId);

    /**
     * Get worker status (for health check and load balancing)
     *
     * @return worker status info
     */
    WorkerStatus getWorkerStatus();

    /**
     * Worker status information
     */
    class WorkerStatus {
        private String workerId;
        private int agentCount;
        private long freeMemoryMb;
        private boolean healthy;

        // Getters and setters
        public String getWorkerId() { return workerId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        public int getAgentCount() { return agentCount; }
        public void setAgentCount(int agentCount) { this.agentCount = agentCount; }
        public long getFreeMemoryMb() { return freeMemoryMb; }
        public void setFreeMemoryMb(long freeMemoryMb) { this.freeMemoryMb = freeMemoryMb; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
    }
}
