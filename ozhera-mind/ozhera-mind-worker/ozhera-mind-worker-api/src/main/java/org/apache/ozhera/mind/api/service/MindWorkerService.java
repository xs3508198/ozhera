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

import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;

/**
 * Dubbo interface for Mind Worker.
 * Note: Gateway uses HTTP/WebClient for streaming support.
 * This interface is kept for potential non-streaming Dubbo calls.
 */
public interface MindWorkerService {

    /**
     * Send a chat message (non-streaming).
     * Agent is created automatically if not exists for the user.
     *
     * @param request chat request with username and message
     * @return chat response
     */
    ChatResponse chat(ChatRequest request);

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
        private int userCount;
        private long freeMemoryMb;
        private boolean healthy;

        public String getWorkerId() { return workerId; }
        public void setWorkerId(String workerId) { this.workerId = workerId; }
        public int getUserCount() { return userCount; }
        public void setUserCount(int userCount) { this.userCount = userCount; }
        public long getFreeMemoryMb() { return freeMemoryMb; }
        public void setFreeMemoryMb(long freeMemoryMb) { this.freeMemoryMb = freeMemoryMb; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
    }
}
