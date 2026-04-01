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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * HTTP client for communicating with Worker instances.
 * Uses WebClient for non-blocking HTTP calls and SSE streaming.
 */
@Slf4j
@Service
public class WorkerClient {

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Send chat message to worker (non-streaming)
     */
    public Mono<ChatResponse> chat(String workerUrl, ChatRequest request) {
        log.debug("Sending chat to worker: {}, user: {}", workerUrl, request.getUsername());
        return webClient.post()
                .uri(workerUrl + "/worker/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(Duration.ofSeconds(120))
                .doOnError(e -> log.error("Chat failed on worker: {}", workerUrl, e));
    }

    /**
     * Send chat message to worker (streaming via SSE)
     */
    public Flux<String> chatStream(String workerUrl, ChatRequest request) {
        log.debug("Sending stream chat to worker: {}, user: {}", workerUrl, request.getUsername());
        return webClient.post()
                .uri(workerUrl + "/worker/agent/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMinutes(5))
                .doOnError(e -> log.error("Stream chat failed on worker: {}", workerUrl, e));
    }
}
