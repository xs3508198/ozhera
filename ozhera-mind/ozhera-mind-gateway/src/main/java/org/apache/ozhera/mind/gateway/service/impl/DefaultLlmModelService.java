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
package org.apache.ozhera.mind.gateway.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ModelInfo;
import org.apache.ozhera.mind.gateway.service.LlmModelService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of LlmModelService.
 */
@Slf4j
@Service
public class DefaultLlmModelService implements LlmModelService {

    private static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DASHSCOPE_MODELS_URL = "https://dashscope.aliyuncs.com/api/v1/models";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public List<ModelInfo> listModels(String providerCode, String apiKey) {
        log.info("Listing models for provider: {}", providerCode);

        if ("openai".equalsIgnoreCase(providerCode)) {
            return listOpenAiModels(apiKey);
        } else if ("dashscope".equalsIgnoreCase(providerCode)) {
            return listDashScopeModels(apiKey);
        } else {
            throw new IllegalArgumentException("Unsupported provider: " + providerCode);
        }
    }

    private List<ModelInfo> listOpenAiModels(String apiKey) {
        log.debug("Listing OpenAI models");
        try {
            String url = OPENAI_DEFAULT_BASE_URL + "/models";

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

    private List<ModelInfo> parseOpenAiModelsResponse(String responseBody) {
        JSONObject root = JSON.parseObject(responseBody);
        JSONArray data = root.getJSONArray("data");
        List<ModelInfo> models = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                JSONObject model = data.getJSONObject(i);
                String id = model.getString("id");
                String owner = model.getString("owned_by");
                if (id != null) {
                    models.add(ModelInfo.builder()
                            .modelId(id)
                            .owner(owner)
                            .build());
                }
            }
        }
        log.info("Found {} OpenAI models", models.size());
        return models;
    }

    private List<ModelInfo> parseDashScopeModelsResponse(String responseBody) {
        JSONObject root = JSON.parseObject(responseBody);
        JSONObject output = root.getJSONObject("output");
        List<ModelInfo> models = new ArrayList<>();
        if (output != null) {
            JSONArray modelsArray = output.getJSONArray("models");
            if (modelsArray != null) {
                for (int i = 0; i < modelsArray.size(); i++) {
                    JSONObject model = modelsArray.getJSONObject(i);
                    String modelId = model.getString("model");
                    String name = model.getString("name");
                    if (modelId != null) {
                        models.add(ModelInfo.builder()
                                .modelId(modelId)
                                .owner(name)
                                .build());
                    }
                }
            }
        }
        log.info("Found {} DashScope models", models.size());
        return models;
    }
}
