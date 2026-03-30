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
package org.apache.ozhera.mind.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.AgentCreateRequest;
import org.apache.ozhera.mind.api.dto.AgentCreateResponse;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import org.apache.ozhera.mind.gateway.router.AgentRouterService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

/**
 * Gateway service that routes requests to the appropriate worker.
 * Uses HTTP/WebClient instead of Dubbo for WebFlux compatibility.
 */
@Slf4j
@Service
public class GatewayAgentService {

    @Resource
    private WorkerClient workerClient;

    @Resource
    private WorkerDiscoveryService workerDiscoveryService;

    @Resource
    private AgentRouterService agentRouterService;

    /**
     * Create a new agent.
     * Gateway selects a worker and forwards the creation request.
     */
    public Mono<AgentCreateResponse> createAgent(AgentCreateRequest request) {
        log.info("Creating agent for user: {}", request.getUsername());

        try {
            // Select the best worker (least connections)
            String workerUrl = workerDiscoveryService.selectWorkerForNewAgent();

            return workerClient.createAgent(workerUrl, request)
                    .doOnSuccess(response -> {
                        if (response.isSuccess()) {
                            // Store agent -> worker mapping in Redis
                            response.setWorkerId(workerUrl);
                            agentRouterService.bindAgentToWorker(response.getAgentId(), workerUrl);
                            log.info("Agent {} created on worker {}", response.getAgentId(), workerUrl);
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Failed to create agent", e);
                        return Mono.just(AgentCreateResponse.builder()
                                .success(false)
                                .errorMessage("Failed to create agent: " + e.getMessage())
                                .build());
                    });
        } catch (Exception e) {
            log.error("Failed to create agent", e);
            return Mono.just(AgentCreateResponse.builder()
                    .success(false)
                    .errorMessage("Failed to create agent: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Route chat request to the correct worker based on agentId (non-streaming).
     */
    public Mono<ChatResponse> chat(ChatRequest request) {
        String agentId = request.getAgentId();
        log.debug("Chat request for agent: {}", agentId);

        // Find which worker has this agent
        String workerUrl = agentRouterService.getWorkerForAgent(agentId);
        if (workerUrl == null) {
            return Mono.just(ChatResponse.builder()
                    .errorMessage("Agent not found: " + agentId)
                    .finished(true)
                    .build());
        }

        return workerClient.chat(workerUrl, request)
                .doOnSuccess(r -> agentRouterService.refreshAgentExpiration(agentId))
                .onErrorResume(e -> {
                    log.error("Chat failed for agent: {}", agentId, e);
                    return Mono.just(ChatResponse.builder()
                            .errorMessage("Chat failed: " + e.getMessage())
                            .finished(true)
                            .build());
                });
    }

    /**
     * Route chat request to the correct worker (streaming via SSE).
     */
    public Flux<String> chatStream(ChatRequest request) {
        String agentId = request.getAgentId();
        log.debug("Stream chat request for agent: {}", agentId);

        // Find which worker has this agent
        String workerUrl = agentRouterService.getWorkerForAgent(agentId);
        if (workerUrl == null) {
            return Flux.just("data: {\"error\": \"Agent not found: " + agentId + "\"}\n\n");
        }

        agentRouterService.refreshAgentExpiration(agentId);
        return workerClient.chatStream(workerUrl, request)
                .onErrorResume(e -> {
                    log.error("Stream chat failed for agent: {}", agentId, e);
                    return Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n");
                });
    }

    /**
     * Destroy an agent.
     */
    public Mono<Boolean> destroyAgent(String agentId) {
        String workerUrl = agentRouterService.getWorkerForAgent(agentId);
        if (workerUrl == null) {
            log.warn("Agent not found for destruction: {}", agentId);
            return Mono.just(false);
        }

        return workerClient.destroyAgent(workerUrl, agentId)
                .doOnSuccess(result -> {
                    if (result) {
                        agentRouterService.unbindAgent(agentId);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to destroy agent: {}", agentId, e);
                    return Mono.just(false);
                });
    }
}
