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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ModelInfo;
import org.apache.ozhera.mind.service.llm.config.LlmProperties;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ChatModelFactory {

    private static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DASHSCOPE_MODELS_URL = "https://dashscope.aliyuncs.com/api/v1/models";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

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

    /**
     * List available models for a given provider.
     * Internal network versions can override this method to return their own model list.
     *
     * @param providerCode the provider code (e.g., "openai", "dashscope")
     * @param apiKey the API key for authentication
     * @return list of available models with owner info
     */
    public List<ModelInfo> listModels(String providerCode, String apiKey) {
        LlmProvider provider = LlmProvider.fromCode(providerCode);

        switch (provider) {
            case OPENAI:
                return listOpenAiModels(apiKey);
            case DASHSCOPE:
                return listDashScopeModels(apiKey);
            default:
                throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }

    private List<ModelInfo> listOpenAiModels(String apiKey) {
        log.debug("Listing OpenAI models");
        try {
            String baseUrl = properties.getOpenaiBaseUrl() != null && !properties.getOpenaiBaseUrl().isEmpty()
                    ? properties.getOpenaiBaseUrl() : OPENAI_DEFAULT_BASE_URL;
            String url = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to list OpenAI models, status: {}, body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to list models: " + response.statusCode());
            }

            return parseOpenAiModelsResponse(response.body());
        } catch (Exception e) {
            log.error("Failed to list OpenAI models", e);
            throw new RuntimeException("Failed to list OpenAI models: " + e.getMessage(), e);
        }
    }

    private List<ModelInfo> listDashScopeModels(String apiKey) {
        log.debug("Listing DashScope models");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DASHSCOPE_MODELS_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to list DashScope models, status: {}, body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to list models: " + response.statusCode());
            }

            return parseDashScopeModelsResponse(response.body());
        } catch (Exception e) {
            log.error("Failed to list DashScope models", e);
            throw new RuntimeException("Failed to list DashScope models: " + e.getMessage(), e);
        }
    }

    private List<ModelInfo> parseOpenAiModelsResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");
        List<ModelInfo> models = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode model : data) {
                JsonNode idNode = model.get("id");
                JsonNode ownerNode = model.get("owned_by");
                if (idNode != null) {
                    models.add(ModelInfo.builder()
                            .modelId(idNode.asText())
                            .owner(ownerNode != null ? ownerNode.asText() : null)
                            .build());
                }
            }
        }
        log.info("Found {} models", models.size());
        return models;
    }

    private List<ModelInfo> parseDashScopeModelsResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode output = root.get("output");
        List<ModelInfo> models = new ArrayList<>();
        if (output != null) {
            JsonNode modelsNode = output.get("models");
            if (modelsNode != null && modelsNode.isArray()) {
                for (JsonNode model : modelsNode) {
                    JsonNode modelIdNode = model.get("model");
                    JsonNode nameNode = model.get("name");
                    if (modelIdNode != null) {
                        models.add(ModelInfo.builder()
                                .modelId(modelIdNode.asText())
                                .owner(nameNode != null ? nameNode.asText() : null)
                                .build());
                    }
                }
            }
        }
        log.info("Found {} DashScope models", models.size());
        return models;
    }
}
