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
package org.apache.ozhera.mind.gateway.controller;

import com.xiaomi.mone.tpc.login.util.UserUtil;
import com.xiaomi.mone.tpc.login.vo.AuthUserVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.AgentCreateRequest;
import org.apache.ozhera.mind.api.dto.AgentCreateResponse;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.api.dto.ChatResponse;
import org.apache.ozhera.mind.gateway.controller.dto.ApiResult;
import org.apache.ozhera.mind.gateway.controller.dto.CreateAgentReq;
import org.apache.ozhera.mind.gateway.controller.dto.SendMessageReq;
import org.apache.ozhera.mind.gateway.service.GatewayAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

/**
 * Gateway API endpoints for frontend.
 */
@Slf4j
@RestController
@RequestMapping("/api/mind")
public class GatewayController {

    @Resource
    private GatewayAgentService gatewayAgentService;

    /**
     * Create a new agent
     */
    @PostMapping("/agent/create")
    public Mono<ApiResult<AgentCreateResponse>> createAgent(@RequestBody CreateAgentReq req) {
        AuthUserVo userInfo = UserUtil.getUser();
        if (userInfo == null) {
            return Mono.just(ApiResult.error("User not logged in"));
        }

        AgentCreateRequest request = new AgentCreateRequest();
        request.setUserId(String.valueOf(userInfo.getUserId()));
        request.setUsername(userInfo.genFullAccount());
        request.setAgentName(req.getAgentName());
        request.setEnabledTools(req.getEnabledTools());
        request.setSystemPrompt(req.getSystemPrompt());

        return gatewayAgentService.createAgent(request)
                .map(response -> {
                    if (response.isSuccess()) {
                        return ApiResult.success(response);
                    } else {
                        return ApiResult.error(response.getErrorMessage());
                    }
                });
    }

    /**
     * Send a message to an agent (non-streaming)
     */
    @PostMapping("/agent/chat")
    public Mono<ApiResult<ChatResponse>> chat(@RequestBody SendMessageReq req) {
        AuthUserVo userInfo = UserUtil.getUser();
        if (userInfo == null) {
            return Mono.just(ApiResult.error("User not logged in"));
        }

        ChatRequest request = new ChatRequest();
        request.setAgentId(req.getAgentId());
        request.setUserId(String.valueOf(userInfo.getUserId()));
        request.setMessage(req.getMessage());
        request.setStream(false);

        return gatewayAgentService.chat(request)
                .map(response -> {
                    if (response.getErrorMessage() != null) {
                        return ApiResult.error(response.getErrorMessage());
                    }
                    return ApiResult.success(response);
                });
    }

    /**
     * Send a message to an agent (streaming via SSE)
     */
    @PostMapping(value = "/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody SendMessageReq req) {
        AuthUserVo userInfo = UserUtil.getUser();
        if (userInfo == null) {
            return Flux.just("data: {\"error\": \"User not logged in\"}\n\n");
        }

        ChatRequest request = new ChatRequest();
        request.setAgentId(req.getAgentId());
        request.setUserId(String.valueOf(userInfo.getUserId()));
        request.setMessage(req.getMessage());
        request.setStream(true);

        return gatewayAgentService.chatStream(request);
    }

    /**
     * Destroy an agent
     */
    @DeleteMapping("/agent/{agentId}")
    public Mono<ApiResult<Boolean>> destroyAgent(@PathVariable String agentId) {
        AuthUserVo userInfo = UserUtil.getUser();
        if (userInfo == null) {
            return Mono.just(ApiResult.error("User not logged in"));
        }

        return gatewayAgentService.destroyAgent(agentId)
                .map(ApiResult::success);
    }
}
