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
package org.apache.ozhera.mind.service.llm.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.llm.provider.DashScopeModelProvider;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.llm.provider.OpenAIModelProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration for Model Providers.
 *
 * Configure llm.provider in properties to select the provider:
 * - openai: Use OpenAI GPT models
 * - dashscope: Use Alibaba DashScope/Qwen models
 *
 * For internal network versions, implement ModelProviderService interface
 * and configure llm.provider to your custom provider name.
 */
@Slf4j
@Configuration
public class ModelProviderAutoConfiguration {

    /**
     * OpenAI Model Provider
     * Activated when llm.provider=openai
     */
    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
    public ModelProviderService openAIModelProvider(LlmProperties properties) {
        log.info("Loading OpenAI Model Provider");
        return new OpenAIModelProvider(properties);
    }

    /**
     * DashScope Model Provider
     * Activated when llm.provider=dashscope
     */
    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "dashscope")
    public ModelProviderService dashScopeModelProvider(LlmProperties properties) {
        log.info("Loading DashScope Model Provider");
        return new DashScopeModelProvider(properties);
    }

    /**
     * Default fallback provider (DashScope)
     * Used when no llm.provider is specified and no other provider is configured
     */
    @Bean
    @ConditionalOnMissingBean(ModelProviderService.class)
    public ModelProviderService defaultModelProvider(LlmProperties properties) {
        log.info("No llm.provider configured, using default DashScope provider");
        return new DashScopeModelProvider(properties);
    }
}
