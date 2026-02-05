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
package org.apache.ozhera.log.manager.service.extension.ai.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * LLM Service Interface
 * Provides abstraction for LLM calls, allowing flexible implementation switching via configuration.
 */
public interface LlmService {

    String DEFAULT_LLM_SERVICE_KEY = "defaultLlmService";

    /**
     * Single-turn chat
     *
     * @param prompt user prompt
     * @return LLM response
     */
    String chat(String prompt);

    /**
     * Multi-turn chat with conversation history
     *
     * @param messages      conversation history
     * @param systemPrompt  system prompt
     * @return LLM response
     */
    String chat(List<ChatMessage> messages, String systemPrompt);

    /**
     * Streaming chat with token-by-token callback
     *
     * @param messages      conversation history
     * @param systemPrompt  system prompt
     * @param onToken       callback for each token chunk
     * @param onComplete    callback when streaming completes with full response
     * @param onError       callback when error occurs
     */
    void streamChat(List<ChatMessage> messages, String systemPrompt,
                    Consumer<String> onToken, Consumer<String> onComplete, Consumer<Throwable> onError);

    /**
     * Get the model name being used
     *
     * @return model name/identifier
     */
    String getModelName();

    /**
     * Chat message for multi-turn conversation
     */
    class ChatMessage {
        private Role role;
        private String content;

        public ChatMessage() {
        }

        public ChatMessage(Role role, String content) {
            this.role = role;
            this.content = content;
        }

        public static ChatMessage system(String content) {
            return new ChatMessage(Role.SYSTEM, content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage(Role.USER, content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage(Role.ASSISTANT, content);
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public enum Role {
            SYSTEM,
            USER,
            ASSISTANT
        }
    }
}
