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
package org.apache.ozhera.mind.service.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Manages agent cache cleanup and Redis user-worker mapping.
 * When an agent is evicted from local cache, this service cleans up the Redis mapping.
 */
@Slf4j
@Service
public class AgentCacheManager {

    private static final String USER_WORKER_PREFIX = "hera:mind:user:worker:";
    private static final String WORKER_USER_COUNT_PREFIX = "hera:mind:worker:user:count:";

    @Resource
    private RedisService redisService;

    /**
     * Called when an agent is evicted from local cache.
     * Cleans up the user-worker mapping in Redis so Gateway can reassign.
     */
    public void onAgentEvicted(String username) {
        try {
            String userWorkerKey = USER_WORKER_PREFIX + username;
            String workerUrl = redisService.get(userWorkerKey);

            if (workerUrl != null) {
                // Delete user-worker mapping
                redisService.del(userWorkerKey);
                // Decrement worker user count
                redisService.decr(WORKER_USER_COUNT_PREFIX + workerUrl);
                log.info("Cleaned up Redis mapping for evicted agent: user={}, worker={}", username, workerUrl);
            }
        } catch (Exception e) {
            log.error("Failed to clean up Redis mapping for user: {}", username, e);
        }
    }
}
