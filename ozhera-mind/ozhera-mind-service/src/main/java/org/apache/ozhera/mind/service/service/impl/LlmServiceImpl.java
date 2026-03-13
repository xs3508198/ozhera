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

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.service.LlmService;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.apache.ozhera.mind.service.llm.ChatModelFactory;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class LlmServiceImpl implements LlmService {

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private UserConfigService userConfigService;

    @Override
    public ChatResponse chat(String username, List<Msg> messages) {
        UserConfig config = getUserConfig(username);
        Model model = chatModelFactory.createModel(config);

        log.debug("Chat with model: {}", config.getModelType());
        try {
            Flux<ChatResponse> responseFlux = model.stream(messages, Collections.emptyList(), null);
            return responseFlux.reduce(this::mergeResponses).block();
        } catch (Exception e) {
            log.error("Chat failed", e);
            throw new RuntimeException("Chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<ChatResponse> chatStream(String username, List<Msg> messages) {
        UserConfig config = getUserConfig(username);
        Model model = chatModelFactory.createModel(config);

        log.debug("Chat stream with model: {}", config.getModelType());
        try {
            return model.stream(messages, Collections.emptyList(), null);
        } catch (Exception e) {
            log.error("Chat stream failed", e);
            return Flux.error(new RuntimeException("Chat stream failed: " + e.getMessage(), e));
        }
    }

    private UserConfig getUserConfig(String username) {
        UserConfig config = userConfigService.getByUsername(username);
        if (config == null) {
            throw new RuntimeException("No LLM config found for user: " + username);
        }
        return config;
    }

    private ChatResponse mergeResponses(ChatResponse r1, ChatResponse r2) {
        List<ContentBlock> mergedContent = new ArrayList<>();
        if (r1.getContent() != null) {
            mergedContent.addAll(r1.getContent());
        }
        if (r2.getContent() != null) {
            mergedContent.addAll(r2.getContent());
        }
        return ChatResponse.builder()
                .content(mergedContent)
                .finishReason(r2.getFinishReason())
                .usage(r2.getUsage())
                .build();
    }
}
