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
package org.apache.ozhera.log.manager.service.impl;

import com.google.gson.Gson;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.xiaomi.youpin.docean.anno.Service;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.log.common.Config;
import org.apache.ozhera.log.common.Result;
import org.apache.ozhera.log.exception.CommonError;
import org.apache.ozhera.log.manager.common.context.MoneUserContext;
import org.apache.ozhera.log.manager.model.bo.BotQAParam;
import org.apache.ozhera.log.manager.model.dto.AiAnalysisHistoryDTO;
import org.apache.ozhera.log.manager.model.dto.LogAiAnalysisDTO;
import org.apache.ozhera.log.manager.model.vo.LogAiAnalysisResponse;
import org.apache.ozhera.log.manager.service.MilogAiAnalysisService;
import org.apache.ozhera.log.manager.service.extension.ai.llm.LlmService;
import org.apache.ozhera.log.manager.service.extension.ai.llm.LlmService.ChatMessage;
import org.apache.ozhera.log.manager.service.extension.ai.llm.LlmServiceFactory;
import org.apache.ozhera.log.manager.service.extension.ai.memory.ChatMemoryService;
import org.apache.ozhera.log.manager.service.extension.ai.memory.ChatMemoryService.ConversationContext;
import org.apache.ozhera.log.manager.service.extension.ai.memory.ChatMemoryService.ConversationSummary;
import org.apache.ozhera.log.manager.service.extension.ai.memory.ChatMemoryService.QAPair;
import org.apache.ozhera.log.manager.service.extension.ai.memory.ChatMemoryServiceFactory;
import org.apache.ozhera.log.manager.service.extension.ai.preprocessor.LogPreprocessor;
import org.apache.ozhera.log.manager.service.extension.ai.preprocessor.LogPreprocessorFactory;
import org.apache.ozhera.log.manager.service.extension.ai.ratelimit.AiRateLimiter;
import org.apache.ozhera.log.manager.service.extension.ai.ratelimit.AiRateLimiterFactory;
import org.apache.ozhera.log.manager.service.extension.ai.summarizer.ConversationSummarizer;
import org.apache.ozhera.log.manager.service.extension.ai.summarizer.ConversationSummarizerFactory;
import org.apache.ozhera.log.manager.user.MoneUser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;

