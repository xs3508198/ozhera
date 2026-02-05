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
package org.apache.ozhera.log.manager.service.extension.ai.llm;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.xiaomi.youpin.docean.anno.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ozhera.log.common.Config;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.apache.ozhera.log.manager.service.extension.ai.llm.LlmService.DEFAULT_LLM_SERVICE_KEY;

/**
 * Default LLM Service implementation using direct HTTP calls.
 * Uses OpenAI-compatible API format, works with OpenAI, internal LLM services, etc.
 */
@Service(name = DEFAULT_LLM_SERVICE_KEY)
@Slf4j
public class DefaultLlmService implements LlmService {

    private static final Gson gson = new Gson();
    private static final int MAX_RETRY_COUNT = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;
    private static final int MAX_RETRY_DELAY_MS = 5000;

    private String apiUrl;
    private String apiKey;
    private String modelName;
    private String modelProviderId;
    private int requestTimeout;

    @PostConstruct
    public void init() {
        this.apiUrl = Config.ins().get("ai.llm.base-url", "");
        this.apiKey = Config.ins().get("ai.llm.api-key", "");
        this.modelName = Config.ins().get("ai.llm.model", "");
        this.modelProviderId = Config.ins().get("ai.llm.model-provider-id", "");
        this.requestTimeout = Integer.parseInt(Config.ins().get("ai.llm.timeout", "120")) * 1000;

        log.info("DefaultLlmService initialized with apiUrl: {}, model: {}", apiUrl, modelName);
    }

    @Override
    public String chat(String prompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.user(prompt));
        return chat(messages, null);
    }

    @Override
    public String chat(List<ChatMessage> messages, String systemPrompt) {
        LlmRequest request = buildRequest(messages, systemPrompt, false);
        LlmResponse response = executeWithRetry(request);
        return extractContent(response);
    }

    @Override
    public void streamChat(List<ChatMessage> messages, String systemPrompt,
                           Consumer<String> onToken, Consumer<String> onComplete, Consumer<Throwable> onError) {
        LlmRequest request = buildRequest(messages, systemPrompt, true);

        try {
            String reqBody = gson.toJson(request);
            log.debug("Stream LLM request: {}", reqBody);

            HttpRequest httpRequest = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(reqBody)
                    .timeout(requestTimeout);

            if (StringUtils.isNotBlank(modelProviderId)) {
                httpRequest.header("X-Model-Provider-Id", modelProviderId);
            }

            HttpResponse response = httpRequest.execute();

            if (response.isOk()) {
                String responseBody = response.body();
                parseStreamResponse(responseBody, onToken, onComplete);
            } else {
                log.error("Stream LLM call failed, status: {}, body: {}", response.getStatus(), response.body());
                if (onError != null) {
                    onError.accept(new RuntimeException("LLM call failed, status: " + response.getStatus()));
                }
            }
        } catch (Exception e) {
            log.error("Stream LLM call exception", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    private LlmRequest buildRequest(List<ChatMessage> messages, String systemPrompt, boolean stream) {
        LlmRequest request = new LlmRequest();
        request.setModel(modelName);
        request.setStream(stream);

        List<LlmMessage> llmMessages = new ArrayList<>();

        if (StringUtils.isNotBlank(systemPrompt)) {
            llmMessages.add(new LlmMessage("system", systemPrompt));
        }

        for (ChatMessage msg : messages) {
            String role = msg.getRole().name().toLowerCase();
            llmMessages.add(new LlmMessage(role, msg.getContent()));
        }

        request.setMessages(llmMessages);
        return request;
    }

    private LlmResponse executeWithRetry(LlmRequest request) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                if (retryCount > 0) {
                    log.info("Retry attempt {} for LLM call", retryCount);
                    int delayMs = Math.min(INITIAL_RETRY_DELAY_MS * (1 << (retryCount - 1)), MAX_RETRY_DELAY_MS);
                    Thread.sleep(delayMs);
                }

                LlmResponse response = doRequest(request);
                if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                    return response;
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM call failed (retry {}/{}): {}", retryCount, MAX_RETRY_COUNT, e.getMessage());

                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    retryCount++;
                    continue;
                } else {
                    log.error("LLM call failed due to non-429 error, no further retries: {}", e.getMessage());
                    break;
                }
            }
            retryCount++;
        }

        if (lastException != null) {
            log.error("After {} retries, LLM call still failed: {}", MAX_RETRY_COUNT, lastException.getMessage());
        }
        return new LlmResponse();
    }

    private LlmResponse doRequest(LlmRequest request) {
        String reqBody = gson.toJson(request);
        log.debug("LLM request: {}", reqBody);

        HttpRequest httpRequest = HttpRequest.post(apiUrl)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(reqBody)
                .timeout(requestTimeout);

        if (StringUtils.isNotBlank(modelProviderId)) {
            httpRequest.header("X-Model-Provider-Id", modelProviderId);
        }

        HttpResponse response = httpRequest.execute();

        if (response.isOk()) {
            log.debug("LLM response: {}", response.body());
            return gson.fromJson(response.body(), LlmResponse.class);
        } else {
            log.error("LLM call failed, status: {}, body: {}", response.getStatus(), response.body());
            throw new RuntimeException("LLM call failed, status: " + response.getStatus());
        }
    }

    private void parseStreamResponse(String responseBody, Consumer<String> onToken, Consumer<String> onComplete) {
        StringBuilder contentBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new StringReader(responseBody))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || !line.startsWith("data: ")) {
                    continue;
                }

                String jsonData = line.substring(6);

                if ("[DONE]".equals(jsonData)) {
                    break;
                }

                try {
                    LlmResponse streamChunk = gson.fromJson(jsonData, LlmResponse.class);

                    if (streamChunk != null && streamChunk.getChoices() != null && !streamChunk.getChoices().isEmpty()) {
                        LlmChoice choice = streamChunk.getChoices().get(0);

                        if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                            String token = choice.getDelta().getContent();
                            contentBuilder.append(token);
                            if (onToken != null) {
                                onToken.accept(token);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse stream chunk: {}", jsonData, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse stream response", e);
        }

        if (onComplete != null) {
            onComplete.accept(contentBuilder.toString());
        }
    }

    private String extractContent(LlmResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return "";
        }
        LlmChoice choice = response.getChoices().get(0);
        if (choice.getMessage() != null) {
            return choice.getMessage().getContent();
        }
        return "";
    }

    // Request/Response DTOs

    @Data
    public static class LlmRequest {
        private String model;
        private List<LlmMessage> messages;
        private Boolean stream;
        private Double temperature;
        @SerializedName("max_tokens")
        private Integer maxTokens;
    }

    @Data
    public static class LlmMessage {
        private String role;
        private String content;

        public LlmMessage() {}

        public LlmMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    public static class LlmResponse {
        private String id;
        private String object;
        private Long created;
        private String model;
        private List<LlmChoice> choices;
        private LlmUsage usage;
    }

    @Data
    public static class LlmChoice {
        private Integer index;
        private LlmMessage message;
        private LlmMessage delta;
        @SerializedName("finish_reason")
        private String finishReason;
    }

    @Data
    public static class LlmUsage {
        @SerializedName("prompt_tokens")
        private Integer promptTokens;
        @SerializedName("completion_tokens")
        private Integer completionTokens;
        @SerializedName("total_tokens")
        private Integer totalTokens;
    }
}
