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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.service.hook.StreamingHook;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Sinks;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import org.apache.ozhera.mind.service.context.UserContext;
import org.apache.ozhera.mind.service.entity.ChatMessage;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.llm.tool.LogToolService;
import org.apache.ozhera.mind.service.redis.AgentCacheManager;
import org.apache.ozhera.mind.service.service.AgentService;
import org.apache.ozhera.mind.service.service.ChatMessageService;
import org.apache.ozhera.mind.service.service.MemoryStateService;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Agent Service Implementation.
 * Manages agent lifecycle with AutoContextMemory for intelligent context management.
 * Memory state is persisted to Redis for recovery across agent recreations.
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    @NacosValue(value = "${agent.cache.expire-minutes:15}", autoRefreshed = true)
    private int cacheExpireMinutes;

    @NacosValue(value = "${agent.cache.max-size:500}", autoRefreshed = true)
    private int cacheMaxSize;

    // AutoContextMemory configuration
    @NacosValue(value = "${agent.memory.msg-threshold:20}", autoRefreshed = true)
    private int msgThreshold;

    @NacosValue(value = "${agent.memory.max-token:128000}", autoRefreshed = true)
    private int maxToken;

    @NacosValue(value = "${agent.memory.token-ratio:0.8}", autoRefreshed = true)
    private double tokenRatio;

    @NacosValue(value = "${agent.memory.last-keep:5}", autoRefreshed = true)
    private int lastKeep;

    @NacosValue(value = "${agent.memory.large-payload-threshold:5000}", autoRefreshed = true)
    private long largePayloadThreshold;

    @NacosValue(value = "${agent.history.load-size:50}", autoRefreshed = true)
    private int historyLoadSize;

    /**
     * Agent cache by username.
     */
    private Cache<String, ReActAgent> agentCache;

    /**
     * Memory cache by username (for saving state on agent eviction).
     */
    private Cache<String, AutoContextMemory> memoryCache;

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private ModelProviderService modelProviderService;

    @Resource
    private LogToolService logToolService;

    @Resource
    private AgentCacheManager agentCacheManager;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private MemoryStateService memoryStateService;
    @Autowired
    private Gson gson;

    @PostConstruct
    public void init() {
        log.info("Initializing agent cache with maxSize={}, expireMinutes={}", cacheMaxSize, cacheExpireMinutes);

        // Memory cache with same expiration, saves state on eviction
        memoryCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterAccess(cacheExpireMinutes, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .removalListener((String username, AutoContextMemory memory, RemovalCause cause) -> {
                    if (cause.wasEvicted() && memory != null) {
                        log.info("Memory evicted, saving state for user: {}", username);
                        memoryStateService.saveState(username, memory);
                    }
                })
                .build();

        agentCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterAccess(cacheExpireMinutes, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .removalListener((String username, ReActAgent agent, RemovalCause cause) -> {
                    if (cause.wasEvicted()) {
                        log.info("Agent evicted from cache: user={}, cause={}", username, cause);
                        agentCacheManager.onAgentEvicted(username);
                    }
                })
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String username = request.getUsername();
        String userMessage = request.getMessage();

        // Set user context for Tool methods
        UserContext.set(new UserContext.UserInfo(username, 0));
        try {
            ReActAgent agent = getOrCreateAgent(username);

            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(userMessage).build())
                    .build();

            Msg response = agent.call(List.of(userMsg)).block();
            String responseContent = response != null ? response.getTextContent() : "";

            // Save to MySQL for frontend display (original messages)
            chatMessageService.saveMessage(username, "USER", userMessage);
            if (!responseContent.isEmpty()) {
                chatMessageService.saveMessage(username, "ASSISTANT", responseContent);
            }

            // Save memory state to Redis
            saveMemoryState(username);

            return ChatResponse.builder()
                    .content(responseContent)
                    .finished(true)
                    .build();
        } catch (Exception e) {
            log.error("Chat failed for user: {}", username, e);
            return ChatResponse.builder()
                    .errorMessage(e.getMessage())
                    .finished(true)
                    .build();
        } finally {
            UserContext.clear();
        }
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        String username = request.getUsername();
        String userMessage = request.getMessage();

        // Create sink for streaming
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder fullResponse = new StringBuilder();
        StreamingHook streamingHook = new StreamingHook(sink, fullResponse);

        // Capture user context for async execution
        UserContext.UserInfo userInfo = new UserContext.UserInfo(username, 0);

        try {
            // Create agent with streaming hook
            ReActAgent agent = createStreamingAgent(username, streamingHook);

            Msg userMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(userMessage).build())
                    .build();

            // Execute agent call asynchronously
            agent.call(List.of(userMsg))
                    .doOnSubscribe(subscription -> {
                        // Set user context when async execution starts
                        UserContext.set(userInfo);
                    })
                    .doOnSuccess(msg -> {
                        streamingHook.complete();
                        // Save to MySQL for frontend display
                        chatMessageService.saveMessage(username, "USER", userMessage);
                        String content = fullResponse.toString();
                        if (!content.isEmpty()) {
                            chatMessageService.saveMessage(username, "ASSISTANT", content);
                        }
                        // Save memory state to Redis
                        saveMemoryState(username);
                    })
                    .doOnError(e -> {
                        log.error("Stream chat failed for user: {}", username, e);
                        streamingHook.error(e);
                    })
                    .doFinally(signalType -> {
                        // Clear user context when done
                        UserContext.clear();
                    })
                    .subscribe();

            return sink.asFlux();
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
            log.debug("Using cached agent for user: {}", username);
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

        // Create AutoContextMemory with configuration
        AutoContextConfig memoryConfig = AutoContextConfig.builder()
                .msgThreshold(msgThreshold)
                .maxToken(maxToken)
                .tokenRatio(tokenRatio)
                .lastKeep(lastKeep)
                .largePayloadThreshold(largePayloadThreshold)
                .build();

        AutoContextMemory memory = new AutoContextMemory(memoryConfig, model);

        // Try to restore memory state from Redis
        boolean restored = memoryStateService.loadState(username, memory);

        if (!restored) {
            // No saved state, load history from MySQL
            log.info("No memory state in Redis, loading history from MySQL for user: {}", username);
            List<Msg> historyMessages = loadHistoryMessages(username);
            for (Msg msg : historyMessages) {
                memory.addMessage(msg);
            }
            if (!historyMessages.isEmpty()) {
                log.info("Loaded {} history messages from MySQL for user: {}", historyMessages.size(), username);
            }
        } else {
            log.info("Restored memory state from Redis for user: {}", username);
        }

        // Create toolkit and register tools
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(logToolService);

        // Create ReAct agent with AutoContextMemory
        agent = ReActAgent.builder()
                .name("MindAgent")
                .model(model)
                .toolkit(toolkit)
                .memory(memory)
                .build();

        // Cache both agent and memory
        agentCache.put(username, agent);
        memoryCache.put(username, memory);

        return agent;
    }

    /**
     * Create a streaming-enabled agent with StreamingHook.
     * This creates a temporary agent that shares memory with the cached one.
     */
    private ReActAgent createStreamingAgent(String username, StreamingHook streamingHook) {
        // Get user config from database
        UserConfig userConfig = userConfigService.getByUsername(username);
        if (userConfig == null) {
            throw new RuntimeException("User config not found. Please configure your API key and model settings first.");
        }

        // Create model using ModelProviderService
        Model model = modelProviderService.createModel(userConfig);

        // Get or create memory (shared with cached agent)
        AutoContextMemory memory = memoryCache.getIfPresent(username);
        if (memory == null) {
            AutoContextConfig memoryConfig = AutoContextConfig.builder()
                    .msgThreshold(msgThreshold)
                    .maxToken(maxToken)
                    .tokenRatio(tokenRatio)
                    .lastKeep(lastKeep)
                    .largePayloadThreshold(largePayloadThreshold)
                    .build();

            memory = new AutoContextMemory(memoryConfig, model);

            // Try to restore memory state from Redis
            boolean restored = memoryStateService.loadState(username, memory);

            if (!restored) {
                // No saved state, load history from MySQL
                log.info("No memory state in Redis, loading history from MySQL for user: {}", username);
                List<Msg> historyMessages = loadHistoryMessages(username);
                for (Msg msg : historyMessages) {
                    memory.addMessage(msg);
                }
            }

            // Cache memory for future use
            memoryCache.put(username, memory);
        }

        // Create toolkit and register tools
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(logToolService);
        log.info("工具注册列表为：{}" , gson.toJson(toolkit.getToolSchemas()));
        // Create temporary agent with streaming hook
        return ReActAgent.builder()
                .name("MindAgent")
                .sysPrompt("你是一个hera日志的助手，可以根据用户的要求来执行对应的任务")
                .model(model)
                .toolkit(toolkit)
                .memory(memory)
                .hook(streamingHook)
                .build();
    }

    /**
     * Save memory state to Redis.
     */
    private void saveMemoryState(String username) {
        AutoContextMemory memory = memoryCache.getIfPresent(username);
        if (memory != null) {
            memoryStateService.saveState(username, memory);
        }
    }

    /**
     * Load chat history from MySQL and convert to Msg list.
     * Only used when no Redis state exists.
     */
    private List<Msg> loadHistoryMessages(String username) {
        List<ChatMessage> history = chatMessageService.getRecentMessages(username, historyLoadSize);
        if (history.isEmpty()) {
            return Collections.emptyList();
        }

        return history.stream()
                .map(msg -> Msg.builder()
                        .name("USER".equals(msg.getRole()) ? "user" : "assistant")
                        .role("USER".equals(msg.getRole()) ? MsgRole.USER : MsgRole.ASSISTANT)
                        .content(TextBlock.builder().text(msg.getContent()).build())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Invalidate agent cache for a user.
     * Should be called when user updates their config.
     */
    public void invalidateCache(String username) {
        log.info("Invalidating agent cache for user: {}", username);
        // Save memory state before invalidating
        saveMemoryState(username);
        agentCache.invalidate(username);
        memoryCache.invalidate(username);
    }
}
