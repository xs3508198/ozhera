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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.llm.tool.LogToolService;
import org.apache.ozhera.mind.service.service.AgentService;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent Service Implementation.
 * Manages agent lifecycle internally, one agent per user.
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    /**
     * Agent cache by username.
     * Cache expires after 30 minutes of inactivity.
     */
    private final Cache<String, ReActAgent> agentCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private ModelProviderService modelProviderService;

    @Resource
    private LogToolService logToolService;

    @Override
    public ChatResponse chat(ChatRequest request) {
        String username = request.getUsername();

        try {
            ReActAgent agent = getOrCreateAgent(username);

            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(request.getMessage()).build())
                    .build();

            Msg response = agent.call(List.of(userMsg)).block();

            return ChatResponse.builder()
                    .content(response != null ? response.getTextContent() : "")
                    .finished(true)
                    .build();
        } catch (Exception e) {
            log.error("Chat failed for user: {}", username, e);
            return ChatResponse.builder()
                    .errorMessage(e.getMessage())
                    .finished(true)
                    .build();
        }
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        String username = request.getUsername();

        try {
            ReActAgent agent = getOrCreateAgent(username);

            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(request.getMessage()).build())
                    .build();

            return agent.call(List.of(userMsg))
                    .map(msg -> msg != null && msg.getTextContent() != null ? msg.getTextContent() : "")
                    .flux();
        } catch (Exception e) {
            log.error("Stream chat failed for user: {}", username, e);
            return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Get existing agent or create a new one for the user.
     */
    private ReActAgent getOrCreateAgent(String username) {
        ReActAgent agent = agentCache.getIfPresent(username);
        if (agent != null) {
            log.info("Using cached agent for user: {}", username);
            return agent;
        }

        log.info("Creating new agent for user: {}", username);

        // Get user config from database
        UserConfig userConfig = userConfigService.getByUsername(username);
        if (userConfig == null) {
            throw new RuntimeException("User config not found. Please configure your API key and model settings first.");
        }

        // Create model using ModelProviderService
        Model model = modelProviderService.createModel(userConfig);

        // Create toolkit and register tools
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(logToolService);

        // Create ReAct agent
        agent = ReActAgent.builder()
                .name("MindAgent")
                .model(model)
                .toolkit(toolkit)
                .build();

        agentCache.put(username, agent);
        return agent;
    }

    /**
     * Invalidate agent cache for a user.
     * Should be called when user updates their config.
     */
    public void invalidateCache(String username) {
        log.info("Invalidating agent cache for user: {}", username);
        agentCache.invalidate(username);
    }
}
