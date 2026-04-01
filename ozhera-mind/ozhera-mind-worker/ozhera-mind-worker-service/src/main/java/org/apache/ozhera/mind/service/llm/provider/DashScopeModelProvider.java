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

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.llm.config.LlmProperties;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * DashScope Model Provider.
 * Activated when llm.provider=dashscope (default).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "dashscope", matchIfMissing = true)
public class DashScopeModelProvider implements ModelProviderService {

    @Resource
    private LlmProperties properties;

    @Override
    public Model createModel(UserConfig userConfig) {
        log.info("Creating DashScope model for user: {}, model: {}",
                userConfig.getUsername(), userConfig.getModelType());

        GenerateOptions options = GenerateOptions.builder()
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .topP(properties.getTopP())
                .build();

        return DashScopeChatModel.builder()
                .apiKey(userConfig.getApiKey())
                .modelName(userConfig.getModelType())
                .stream(true)
                .enableThinking(properties.getDashscopeEnableThinking())
                .enableSearch(properties.getDashscopeEnableSearch())
                .defaultOptions(options)
                .build();
    }
}
