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
package org.apache.ozhera.mind.service.swarm;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.redis.RedisService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Service for persisting SwarmSession to Redis.
 * Tracks which agent the user is currently interacting with.
 */
@Slf4j
@Service
public class SwarmSessionService {

    private static final String SESSION_KEY_PREFIX = "hera:mind:session:";
    private static final long SESSION_TTL_SECONDS = 24 * 60 * 60; // 24 hours

    @Resource
    private RedisService redisService;

    @Resource
    private Gson gson;

    /**
     * Save session to Redis.
     */
    public void saveSession(String username, SwarmSession session) {
        try {
            String key = SESSION_KEY_PREFIX + username;
            String json = gson.toJson(session);
            redisService.setEx(key, SESSION_TTL_SECONDS, json);
            log.debug("Saved session for user: {}, currentAgent: {}", username, session.getCurrentAgentName());
        } catch (Exception e) {
            log.error("Failed to save session for user: {}", username, e);
        }
    }

    /**
     * Load session from Redis.
     */
    public SwarmSession loadSession(String username) {
        try {
            String key = SESSION_KEY_PREFIX + username;
            String json = redisService.get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return gson.fromJson(json, SwarmSession.class);
        } catch (Exception e) {
            log.error("Failed to load session for user: {}", username, e);
            return null;
        }
    }

    /**
     * Get or create session.
     */
    public SwarmSession getOrCreate(String username) {
        SwarmSession session = loadSession(username);
        if (session == null) {
            session = SwarmSession.create(username);
            log.info("Created new session for user: {}", username);
        }
        return session;
    }

    /**
     * Delete session.
     */
    public void deleteSession(String username) {
        try {
            String key = SESSION_KEY_PREFIX + username;
            redisService.del(key);
            log.info("Deleted session for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to delete session for user: {}", username, e);
        }
    }
}
