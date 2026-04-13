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
package org.apache.ozhera.mind.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import org.apache.ozhera.mind.service.swarm.SwarmAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;

/**
 * Worker internal API endpoints.
 * Called by Gateway, no authentication required.
 */
@Slf4j
@RestController
@RequestMapping("/worker/agent")
public class WorkerAgentController {

    @Resource
    private SwarmAgentService swarmAgentService;

    /**
     * Chat with agent (non-streaming).
     * Agent is created automatically if not exists for the user.
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.debug("Chat request for user: {}", request.getUsername());
        try {
            return swarmAgentService.chat(request);
        } catch (Exception e) {
            log.error("Chat failed", e);
            return ChatResponse.builder()
                    .errorMessage(e.getMessage())
                    .finished(true)
                    .build();
        }
    }

    /**
     * Chat with agent (streaming via SSE).
     * Agent is created automatically if not exists for the user.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.debug("Stream chat request for user: {}", request.getUsername());
        return swarmAgentService.chatStream(request)
                .map(content -> "data: " + content + "\n\n")
                .onErrorResume(e -> {
                    log.error("Stream chat failed", e);
                    return Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n");
                });
    }
}
