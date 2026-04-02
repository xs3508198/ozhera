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
package org.apache.ozhera.mind.service.service;

import org.apache.ozhera.mind.service.entity.ChatMessage;

import java.util.List;

/**
 * Service for managing chat message history.
 * Handles Redis caching and MySQL persistence.
 */
public interface ChatMessageService {

    /**
     * Save a message (to Redis immediately, async to MySQL)
     */
    void saveMessage(String username, String role, String content);

    /**
     * Get recent messages for a user (from Redis cache, fallback to MySQL)
     * @param username the user
     * @param limit max number of messages to return
     * @return messages in chronological order (oldest first)
     */
    List<ChatMessage> getRecentMessages(String username, int limit);

    /**
     * Get all messages for a user (from MySQL, for frontend display)
     * @param username the user
     * @param page page number (0-based)
     * @param pageSize page size
     * @return messages in chronological order
     */
    List<ChatMessage> getMessageHistory(String username, int page, int pageSize);

    /**
     * Clear message history for a user
     */
    void clearHistory(String username);
}
