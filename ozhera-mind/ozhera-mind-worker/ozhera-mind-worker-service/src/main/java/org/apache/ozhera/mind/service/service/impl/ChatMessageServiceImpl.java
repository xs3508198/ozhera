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

import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.google.gson.Gson;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.dao.mapper.ChatMessageMapper;
import org.apache.ozhera.mind.service.entity.ChatMessage;
import org.apache.ozhera.mind.service.redis.RedisService;
import org.apache.ozhera.mind.service.service.ChatMessageService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Chat message service implementation.
 * Uses Redis as cache for recent messages, MySQL for persistence.
 */
@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final String CHAT_HISTORY_PREFIX = "hera:mind:chat:history:";
    private static final Gson GSON = new Gson();

    @NacosValue(value = "${chat.history.redis.max-size:100}", autoRefreshed = true)
    private int redisMaxSize;

    @NacosValue(value = "${chat.history.redis.expire-hours:24}", autoRefreshed = true)
    private int redisExpireHours;

    @Resource
    private RedisService redisService;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Override
    public void saveMessage(String username, String role, String content) {
        ChatMessage message = ChatMessage.builder()
                .username(username)
                .role(role)
                .content(content)
                .createdAt(System.currentTimeMillis())
                .build();

        // Save to Redis immediately
        saveToRedis(username, message);

        // Async save to MySQL
        asyncSaveToMySQL(message);
    }

    private void saveToRedis(String username, ChatMessage message) {
        try {
            String key = CHAT_HISTORY_PREFIX + username;
            String json = GSON.toJson(message);

            // Append to list
            redisService.rpush(key, json);

            // Trim to max size (keep latest N messages)
            long len = redisService.llen(key);
            if (len > redisMaxSize) {
                redisService.ltrim(key, len - redisMaxSize, -1);
            }

            // Set/refresh expiration
            redisService.expire(key, redisExpireHours * 3600L);

            log.debug("Saved message to Redis for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to save message to Redis for user: {}", username, e);
        }
    }

    @Async
    protected void asyncSaveToMySQL(ChatMessage message) {
        try {
            chatMessageMapper.insert(message);
            log.debug("Saved message to MySQL for user: {}", message.getUsername());
        } catch (Exception e) {
            log.error("Failed to save message to MySQL for user: {}", message.getUsername(), e);
        }
    }

    @Override
    public List<ChatMessage> getRecentMessages(String username, int limit) {
        // Try Redis first
        List<ChatMessage> messages = getFromRedis(username, limit);
        if (!messages.isEmpty()) {
            log.debug("Got {} messages from Redis for user: {}", messages.size(), username);
            return messages;
        }

        // Fallback to MySQL and populate Redis
        messages = getFromMySQL(username, limit);
        if (!messages.isEmpty()) {
            log.info("Loaded {} messages from MySQL to Redis for user: {}", messages.size(), username);
            populateRedisFromMySQL(username, messages);
        }

        return messages;
    }

    private List<ChatMessage> getFromRedis(String username, int limit) {
        try {
            String key = CHAT_HISTORY_PREFIX + username;
            if (!redisService.exists(key)) {
                return Collections.emptyList();
            }

            // Get last N messages
            long len = redisService.llen(key);
            long start = Math.max(0, len - limit);
            List<String> jsonList = redisService.lrange(key, start, -1);

            return jsonList.stream()
                    .map(json -> GSON.fromJson(json, ChatMessage.class))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get messages from Redis for user: {}", username, e);
            return Collections.emptyList();
        }
    }

    private List<ChatMessage> getFromMySQL(String username, int limit) {
        try {
            QueryWrapper query = QueryWrapper.create()
                    .eq("username", username)
                    .orderBy("created_at", false)
                    .limit(limit);

            List<ChatMessage> messages = chatMessageMapper.selectListByQuery(query);
            // Reverse to chronological order
            Collections.reverse(messages);
            return messages;
        } catch (Exception e) {
            log.error("Failed to get messages from MySQL for user: {}", username, e);
            return Collections.emptyList();
        }
    }

    private void populateRedisFromMySQL(String username, List<ChatMessage> messages) {
        try {
            String key = CHAT_HISTORY_PREFIX + username;
            // Clear existing
            redisService.del(key);
            // Add all messages
            for (ChatMessage msg : messages) {
                redisService.rpush(key, GSON.toJson(msg));
            }
            // Set expiration
            redisService.expire(key, redisExpireHours * 3600L);
        } catch (Exception e) {
            log.error("Failed to populate Redis from MySQL for user: {}", username, e);
        }
    }

    @Override
    public List<ChatMessage> getMessageHistory(String username, int page, int pageSize) {
        try {
            int offset = page * pageSize;
            QueryWrapper query = QueryWrapper.create()
                    .eq("username", username)
                    .orderBy("created_at", false)
                    .limit(offset, pageSize);

            List<ChatMessage> messages = chatMessageMapper.selectListByQuery(query);
            // Reverse to chronological order
            Collections.reverse(messages);
            return messages;
        } catch (Exception e) {
            log.error("Failed to get message history from MySQL for user: {}", username, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void clearHistory(String username) {
        try {
            // Clear Redis
            String key = CHAT_HISTORY_PREFIX + username;
            redisService.del(key);

            // Clear MySQL
            QueryWrapper query = QueryWrapper.create()
                    .eq("username", username);
            chatMessageMapper.deleteByQuery(query);

            log.info("Cleared chat history for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to clear history for user: {}", username, e);
        }
    }
}
