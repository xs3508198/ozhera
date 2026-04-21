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
package org.apache.ozhera.mind.service.swarm.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.apache.ozhera.mind.service.swarm.HandoffTool;
import org.apache.ozhera.mind.service.swarm.HeraAgent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * CommonAgent - Default entry point agent.
 * Handles general conversation and guides users to specialized agents.
 */
@Component
public class CommonAgent implements HeraAgent {

    private static final String SYSTEM_PROMPT = """
            You are Hera Mind, an intelligent assistant for the Hera observability platform.

            ## Your Capabilities
            1. Have friendly conversations with users
            2. Introduce Hera platform features
            3. Answer general questions about observability
            4. Guide users to specialized agents when needed

            ## Hera Platform Features
            - **Log Management**: Log spaces, stores, tails, log search and analysis
            - **Monitoring & Alerting**: Alarm strategies, alert rules, notifications

            ## Handoff Rules
            When user has **specific operational needs**, use handoff tools:
            - Log operations (create/config/search logs) → handoff_to_log
            - Monitor operations (create/config alerts) → handoff_to_monitor

            ## Important
            - If user is just **asking questions** (like "what is a log space"), answer directly
            - Only handoff when user wants to **perform operations**
            - Briefly explain before handoff
            """;

    @Resource
    private HandoffTool handoffTool;

    @Override
    public String getName() {
        return "CommonAgent";
    }

    @Override
    public String getDescription() {
        return "General assistant for chat and guiding users";
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public Toolkit getToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(handoffTool);
        return toolkit;
    }

    @Override
    public List<Object> getToolObjects() {
        return List.of(handoffTool);
    }

    @Override
    public List<String> getHandoffTargets() {
        return List.of("LogAgent", "MonitorAgent");
    }

    @Override
    public ReActAgent createReActAgent(Model model, AutoContextMemory memory, Hook hook) {
        return ReActAgent.builder()
                .name(getName())
                .sysPrompt(getSystemPrompt())
                .model(model)
                .toolkit(getToolkit())
                .memory(memory)
                .hook(hook)
                .build();
    }

    @Override
    public boolean isDefaultEntry() {
        return true;
    }
}
