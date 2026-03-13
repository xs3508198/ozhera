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
package org.apache.ozhera.mind.service.llm;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.llm.config.LlmProperties;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ChatModelFactory {

    @Resource
    private LlmProperties properties;

    public Model createModel(UserConfig config) {
        LlmProvider provider = LlmProvider.fromCode(config.getModelPlatform());

        switch (provider) {
            case OPENAI:
                return buildOpenAiModel(config);
            case DASHSCOPE:
                return buildDashScopeModel(config);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }

    private Model buildOpenAiModel(UserConfig config) {
        return OpenAIChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelType())
                .stream(true)
                .generateOptions(buildGenerateOptions())
                .build();
    }

    private Model buildDashScopeModel(UserConfig config) {
        return DashScopeChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelType())
                .stream(true)
                .enableThinking(properties.getDashscopeEnableThinking())
                .enableSearch(properties.getDashscopeEnableSearch())
                .defaultOptions(buildGenerateOptions())
                .build();
    }

    private GenerateOptions buildGenerateOptions() {
        return GenerateOptions.builder()
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .topP(properties.getTopP())
                .build();
    }
}
