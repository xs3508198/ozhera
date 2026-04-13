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

import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import org.apache.ozhera.mind.service.entity.ChatMessage;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.concurrency.ConcurrencyLimiter;
import org.apache.ozhera.mind.service.concurrency.UserLockManager;
import org.apache.ozhera.mind.service.service.ChatMessageService;
import org.apache.ozhera.mind.service.service.MemoryStateService;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Swarm Agent Service.
 * Main service for Multi-Agent chat, replacing AgentServiceImpl.
 */
@Slf4j
@Service
public class SwarmAgentService {

    // ==================== Configuration ====================

    @NacosValue(value = "${agent.cache.expire-minutes:15}", autoRefreshed = true)
    private int cacheExpireMinutes;

    @NacosValue(value = "${agent.cache.max-size:500}", autoRefreshed = true)
    private int cacheMaxSize;

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

    @NacosValue(value = "${agent.concurrency.acquire-timeout-ms:5000}", autoRefreshed = true)
    private long acquireTimeoutMs;

    @NacosValue(value = "${agent.concurrency.user-lock-timeout-ms:30000}", autoRefreshed = true)
    private long userLockTimeoutMs;

    // ==================== Dependencies ====================

    @Resource
    private SwarmExecutor swarmExecutor;

    @Resource
    private SwarmSessionService sessionService;

    @Resource
    private ModelProviderService modelProviderService;

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private MemoryStateService memoryStateService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ConcurrencyLimiter concurrencyLimiter;

    @Resource
    private UserLockManager userLockManager;

    @Resource
    private ExecutorService agentVirtualExecutor;

    // ==================== Caches ====================

    /**
     * Memory cache: one per user, shared by all agents.
     */
    private Cache<String, AutoContextMemory> memoryCache;

    /**
     * Session cache: tracks current agent per user.
     */
    private Cache<String, SwarmSession> sessionCache;

