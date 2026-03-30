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
package org.apache.ozhera.mind.gateway.router;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.gateway.redis.RedisService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Manages the mapping between Agent and Worker.
 * Stores agentId -> workerId mapping in Redis for routing requests.
 */
@Slf4j
@Service
public class AgentRouterService {

    private static final String AGENT_WORKER_PREFIX = "mind:agent:worker:";
    private static final String WORKER_AGENT_COUNT_PREFIX = "mind:worker:agent:count:";
    private static final long AGENT_EXPIRE_SECONDS = 24 * 60 * 60;

    @Resource
    private RedisService redisService;

    /**
     * Bind agent to a specific worker
     */
    public void bindAgentToWorker(String agentId, String workerId) {
        String key = AGENT_WORKER_PREFIX + agentId;
        redisService.setEx(key, AGENT_EXPIRE_SECONDS, workerId);
        redisService.incr(WORKER_AGENT_COUNT_PREFIX + workerId);
        log.info("Bound agent {} to worker {}", agentId, workerId);
    }

    /**
     * Get the worker ID for an agent
     */
    public String getWorkerForAgent(String agentId) {
        String key = AGENT_WORKER_PREFIX + agentId;
        return redisService.get(key);
    }

    /**
     * Unbind agent from worker (when agent is destroyed)
     */
    public void unbindAgent(String agentId) {
        String key = AGENT_WORKER_PREFIX + agentId;
        String workerId = redisService.get(key);
        if (workerId != null) {
            redisService.del(key);
            redisService.decr(WORKER_AGENT_COUNT_PREFIX + workerId);
            log.info("Unbound agent {} from worker {}", agentId, workerId);
        }
    }

    /**
     * Get agent count for a worker (for load balancing)
     */
    public long getWorkerAgentCount(String workerId) {
        String countStr = redisService.get(WORKER_AGENT_COUNT_PREFIX + workerId);
        return countStr != null ? Long.parseLong(countStr) : 0;
    }

    /**
     * Refresh agent expiration time (keep alive)
     */
    public void refreshAgentExpiration(String agentId) {
        String key = AGENT_WORKER_PREFIX + agentId;
        redisService.expire(key, AGENT_EXPIRE_SECONDS);
    }
}
