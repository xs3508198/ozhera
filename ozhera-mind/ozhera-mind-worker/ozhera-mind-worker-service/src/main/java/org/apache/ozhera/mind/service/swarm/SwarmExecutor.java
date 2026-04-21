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
package org.apache.ozhera.mind.service.swarm;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.ozhera.log.api.service.LogAgentApiService;
import org.apache.ozhera.mind.service.concurrency.LLMRateLimiter;
import org.apache.ozhera.mind.service.confirmation.ConfirmationManager;
import org.apache.ozhera.mind.service.context.UserContext;
import org.apache.ozhera.mind.service.hook.StreamingHook;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.llm.tool.DeleteLogSpaceTool;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Swarm Executor.
 * Executes agents and handles handoffs between them.
 */
@Slf4j
@Service
public class SwarmExecutor {

    private static final int MAX_HANDOFF_DEPTH = 3;

    @Resource
    private AgentRegistry agentRegistry;

    @Resource
    private ModelProviderService modelProviderService;

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private SwarmSessionService sessionService;

    @Resource
    private LLMRateLimiter llmRateLimiter;

    @Resource
    private ConfirmationManager confirmationManager;

    @DubboReference(interfaceClass = LogAgentApiService.class, group = "${log.agent.group}", check = false)
    private LogAgentApiService logAgentApiService;

    /**
     * Execute agent with streaming response.
     */
    public Flux<String> execute(
            String username,
            String userMessage,
            SwarmSession session,
            AutoContextMemory memory) {

        return Flux.create(sink -> {
            try {
                List<Object> requestScopedTools = createRequestScopedTools(sink);
                executeInternal(username, userMessage, session, memory, sink, requestScopedTools, new HashSet<>(), 0);
            } catch (Exception e) {
                log.error("Execution failed for user: {}", username, e);
                sink.error(e);
            }
        });
    }

    /**
     * Create request-scoped tools with FluxSink injected.
     */
    private List<Object> createRequestScopedTools(FluxSink<String> sink) {
        List<Object> tools = new ArrayList<>();
        tools.add(new DeleteLogSpaceTool(sink, confirmationManager, logAgentApiService));
        return tools;
    }

    /**
     * Internal execution with handoff support.
     */
    private void executeInternal(
            String username,
            String userMessage,
            SwarmSession session,
            AutoContextMemory memory,
            reactor.core.publisher.FluxSink<String> sink,
            List<Object> requestScopedTools,
            Set<String> visitedAgents,
            int depth) {

        // Check handoff depth
        if (depth >= MAX_HANDOFF_DEPTH) {
            log.warn("Max handoff depth reached for user: {}", username);
            sink.next("\n[Max handoff depth reached, stopping here]\n");
            sink.complete();
            return;
        }

        // Get current agent
        String currentAgentName = session.getCurrentAgentName();

        // Check for cycles
        if (visitedAgents.contains(currentAgentName)) {
            log.warn("Agent cycle detected: {}", currentAgentName);
            sink.complete();
            return;
        }
        visitedAgents.add(currentAgentName);

        HeraAgent agentDef = agentRegistry.getAgent(currentAgentName);
        log.info("Executing {} for user: {}, message: {}", currentAgentName, username,
                userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);

        // Create model
        UserConfig userConfig = userConfigService.getByUsername(username);
        Model model = modelProviderService.createModel(userConfig);

        // Rate limit LLM API calls
        String provider = userConfig.getModelPlatform();
        llmRateLimiter.acquire(provider);
        log.debug("LLM rate limit acquired for provider: {}", provider);

        // Create streaming hook
        StringBuilder fullResponse = new StringBuilder();
        Sinks.Many<String> agentSink = Sinks.many().unicast().onBackpressureBuffer();
        StreamingHook hook = new StreamingHook(agentSink, fullResponse);

        // Create agent with request-scoped tools
        ReActAgent agent = agentDef.createReActAgentWithTools(model, memory, hook, requestScopedTools);

        // Build user message
        Msg userMsg = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(userMessage).build())
                .build();

        // Set user context
        UserContext.UserInfo userInfo = new UserContext.UserInfo(username, 0);

        // Subscribe to agent output
        agentSink.asFlux().subscribe(
                chunk -> sink.next(chunk),
                error -> {
                    log.error("Agent error", error);
                    sink.error(error);
                },
                () -> {
                    // Agent completed, check for handoff
                    String response = fullResponse.toString();

                    if (HandoffTool.isHandoff(response)) {
                        String targetAgent = HandoffTool.parseTargetAgent(response);
                        if (targetAgent != null && agentRegistry.hasAgent(targetAgent)) {
                            // Notify user about handoff
                            sink.next(String.format("\n\n---\n*Transferring to %s...*\n---\n\n", targetAgent));

                            // Update session
                            session.switchAgent(targetAgent);
                            sessionService.saveSession(username, session);

                            // Continue with target agent
                            executeInternal(username, userMessage, session, memory, sink, requestScopedTools, visitedAgents, depth + 1);
                        } else {
                            sink.complete();
                        }
                    } else {
                        sink.complete();
                    }
                }
        );

        // Execute agent
        agent.call(List.of(userMsg))
                .doOnSubscribe(s -> UserContext.set(userInfo))
                .doOnSuccess(msg -> hook.complete())
                .doOnError(e -> {
                    log.error("Agent {} execution failed", currentAgentName, e);
                    hook.error(e);
                })
                .doFinally(s -> UserContext.clear())
                .subscribe();
    }
}
