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
package org.apache.ozhera.mind.service.service.impl;

import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.session.Session;
import io.agentscope.core.session.redis.jedis.JedisSession;
import io.agentscope.core.state.SimpleSessionKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.redis.RedisService;
import org.apache.ozhera.mind.service.service.MemoryStateService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * Implementation of MemoryStateService using Redis for persistence.
 */
@Slf4j
@Service
public class MemoryStateServiceImpl implements MemoryStateService {

    private static final String SESSION_KEY_PREFIX = "mind_agent";
    private static final String REDIS_KEY_PREFIX = "hera:mind:memory:";

    @Resource
    private RedisService redisService;

    private Session session;

    @PostConstruct
    public void init() {
        if (redisService.isClusterMode()) {
            log.warn("Redis cluster mode is not supported for JedisSession, memory state persistence disabled");
            return;
        }

        if (redisService.getJedisPool() == null) {
            log.warn("JedisPool is null, memory state persistence disabled");
            return;
        }

        // Use agentscope's JedisSession
        // Note: JedisSession doesn't support TTL directly, Redis keys persist until explicit deletion
        this.session = JedisSession.builder()
                .jedisPool(redisService.getJedisPool())
                .keyPrefix(REDIS_KEY_PREFIX)
                .build();

        log.info("MemoryStateService initialized with JedisSession, keyPrefix={}", REDIS_KEY_PREFIX);
    }

    @Override
    public void saveState(String username, AutoContextMemory memory) {
        if (session == null) {
            log.warn("Session not initialized, cannot save state for user: {}", username);
            return;
        }

        try {
            SimpleSessionKey sessionKey = SimpleSessionKey.of(SESSION_KEY_PREFIX + ":" + username);

            // Use AutoContextMemory's built-in saveTo method
            memory.saveTo(session, sessionKey);

            log.debug("Saved memory state for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to save memory state for user: {}", username, e);
        }
    }

    @Override
    public boolean loadState(String username, AutoContextMemory memory) {
        if (session == null) {
            log.warn("Session not initialized, cannot load state for user: {}", username);
            return false;
        }

        try {
            SimpleSessionKey sessionKey = SimpleSessionKey.of(SESSION_KEY_PREFIX + ":" + username);

            // Use AutoContextMemory's built-in loadFrom method
            memory.loadFrom(session, sessionKey);

            log.info("Loaded memory state for user: {}", username);
            return true;
        } catch (Exception e) {
            log.error("Failed to load memory state for user: {}", username, e);
            return false;
        }
    }

    @Override
    public boolean hasState(String username) {
        if (session == null) {
            return false;
        }
        SimpleSessionKey sessionKey = SimpleSessionKey.of(SESSION_KEY_PREFIX + ":" + username);
        return session.exists(sessionKey);
    }

    @Override
    public void deleteState(String username) {
        if (session == null) {
            log.warn("Session not initialized, cannot delete state for user: {}", username);
            return;
        }

        try {
            SimpleSessionKey sessionKey = SimpleSessionKey.of(SESSION_KEY_PREFIX + ":" + username);
            session.delete(sessionKey);
            log.info("Deleted memory state for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to delete memory state for user: {}", username, e);
        }
    }
}
