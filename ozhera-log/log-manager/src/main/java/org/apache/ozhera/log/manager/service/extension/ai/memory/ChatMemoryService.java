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
package org.apache.ozhera.log.manager.service.extension.ai.memory;

import java.util.List;

/**
 * Chat Memory Service Interface.
 * Provides conversation storage and retrieval functionality.
 */
public interface ChatMemoryService {

    String DEFAULT_CHAT_MEMORY_SERVICE_KEY = "defaultChatMemoryService";

    /**
     * Get conversation context by conversation ID
     *
     * @param conversationId conversation ID
     * @return conversation context, or null if not found
     */
    ConversationContext getConversation(Long conversationId);

    /**
     * Save conversation context
     *
     * @param conversationId conversation ID
     * @param context        conversation context
     */
    void saveConversation(Long conversationId, ConversationContext context);

    /**
     * Create a new conversation
     *
     * @param storeId       store ID
     * @param creator       creator username
     * @param firstMessage  first user message
     * @param firstResponse first bot response
     * @return new conversation ID
     */
    Long createConversation(Long storeId, String creator, String firstMessage, String firstResponse);

    /**
     * Delete a conversation
     *
     * @param conversationId conversation ID
     */
    void deleteConversation(Long conversationId);

    /**
     * Close a conversation (persist cache to database)
     *
     * @param conversationId conversation ID
     */
    void closeConversation(Long conversationId);

    /**
     * Get conversation list for a user
     *
     * @param storeId store ID
     * @param user    username
     * @return list of conversation summaries
     */
    List<ConversationSummary> getConversationList(Long storeId, String user);

    /**
     * Update conversation name
     *
     * @param conversationId conversation ID
     * @param name           new name
     */
    void updateConversationName(Long conversationId, String name);

    /**
     * Persist all cached conversations to database (for shutdown)
     */
    void persistAllConversations();

    /**
     * Clean up expired conversations
     */
    void cleanExpiredConversations();

    /**
     * Conversation context containing model history and original history
     */
    class ConversationContext {
        private List<QAPair> modelHistory;
        private List<QAPair> originalHistory;

        public ConversationContext() {
        }

        public ConversationContext(List<QAPair> modelHistory, List<QAPair> originalHistory) {
            this.modelHistory = modelHistory;
            this.originalHistory = originalHistory;
        }

        public List<QAPair> getModelHistory() {
            return modelHistory;
        }

        public void setModelHistory(List<QAPair> modelHistory) {
            this.modelHistory = modelHistory;
        }

        public List<QAPair> getOriginalHistory() {
            return originalHistory;
        }

        public void setOriginalHistory(List<QAPair> originalHistory) {
            this.originalHistory = originalHistory;
        }
    }

    /**
     * Question-Answer pair
     */
    class QAPair {
        private String time;
        private String user;
        private String bot;

        public QAPair() {
        }

        public QAPair(String time, String user, String bot) {
            this.time = time;
            this.user = user;
            this.bot = bot;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getBot() {
            return bot;
        }

        public void setBot(String bot) {
            this.bot = bot;
        }
    }

    /**
     * Conversation summary for list display
     */
    class ConversationSummary {
        private Long id;
        private String name;
        private String createTime;

        public ConversationSummary() {
        }

        public ConversationSummary(Long id, String name, String createTime) {
            this.id = id;
            this.name = name;
            this.createTime = createTime;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCreateTime() {
            return createTime;
        }

        public void setCreateTime(String createTime) {
            this.createTime = createTime;
        }
    }
}
