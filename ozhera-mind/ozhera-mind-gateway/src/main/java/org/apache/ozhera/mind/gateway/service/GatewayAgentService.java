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
    private AgentRouterService agentRouterService;

    @Resource
    private WorkerDiscoveryService workerDiscoveryService;

    /**
     * Route chat request to the correct worker based on username (non-streaming).
     * Worker will automatically create agent if not exists.
     */
    public Mono<ChatResponse> chat(ChatRequest request) {
        String username = request.getUsername();
        log.debug("Chat request for user: {}", username);

        try {
            String workerUrl = getOrAssignWorker(username);

            return workerClient.chat(workerUrl, request)
                    .onErrorResume(e -> {
                        log.error("Chat failed for user: {}", username, e);
                        return Mono.just(ChatResponse.builder()
                                .errorMessage("Chat failed: " + e.getMessage())
                                .finished(true)
                                .build());
                    });
        } catch (Exception e) {
            log.error("Failed to route chat request for user: {}", username, e);
            return Mono.just(ChatResponse.builder()
                    .errorMessage("Failed to route request: " + e.getMessage())
                    .finished(true)
                    .build());
        }
    }

    /**
     * Route chat request to the correct worker (streaming via SSE).
     * Worker will automatically create agent if not exists.
     */
    public Flux<String> chatStream(ChatRequest request) {
        String username = request.getUsername();
        log.debug("Stream chat request for user: {}", username);

        try {
            String workerUrl = getOrAssignWorker(username);

            return workerClient.chatStream(workerUrl, request)
                    .onErrorResume(e -> {
                        log.error("Stream chat failed for user: {}", username, e);
                        return Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n");
                    });
        } catch (Exception e) {
            log.error("Failed to route stream chat request for user: {}", username, e);
            return Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n");
        }
    }

    /**
     * Get existing worker for user, or assign a new one.
     * If the assigned worker is no longer alive, reassign to a new worker.
     */
    private String getOrAssignWorker(String username) {
        // Check if user already has an assigned worker
        String workerUrl = agentRouterService.getWorkerForUser(username);
        if (workerUrl != null) {
            // Verify worker is still alive
            if (workerDiscoveryService.isWorkerAlive(workerUrl)) {
                return workerUrl;
            }
            // Worker is no longer available, unbind user
            log.warn("Worker {} is no longer available, reassigning user {}", workerUrl, username);
            agentRouterService.unbindUser(username);
        }

        // Select a new worker and bind user to it
        workerUrl = workerDiscoveryService.selectWorkerForNewAgent();
        agentRouterService.bindUserToWorker(username, workerUrl);
        return workerUrl;
    }
}
