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
package org.apache.ozhera.mind.gateway.controller;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.gateway.controller.dto.ApiResult;
import org.apache.ozhera.mind.gateway.dao.mapper.ChatMessageMapper;
import org.apache.ozhera.mind.gateway.entity.ChatMessage;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * Controller for querying chat history.
 * Provides API for frontend to display conversation history.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/history")
public class ChatHistoryController {

    @Resource
    private ChatMessageMapper chatMessageMapper;

    /**
     * Get chat history for a user with pagination.
     *
     * @param username the username
     * @param page     page number (0-based, default 0)
     * @param pageSize page size (default 20, max 100)
     * @return list of chat messages in chronological order
     */
    @GetMapping("/{username}")
    public ApiResult<List<ChatMessage>> getHistory(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        try {
            // Limit page size to prevent abuse
            pageSize = Math.min(pageSize, 100);

            int offset = page * pageSize;
            QueryWrapper query = QueryWrapper.create()
                    .eq("username", username)
                    .orderBy("created_at", false)
                    .limit(offset, pageSize);

            List<ChatMessage> messages = chatMessageMapper.selectListByQuery(query);
            // Reverse to chronological order
            Collections.reverse(messages);

            return ApiResult.success(messages);
        } catch (Exception e) {
            log.error("Failed to get chat history for user: {}", username, e);
            return ApiResult.fail("Failed to get chat history: " + e.getMessage());
        }
    }

    /**
     * Get total message count for a user.
     *
     * @param username the username
     * @return total message count
     */
    @GetMapping("/{username}/count")
    public ApiResult<Long> getHistoryCount(@PathVariable String username) {
        try {
            QueryWrapper query = QueryWrapper.create()
                    .eq("username", username);
            long count = chatMessageMapper.selectCountByQuery(query);
            return ApiResult.success(count);
        } catch (Exception e) {
            log.error("Failed to get chat history count for user: {}", username, e);
            return ApiResult.fail("Failed to get count: " + e.getMessage());
        }
    }

    /**
     * Clear chat history for a user.
     *
     * @param username the username
     * @return success or failure
     */
    @DeleteMapping("/{username}")
    public ApiResult<Void> clearHistory(@PathVariable String username) {
        try {
            QueryWrapper query = QueryWrapper.create()
                    .eq("username", username);
            chatMessageMapper.deleteByQuery(query);
            log.info("Cleared chat history for user: {}", username);
            return ApiResult.success(null);
        } catch (Exception e) {
            log.error("Failed to clear chat history for user: {}", username, e);
            return ApiResult.fail("Failed to clear history: " + e.getMessage());
        }
    }
}
