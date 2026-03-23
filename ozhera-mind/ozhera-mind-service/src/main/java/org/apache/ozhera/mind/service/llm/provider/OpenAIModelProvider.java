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
package org.apache.ozhera.mind.service.llm.provider;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.llm.config.LlmProperties;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class OpenAIModelProvider implements ModelProviderService {

    private final OpenAIChatModel model;
    private final String providerName = "openai";

    public OpenAIModelProvider(LlmProperties properties) {
        log.info("Initializing OpenAI Model Provider, model: {}", properties.getOpenaiModel());

        GenerateOptions options = GenerateOptions.builder()
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .topP(properties.getTopP())
                .build();

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(properties.getOpenaiApiKey())
                .modelName(properties.getOpenaiModel())
                .stream(true)
                .generateOptions(options);

        if (properties.getOpenaiBaseUrl() != null && !properties.getOpenaiBaseUrl().isEmpty()) {
            builder.baseUrl(properties.getOpenaiBaseUrl());
        }

        this.model = builder.build();
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public ChatResponse chat(List<Msg> messages) {
        log.debug("OpenAI chat with {} messages", messages.size());
        try {
            Flux<ChatResponse> responseFlux = model.stream(messages, Collections.emptyList(), null);
            return responseFlux.reduce(this::mergeResponses).block();
        } catch (Exception e) {
            log.error("OpenAI chat failed", e);
            throw new RuntimeException("OpenAI chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<ChatResponse> chatStream(List<Msg> messages) {
        log.debug("OpenAI chat stream with {} messages", messages.size());
        try {
            return model.stream(messages, Collections.emptyList(), null);
        } catch (Exception e) {
            log.error("OpenAI chat stream failed", e);
            return Flux.error(new RuntimeException("OpenAI chat stream failed: " + e.getMessage(), e));
        }
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