    @PostConstruct
    public void init() {
        log.info("Initializing SwarmAgentService with maxSize={}, expireMinutes={}",
                cacheMaxSize, cacheExpireMinutes);

        // Memory cache with eviction listener
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

        // Session cache with eviction listener
        sessionCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterAccess(cacheExpireMinutes, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .removalListener((String username, SwarmSession session, RemovalCause cause) -> {
                    if (cause.wasEvicted() && session != null) {
                        log.info("Session evicted, saving state for user: {}", username);
                        sessionService.saveSession(username, session);
                    }
                })
                .build();
    }

    /**
     * Streaming chat with concurrency control.
     */
    public Flux<String> chatStream(ChatRequest request) {
        String username = request.getUsername();
        String userMessage = request.getMessage();

        log.info("Chat request from user: {}, message length: {}", username, userMessage.length());

        return Mono.fromCallable(() -> {
                    // Step 1: Acquire global concurrency permit
                    if (!concurrencyLimiter.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("System busy, please try again later. " +
                                "Queue length: " + concurrencyLimiter.getQueueLength());
                    }
                    return true;
                })
                .flatMapMany(acquired -> {
                    // Step 2: Try to acquire user lock
                    return Mono.fromCallable(() -> {
                                if (!userLockManager.tryLock(username, userLockTimeoutMs, TimeUnit.MILLISECONDS)) {
                                    concurrencyLimiter.release();
                                    throw new RuntimeException("Previous request still processing, please wait.");
                                }
                                return true;
                            })
                            .flatMapMany(locked -> {
                                // Step 3: Execute chat
                                AutoContextMemory memory = getOrCreateMemory(username);
                                SwarmSession session = getOrCreateSession(username);

                                // Save user message to MySQL
                                chatMessageService.saveMessage(username, "USER", userMessage);

                                StringBuilder fullResponse = new StringBuilder();

                                return swarmExecutor.execute(username, userMessage, session, memory)
                                        .doOnNext(chunk -> fullResponse.append(chunk))
                                        .doOnComplete(() -> {
                                            // Save assistant response
                                            String response = fullResponse.toString();
                                            if (!response.isEmpty()) {
                                                String cleanResponse = response.replaceAll("__HANDOFF__:\\{[^}]+\\}", "").trim();
                                                if (!cleanResponse.isEmpty()) {
                                                    chatMessageService.saveMessage(username, "ASSISTANT", cleanResponse);
                                                }
                                            }

                                            // Save memory state
                                            memoryStateService.saveState(username, memory);

                                            // Save session state
                                            sessionService.saveSession(username, session);

                                            log.info("Chat completed for user: {}, currentAgent: {}",
                                                    username, session.getCurrentAgentName());
                                        })
                                        .doOnError(e -> log.error("Chat failed for user: {}", username, e))
                                        .doFinally(signal -> {
                                            // Always release user lock and global permit
                                            userLockManager.unlock(username);
                                            concurrencyLimiter.release();
                                            log.debug("Released locks for user: {}", username);
                                        });
                            });
                })
                .subscribeOn(Schedulers.fromExecutor(agentVirtualExecutor))
                .onErrorResume(e -> {
                    log.error("Chat error for user: {}", username, e);
                    return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
                });
    }

    /**
     * Non-streaming chat with concurrency control.
     */
    public ChatResponse chat(ChatRequest request) {
        String username = request.getUsername();
        String userMessage = request.getMessage();

        boolean globalAcquired = false;
        boolean userLocked = false;

        try {
            // Step 1: Acquire global concurrency permit
            if (!concurrencyLimiter.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                return ChatResponse.builder()
                        .errorMessage("System busy, please try again later. Queue length: " +
                                concurrencyLimiter.getQueueLength())
                        .finished(true)
                        .build();
            }
            globalAcquired = true;

            // Step 2: Try to acquire user lock
            if (!userLockManager.tryLock(username, userLockTimeoutMs, TimeUnit.MILLISECONDS)) {
                return ChatResponse.builder()
                        .errorMessage("Previous request still processing, please wait.")
                        .finished(true)
                        .build();
            }
            userLocked = true;

            // Step 3: Execute chat
            AutoContextMemory memory = getOrCreateMemory(username);
            SwarmSession session = getOrCreateSession(username);

            // Save user message
            chatMessageService.saveMessage(username, "USER", userMessage);

            // Execute and collect
            String response = swarmExecutor.execute(username, userMessage, session, memory)
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block();

            // Save response
            if (response != null && !response.isEmpty()) {
                String cleanResponse = response.replaceAll("__HANDOFF__:\\{[^}]+\\}", "").trim();
                if (!cleanResponse.isEmpty()) {
                    chatMessageService.saveMessage(username, "ASSISTANT", cleanResponse);
                }
            }

            // Save states
            memoryStateService.saveState(username, memory);
            sessionService.saveSession(username, session);

            return ChatResponse.builder()
                    .content(response)
                    .finished(true)
                    .build();

        } catch (Exception e) {
            log.error("Chat failed for user: {}", username, e);
            return ChatResponse.builder()
                    .errorMessage(e.getMessage())
                    .finished(true)
                    .build();
        } finally {
            // Release in reverse order
            if (userLocked) {
                userLockManager.unlock(username);
            }
            if (globalAcquired) {
                concurrencyLimiter.release();
            }
        }
    }

    /**
     * Get or create memory for user.
     */
    private AutoContextMemory getOrCreateMemory(String username) {
        return memoryCache.get(username, k -> {
            var userConfig = userConfigService.getByUsername(username);
            if (userConfig == null) {
                throw new RuntimeException("User config not found. Please configure your API key first.");
            }

            Model model = modelProviderService.createModel(userConfig);

            AutoContextConfig config = AutoContextConfig.builder()
                    .msgThreshold(msgThreshold)
                    .maxToken(maxToken)
                    .tokenRatio(tokenRatio)
                    .lastKeep(lastKeep)
                    .largePayloadThreshold(largePayloadThreshold)
                    .build();

            AutoContextMemory memory = new AutoContextMemory(config, model);

            // Try to restore from Redis
            boolean restored = memoryStateService.loadState(username, memory);

            if (!restored) {
                // Load history from MySQL
                log.info("No memory state in Redis, loading from MySQL for user: {}", username);
                List<Msg> historyMessages = loadHistoryMessages(username);
                for (Msg msg : historyMessages) {
                    memory.addMessage(msg);
                }
                if (!historyMessages.isEmpty()) {
                    log.info("Loaded {} history messages for user: {}", historyMessages.size(), username);
                }
            } else {
                log.info("Restored memory from Redis for user: {}", username);
            }

            return memory;
        });
    }

    /**
     * Get or create session for user.
     */
    private SwarmSession getOrCreateSession(String username) {
        return sessionCache.get(username, k -> {
            // Try to load from Redis
            SwarmSession session = sessionService.loadSession(username);
            if (session != null) {
                log.info("Restored session from Redis for user: {}, currentAgent: {}",
                        username, session.getCurrentAgentName());
                return session;
            }

            // Create new session
            session = SwarmSession.create(username);
            log.info("Created new session for user: {}", username);
            return session;
        });
    }

    /**
     * Load history messages from MySQL.
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
     * Invalidate cache for user.
     */
    public void invalidateCache(String username) {
        log.info("Invalidating cache for user: {}", username);

        // Save before invalidating
        AutoContextMemory memory = memoryCache.getIfPresent(username);
        if (memory != null) {
            memoryStateService.saveState(username, memory);
        }

        SwarmSession session = sessionCache.getIfPresent(username);
        if (session != null) {
            sessionService.saveSession(username, session);
        }

        memoryCache.invalidate(username);
        sessionCache.invalidate(username);
    }

    /**
     * Reset session to CommonAgent.
     */
    public void resetSession(String username) {
        log.info("Resetting session for user: {}", username);
        SwarmSession session = getOrCreateSession(username);
        session.switchAgent("CommonAgent");
        sessionService.saveSession(username, session);
        sessionCache.put(username, session);
    }
}
