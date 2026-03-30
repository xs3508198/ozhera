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

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.AgentCreateRequest;
import org.apache.ozhera.mind.api.dto.AgentCreateResponse;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import org.apache.ozhera.mind.service.llm.LlmModelService;
import org.apache.ozhera.mind.service.llm.tool.LogToolService;
import org.apache.ozhera.mind.service.service.AgentService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent service implementation.
 * Manages agent lifecycle and execution.
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    /**
     * In-memory agent storage
     */
    private final ConcurrentHashMap<String, AgentContext> agents = new ConcurrentHashMap<>();

    @Resource
    private LlmModelService llmModelService;

    @Resource
    private LogToolService logToolService;

    @Override
    public AgentCreateResponse createAgent(AgentCreateRequest request) {
        String agentId = UUID.randomUUID().toString();
        String username = request.getUsername();

        try {
            // Get user's LLM model
            Model model = llmModelService.getModel(username);

            // Create toolkit and register tools
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(logToolService);

            // Create ReAct agent with tools
            ReActAgent agent = ReActAgent.builder()
                    .name(request.getAgentName() != null ? request.getAgentName() : "MindAgent")
                    .model(model)
                    .sysPrompt(request.getSystemPrompt())
                    .toolkit(toolkit)
                    .build();

            // Store agent context
            AgentContext context = new AgentContext(agentId, username, agent);
            agents.put(agentId, context);

            log.info("Created agent {} for user {}", agentId, username);

            return AgentCreateResponse.builder()
                    .agentId(agentId)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create agent for user {}", username, e);
            return AgentCreateResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        AgentContext context = agents.get(request.getAgentId());
        if (context == null) {
            return ChatResponse.builder()
                    .errorMessage("Agent not found: " + request.getAgentId())
                    .finished(true)
                    .build();
        }

        try {
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(request.getMessage()).build())
                    .build();

            Msg response = context.getAgent().call(List.of(userMsg)).block();

            return ChatResponse.builder()
                    .content(response != null ? response.getTextContent() : "")
                    .finished(true)
                    .build();
        } catch (Exception e) {
            log.error("Chat failed for agent {}", request.getAgentId(), e);
            return ChatResponse.builder()
                    .errorMessage(e.getMessage())
                    .finished(true)
                    .build();
        }
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        AgentContext context = agents.get(request.getAgentId());
        if (context == null) {
            return Flux.just("{\"error\": \"Agent not found\"}");
        }

        try {
            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(request.getMessage()).build())
                    .build();

            // Use call() and convert to Flux for streaming response
            return context.getAgent().call(List.of(userMsg))
                    .map(msg -> msg != null && msg.getTextContent() != null ? msg.getTextContent() : "")
                    .flux();
        } catch (Exception e) {
            log.error("Stream chat failed for agent {}", request.getAgentId(), e);
            return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @Override
    public boolean destroyAgent(String agentId) {
        AgentContext removed = agents.remove(agentId);
        if (removed != null) {
            log.info("Destroyed agent {}", agentId);
            return true;
        }
        return false;
    }

    @Override
    public boolean agentExists(String agentId) {
        return agents.containsKey(agentId);
    }

    /**
     * Agent context holder
     */
    private static class AgentContext {
        private final String agentId;
        private final String username;
        private final ReActAgent agent;

        public AgentContext(String agentId, String username, ReActAgent agent) {
            this.agentId = agentId;
            this.username = username;
            this.agent = agent;
        }

        public String getAgentId() {
            return agentId;
        }

        public String getUsername() {
            return username;
        }

        public ReActAgent getAgent() {
            return agent;
        }
    }
}
