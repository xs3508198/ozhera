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

import org.apache.ozhera.mind.api.dto.AgentCreateRequest;
import org.apache.ozhera.mind.api.dto.AgentCreateResponse;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * Agent management service.
 */
public interface AgentService {

    /**
     * Create a new agent
     */
    AgentCreateResponse createAgent(AgentCreateRequest request);

    /**
     * Chat with an agent (non-streaming)
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Chat with an agent (streaming)
     */
    Flux<String> chatStream(ChatRequest request);

    /**
     * Destroy an agent
     */
    boolean destroyAgent(String agentId);

    /**
     * Check if agent exists
     */
    boolean agentExists(String agentId);
}