@Slf4j
@Service
public class MilogAiAnalysisServiceImpl implements MilogAiAnalysisService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            ## Role
            You are an AI log analysis assistant, responsible for analyzing and explaining log data, providing clear summaries and easy-to-understand explanations for users.

            ### Skills
            - Multi-format log analysis: Able to parse and understand log files from different sources and formats.
            - Key information extraction: Able to extract important information and discover patterns from complex log data.
            - Anomaly detection and diagnosis: Quickly identify potential problems or abnormal behaviors based on log content.
            - Technical language translation: Convert complex technical log information into user-friendly explanations.
            - Real-time data processing: Capable of real-time monitoring and analysis of log data streams.
            - Multi-turn Q&A processing: Able to understand and utilize historical Q&A data to accurately answer current questions.

            ### Constraints
            - Strict confidentiality: Strictly protect user data privacy and security when analyzing and explaining logs.
            - High accuracy: Ensure explanations accurately reflect the actual content of log data.
            - Quick response: Provide required log explanations and support promptly when users make requests.
            - Wide compatibility: Compatible with and support analysis of log files from various sources and formats.
            - User-friendliness: Ensure explanation results are concise and clear, easy to understand even for non-technical users.
            - Strong scalability: Continuously adapt and provide effective analysis as log data volume and complexity grow.
            - Language matching: Respond in the SAME LANGUAGE as the user's input. If the user writes in Chinese, respond in Chinese. If in English, respond in English.

            Below is the log content or user question you need to analyze:
            """;

    private static final Gson gson = new Gson();

    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    private static final Encoding TOKENIZER = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private int conversationTokenThreshold;
    private int requestTokenLimit;
    private int logPreprocessMaxTokens;
    private String systemPrompt;

    public void init() {
        this.conversationTokenThreshold = Integer.parseInt(
                Config.ins().get("ai.conversation.token-threshold", "50000"));
        this.requestTokenLimit = Integer.parseInt(
                Config.ins().get("ai.request.token-limit", "15000"));
        this.logPreprocessMaxTokens = Integer.parseInt(
                Config.ins().get("ai.log.preprocess-max-tokens", "10000"));
        this.systemPrompt = Config.ins().get("ai.system-prompt", DEFAULT_SYSTEM_PROMPT);

        long initialDelay = calculateDelayToTargetHour(3);
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanExpiredConversations();
            } catch (Exception e) {
                log.error("Scheduled cleanup task failed", e);
            }
        }, initialDelay, 24 * 60, MINUTES);
        log.info("Scheduled AI conversation cleanup task initialized, will run at 3:00 AM every day, initial delay: {} minutes", initialDelay);
    }

    @Override
    public Result<LogAiAnalysisResponse> tailLogAiAnalysis(LogAiAnalysisDTO tailLogAiAnalysisDTO) {
        String validationError = validateRequest(tailLogAiAnalysisDTO);
        if (validationError != null) {
            return Result.failParam(validationError);
        }

        // Check rate limit
        MoneUser user = MoneUserContext.getCurrentUser();
        AiRateLimiter rateLimiter = AiRateLimiterFactory.getAiRateLimiter();
        if (!rateLimiter.isAllowed(user.getUser())) {
            long resetTime = rateLimiter.getResetTimeSeconds(user.getUser());
            return Result.failParam("Rate limit exceeded. Please try again in " + resetTime + " seconds");
        }
        rateLimiter.recordRequest(user.getUser());

        List<String> logs = tailLogAiAnalysisDTO.getLogs();

        LogPreprocessor preprocessor = LogPreprocessorFactory.getLogPreprocessor();
        String processedLog;
        if (preprocessor.needsPreprocessing(logs, getLogPreprocessMaxTokens())) {
            processedLog = preprocessor.preprocess(logs, getLogPreprocessMaxTokens());
            log.info("Log preprocessed: original {} lines, processed to {} tokens",
                    logs.size(), preprocessor.countTokens(processedLog));
        } else {
            processedLog = String.join("\n", logs);
        }

        if (countTokens(processedLog) > getRequestTokenLimit()) {
            return Result.failParam("The length of the input information reaches the maximum limit");
        }

        LogAiAnalysisResponse response = new LogAiAnalysisResponse();
        Long conversationId = tailLogAiAnalysisDTO.getConversationId();

        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        LlmService llmService = LlmServiceFactory.getLlmService();

        if (conversationId == null) {
            String answer;
            long startTime = System.currentTimeMillis();
            try {
                List<ChatMessage> messages = buildMessages(null, processedLog);
                answer = llmService.chat(messages, getSystemPrompt());
            } catch (Exception e) {
                log.error("An error occurred in the request for the large model, err: {}", e.getMessage());
                return Result.fail(CommonError.SERVER_ERROR.getCode(), "An error occurred in the request for the large model");
            }
            long processingTime = System.currentTimeMillis() - startTime;

            conversationId = memoryService.createConversation(
                    tailLogAiAnalysisDTO.getStoreId(),
                    user.getUser(),
                    processedLog,
                    answer
            );

            response.setConversationId(conversationId);
            response.setContent(answer);
            response.setProcessingTimeMs(processingTime);
            response.setModelUsed(llmService.getModelName());
            response.setRemainingRequests(rateLimiter.getRemainingRequests(user.getUser()));
            response.setTokensUsed(countTokens(processedLog) + countTokens(answer));
            return Result.success(response);
        } else {
            ConversationContext context = memoryService.getConversation(conversationId);
            if (context == null) {
                return Result.failParam("Conversation not found");
            }

            // Check conversation ownership
            String ownershipError = checkConversationOwnership(conversationId, user.getUser());
            if (ownershipError != null) {
                return Result.failParam(ownershipError);
            }

            long startTime = System.currentTimeMillis();
            AnalysisResult analysisResult = processHistoryConversation(context, processedLog);
            String answer = analysisResult.getAnswer();
            long processingTime = System.currentTimeMillis() - startTime;

            String nowTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            QAPair newQA = new QAPair(nowTimeStr, processedLog, answer);

            List<QAPair> modelHistory;
            if (analysisResult.getSummarizedModelHistory() != null) {
                modelHistory = new ArrayList<>(analysisResult.getSummarizedModelHistory());
            } else {
                modelHistory = new ArrayList<>(context.getModelHistory());
            }
            modelHistory.add(newQA);

            List<QAPair> originalHistory = new ArrayList<>(context.getOriginalHistory());
            originalHistory.add(newQA);

            context.setModelHistory(modelHistory);
            context.setOriginalHistory(originalHistory);
            memoryService.saveConversation(conversationId, context);

            response.setConversationId(conversationId);
            response.setContent(answer);
            response.setProcessingTimeMs(processingTime);
            response.setModelUsed(llmService.getModelName());
            response.setRemainingRequests(rateLimiter.getRemainingRequests(user.getUser()));
            response.setTokensUsed(countTokens(processedLog) + countTokens(answer));
            return Result.success(response);
        }
    }

    @Override
    public void streamAiAnalysis(LogAiAnalysisDTO dto, Consumer<String> onToken,
                                  Consumer<LogAiAnalysisResponse> onComplete, Consumer<String> onError) {
        String validationError = validateRequest(dto);
        if (validationError != null) {
            if (onError != null) onError.accept(validationError);
            return;
        }

        // Check rate limit
        MoneUser user = MoneUserContext.getCurrentUser();
        AiRateLimiter rateLimiter = AiRateLimiterFactory.getAiRateLimiter();
        if (!rateLimiter.isAllowed(user.getUser())) {
            long resetTime = rateLimiter.getResetTimeSeconds(user.getUser());
            if (onError != null) onError.accept("Rate limit exceeded. Please try again in " + resetTime + " seconds");
            return;
        }
        rateLimiter.recordRequest(user.getUser());

        List<String> logs = dto.getLogs();
        LogPreprocessor preprocessor = LogPreprocessorFactory.getLogPreprocessor();
        String processedLog;
        if (preprocessor.needsPreprocessing(logs, getLogPreprocessMaxTokens())) {
            processedLog = preprocessor.preprocess(logs, getLogPreprocessMaxTokens());
        } else {
            processedLog = String.join("\n", logs);
        }

        if (countTokens(processedLog) > getRequestTokenLimit()) {
            if (onError != null) onError.accept("The length of the input information reaches the maximum limit");
            return;
        }

        Long conversationId = dto.getConversationId();
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        LlmService llmService = LlmServiceFactory.getLlmService();

        List<ChatMessage> messages;
        ConversationContext context = null;

        if (conversationId != null) {
            context = memoryService.getConversation(conversationId);
            if (context == null) {
                if (onError != null) onError.accept("Conversation not found");
                return;
            }
            String ownershipError = checkConversationOwnership(conversationId, user.getUser());
            if (ownershipError != null) {
                if (onError != null) onError.accept(ownershipError);
                return;
            }
            messages = buildMessages(context.getModelHistory(), processedLog);
        } else {
            messages = buildMessages(null, processedLog);
        }

        final ConversationContext finalContext = context;
        final String finalProcessedLog = processedLog;
        final long startTime = System.currentTimeMillis();
        final int inputTokens = countTokens(finalProcessedLog);

        llmService.streamChat(messages, getSystemPrompt(),
                onToken,
                fullResponse -> {
                    long processingTime = System.currentTimeMillis() - startTime;

                    // Save conversation after completion
                    Long newConversationId = dto.getConversationId();
                    String nowTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    QAPair newQA = new QAPair(nowTimeStr, finalProcessedLog, fullResponse);

                    if (newConversationId == null) {
                        newConversationId = memoryService.createConversation(
                                dto.getStoreId(), user.getUser(), finalProcessedLog, fullResponse);
                    } else {
                        List<QAPair> modelHistory = new ArrayList<>(finalContext.getModelHistory());
                        modelHistory.add(newQA);
                        List<QAPair> originalHistory = new ArrayList<>(finalContext.getOriginalHistory());
                        originalHistory.add(newQA);
                        finalContext.setModelHistory(modelHistory);
                        finalContext.setOriginalHistory(originalHistory);
                        memoryService.saveConversation(newConversationId, finalContext);
                    }

                    if (onComplete != null) {
                        LogAiAnalysisResponse response = new LogAiAnalysisResponse();
                        response.setConversationId(newConversationId);
                        response.setContent(fullResponse);
                        response.setProcessingTimeMs(processingTime);
                        response.setModelUsed(llmService.getModelName());
                        response.setRemainingRequests(rateLimiter.getRemainingRequests(user.getUser()));
                        response.setTokensUsed(inputTokens + countTokens(fullResponse));
                        onComplete.accept(response);
                    }
                },
                error -> {
                    if (onError != null) onError.accept(error.getMessage());
                }
        );
    }

    private String validateRequest(LogAiAnalysisDTO dto) {
        if (dto == null) {
            return "Request cannot be null";
        }
        if (dto.getStoreId() == null) {
            return "Store id is null";
        }
        if (dto.getLogs() == null || dto.getLogs().isEmpty()) {
            return "Logs cannot be empty";
        }
        return null;
    }

    private String checkConversationOwnership(Long conversationId, String currentUser) {
        // TODO: Implement proper ownership check by querying the conversation's creator
        // For now, we trust that the conversation ID check is sufficient
        // In a full implementation, you would query the database to verify the creator matches currentUser
        return null;
    }

    private AnalysisResult processHistoryConversation(ConversationContext context, String latestQuestion) {
        List<QAPair> modelHistory = context.getModelHistory();
        List<QAPair> originalHistory = context.getOriginalHistory();
        AnalysisResult res = new AnalysisResult();

        try {
            LlmService llmService = LlmServiceFactory.getLlmService();
            ConversationSummarizer summarizer = ConversationSummarizerFactory.getConversationSummarizer();

            List<ChatMessage> messages = buildMessages(modelHistory, latestQuestion);
            String messagesJson = gson.toJson(messages);

            if (countTokens(messagesJson) < getConversationTokenThreshold()) {
                String answer = llmService.chat(messages, getSystemPrompt());
                res.setAnswer(answer);
                return res;
            } else {
                return analysisAndSummarize(modelHistory, originalHistory, latestQuestion, summarizer);
            }
        } catch (Exception e) {
            log.error("An error occurred in the request for the large model, err: {}", e.getMessage());
        }
        return res;
    }

    private AnalysisResult analysisAndSummarize(List<QAPair> modelHistory, List<QAPair> originalHistory,
                                                 String latestQuestion, ConversationSummarizer summarizer) {
        AnalysisResult analysisResult = new AnalysisResult();
        LlmService llmService = LlmServiceFactory.getLlmService();

        AtomicReference<String> answer = new AtomicReference<>("");
        String prompt = getSystemPrompt();
        Future<?> analysisFuture = executor.submit(() -> {
            try {
                List<ChatMessage> messages = buildMessages(modelHistory, latestQuestion);
                String result = llmService.chat(messages, prompt);
                answer.set(result);
            } catch (Exception e) {
                log.error("Analysis task error: {}", e.getMessage());
            }
        });

        AtomicReference<List<QAPair>> newModelHistory = new AtomicReference<>(modelHistory);
        Future<?> summarizeFuture = executor.submit(() -> {
            try {
                int targetTokens = summarizer.getTargetTokenCount();
                List<QAPair> summarized = summarizer.summarize(originalHistory, targetTokens);
                if (summarized != null && !summarized.isEmpty()) {
                    newModelHistory.set(summarized);
                }
            } catch (Exception e) {
                log.error("Summarization task error: {}", e.getMessage());
            }
        });

        try {
            analysisFuture.get();
            summarizeFuture.get();
            analysisResult.setAnswer(answer.get());
            analysisResult.setSummarizedModelHistory(newModelHistory.get());
        } catch (Exception e) {
            log.error("Analysis and summarization task execution error: {}", e.getMessage());
        }
        return analysisResult;
    }

    private List<ChatMessage> buildMessages(List<QAPair> history, String latestQuestion) {
        List<ChatMessage> messages = new ArrayList<>();

        if (history != null && !history.isEmpty()) {
            for (QAPair qa : history) {
                if (qa.getUser() != null && !qa.getUser().isBlank()) {
                    messages.add(ChatMessage.user(qa.getUser()));
                }
                if (qa.getBot() != null && !qa.getBot().isBlank()) {
                    messages.add(ChatMessage.assistant(qa.getBot()));
                }
            }
        }

        messages.add(ChatMessage.user(latestQuestion));
        return messages;
    }

    private int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return TOKENIZER.countTokens(text);
    }

    private int getConversationTokenThreshold() {
        if (conversationTokenThreshold <= 0) {
            conversationTokenThreshold = Integer.parseInt(
                    Config.ins().get("ai.conversation.token-threshold", "50000"));
        }
        return conversationTokenThreshold;
    }

    private int getRequestTokenLimit() {
        if (requestTokenLimit <= 0) {
            requestTokenLimit = Integer.parseInt(
                    Config.ins().get("ai.request.token-limit", "15000"));
        }
        return requestTokenLimit;
    }

    private int getLogPreprocessMaxTokens() {
        if (logPreprocessMaxTokens <= 0) {
            logPreprocessMaxTokens = Integer.parseInt(
                    Config.ins().get("ai.log.preprocess-max-tokens", "10000"));
        }
        return logPreprocessMaxTokens;
    }

    private String getSystemPrompt() {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = Config.ins().get("ai.system-prompt", DEFAULT_SYSTEM_PROMPT);
        }
        return systemPrompt;
    }

    @Override
    public void shutdown() {
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        memoryService.persistAllConversations();
    }

    @Override
    public Result<List<AiAnalysisHistoryDTO>> getAiHistoryList(Long storeId) {
        MoneUser user = MoneUserContext.getCurrentUser();
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();

        List<ConversationSummary> summaries = memoryService.getConversationList(storeId, user.getUser());
        List<AiAnalysisHistoryDTO> result = summaries.stream()
                .map(s -> {
                    AiAnalysisHistoryDTO dto = new AiAnalysisHistoryDTO();
                    dto.setId(s.getId());
                    dto.setName(s.getName());
                    dto.setCreateTime(s.getCreateTime());
                    return dto;
                })
                .collect(Collectors.toList());
        return Result.success(result);
    }

    @Override
    public Result<List<BotQAParam.QAParam>> getAiConversation(Long id) {
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        ConversationContext context = memoryService.getConversation(id);

        if (context == null || context.getOriginalHistory() == null) {
            return Result.success(new ArrayList<>());
        }

        List<BotQAParam.QAParam> result = context.getOriginalHistory().stream()
                .map(qa -> {
                    BotQAParam.QAParam param = new BotQAParam.QAParam();
                    param.setTime(qa.getTime());
                    param.setUser(qa.getUser());
                    param.setBot(qa.getBot());
                    return param;
                })
                .collect(Collectors.toList());
        return Result.success(result);
    }

    @Override
    public Result<Boolean> deleteAiConversation(Long id) {
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        memoryService.deleteConversation(id);
        return Result.success(true);
    }

    @Override
    public Result<Boolean> updateAiName(Long id, String name) {
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        memoryService.updateConversationName(id, name);
        return Result.success(true);
    }

    @Override
    public Result<Boolean> closeAiAnalysis(Long id) {
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        memoryService.closeConversation(id);
        return Result.success(true);
    }

    @Override
    public void cleanExpiredConversations() {
        ChatMemoryService memoryService = ChatMemoryServiceFactory.getChatMemoryService();
        memoryService.cleanExpiredConversations();
    }

    private static long calculateDelayToTargetHour(int targetHour) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetTime = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0);

        if (now.isAfter(targetTime)) {
            targetTime = targetTime.plusDays(1);
        }

        return java.time.temporal.ChronoUnit.MINUTES.between(now, targetTime);
    }

    @Data
    static class AnalysisResult {
        private String answer;
        private List<QAPair> summarizedModelHistory;
    }
}
