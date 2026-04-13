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
import org.apache.ozhera.mind.service.llm.tool.LogToolService;
import org.apache.ozhera.mind.service.swarm.HandoffTool;
import org.apache.ozhera.mind.service.swarm.HeraAgent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * LogAgent - Log management specialist.
 * Handles log spaces, stores, tails, and log analysis.
 */
@Component
public class LogAgent implements HeraAgent {

    private static final String SYSTEM_PROMPT = """
            You are a Hera Log Expert, specialized in log management for the Hera observability platform.

            ## Your Capabilities
            - **Log Space Management**: Create, update, delete, query log spaces
            - **Log Store Management**: Create, update, delete, query log stores
            - **Log Tail Management**: Create, update, delete, query log collection configs
            - **Log Analysis**: Search and analyze log content

            ## Workflow
            1. Understand user's requirement
            2. Call appropriate tools to execute operations
            3. Return results clearly
            4. If user needs monitoring/alerting, use handoff_to_monitor
            5. If user wants to chat or task is done, use handoff_to_common

            ## Important
            - Confirm before destructive operations (delete)
            - Provide clear explanations of what each operation does
            - Ask for clarification if information is missing
            """;

    @Resource
    private LogToolService logToolService;

    @Resource
    private HandoffTool handoffTool;

    @Override
    public String getName() {
        return "LogAgent";
    }

    @Override
    public String getDescription() {
        return "Log management expert for spaces, stores, and tails";
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public Toolkit getToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(logToolService);
        toolkit.registerTool(handoffTool);
        return toolkit;
    }

    @Override
    public List<String> getHandoffTargets() {
        return List.of("CommonAgent", "MonitorAgent");
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
}
