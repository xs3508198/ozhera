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

import com.xiaomi.mone.tpc.login.util.UserUtil;
import com.xiaomi.mone.tpc.login.vo.AuthUserVo;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.server.dto.ChatRequest;
import org.apache.ozhera.mind.server.dto.Result;
import org.apache.ozhera.mind.server.dto.UserConfigRequest;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.service.LlmService;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/llm")
public class AgentController {

    @Resource
    private LlmService llmService;

    @Resource
    private UserConfigService userConfigService;

    // ==================== Chat APIs ====================

    /**
     * Non-streaming chat
     */
    @PostMapping("/chat")
    public Result<String> chat(@RequestBody ChatRequest request) {
        try {
            AuthUserVo userInfo = UserUtil.getUser();
            if (userInfo == null) {
                return Result.error("User not logged in");
            }
            String username = userInfo.genFullAccount();

            Msg msg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(request.getMessage())
                    .build();

            io.agentscope.core.model.ChatResponse response = llmService.chat(username, List.of(msg));
            return Result.success(extractText(response));
        } catch (Exception e) {
            log.error("Chat failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * Streaming chat (SSE)
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        try {
            AuthUserVo userInfo = UserUtil.getUser();
            if (userInfo == null) {
                return Flux.just("data: [ERROR] User not logged in\n\n");
            }
            String username = userInfo.genFullAccount();

            Msg msg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(request.getMessage())
                    .build();

            return llmService.chatStream(username, List.of(msg))
                    .map(r -> "data: " + extractText(r) + "\n\n");
        } catch (Exception e) {
            log.error("Chat stream failed", e);
            return Flux.just("data: [ERROR] " + e.getMessage() + "\n\n");
        }
    }

    // ==================== User Config APIs ====================

    /**
     * Save or update current user's LLM config
     */
    @PostMapping("/config")
    public Result<UserConfig> saveOrUpdateConfig(@RequestBody UserConfigRequest request) {
        try {
            UserConfig config = UserConfig.builder()
                    .modelPlatform(request.getModelPlatform())
                    .modelType(request.getModelType())
                    .apiKey(request.getApiKey())
                    .build();

            UserConfig saved = userConfigService.saveOrUpdate(config);
            return Result.success(saved);
        } catch (Exception e) {
            log.error("Save config failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get current user's LLM config
     */
    @GetMapping("/config")
    public Result<UserConfig> getMyConfig() {
        try {
            UserConfig config = userConfigService.getMyConfig();
            return Result.success(config);
        } catch (Exception e) {
            log.error("Get config failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * Delete current user's LLM config
     */
    @DeleteMapping("/config")
    public Result<Void> deleteMyConfig() {
        try {
            userConfigService.deleteMyConfig();
            return Result.success(null);
        } catch (Exception e) {
            log.error("Delete config failed", e);
            return Result.error(e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    private String extractText(io.agentscope.core.model.ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.getContent()) {
            if (block instanceof TextBlock) {
                sb.append(((TextBlock) block).getText());
            }
        }
        return sb.toString();
    }
}
