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
import org.apache.ozhera.mind.gateway.service.WorkerDiscoveryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Manages the mapping between User and Worker.
 * Stores username -> workerId mapping in Redis for routing requests.
 * Each user is bound to a specific worker for session affinity.
 */
@Slf4j
@Service
public class AgentRouterService {

    private static final String USER_WORKER_PREFIX = "mind:user:worker:";
    private static final String WORKER_USER_COUNT_PREFIX = "mind:worker:user:count:";
    private static final long USER_EXPIRE_SECONDS = 24 * 60 * 60;

    @Resource
    private RedisService redisService;

    @Resource
    private WorkerDiscoveryService workerDiscoveryService;

    /**
     * Get worker for user, or assign one if not exists.
     */
    public String getOrAssignWorker(String username) {
        String key = USER_WORKER_PREFIX + username;
        String workerId = redisService.get(key);

        if (workerId != null) {
            log.debug("Found existing worker {} for user {}", workerId, username);
            refreshUserExpiration(username);
            return workerId;
        }

        // Select a worker for this user
        workerId = workerDiscoveryService.selectWorkerForNewAgent();
        bindUserToWorker(username, workerId);
        return workerId;
    }

    /**
     * Bind user to a specific worker
     */
    public void bindUserToWorker(String username, String workerId) {
        String key = USER_WORKER_PREFIX + username;
        redisService.setEx(key, USER_EXPIRE_SECONDS, workerId);
        redisService.incr(WORKER_USER_COUNT_PREFIX + workerId);
        log.info("Bound user {} to worker {}", username, workerId);
    }

    /**
     * Unbind user from worker
     */
    public void unbindUser(String username) {
        String key = USER_WORKER_PREFIX + username;
        String workerId = redisService.get(key);
        if (workerId != null) {
            redisService.del(key);
            redisService.decr(WORKER_USER_COUNT_PREFIX + workerId);
            log.info("Unbound user {} from worker {}", username, workerId);
        }
    }

    /**
     * Get user count for a worker (for load balancing)
     */
    public long getWorkerUserCount(String workerId) {
        String countStr = redisService.get(WORKER_USER_COUNT_PREFIX + workerId);
        return countStr != null ? Long.parseLong(countStr) : 0;
    }

    /**
     * Refresh user expiration time (keep alive)
     */
    public void refreshUserExpiration(String username) {
        String key = USER_WORKER_PREFIX + username;
        redisService.expire(key, USER_EXPIRE_SECONDS);
    }
}
