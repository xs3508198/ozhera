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
import org.apache.ozhera.mind.service.llm.tool.MonitorToolService;
import org.apache.ozhera.mind.service.swarm.HandoffTool;
import org.apache.ozhera.mind.service.swarm.HeraAgent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * MonitorAgent - Monitoring and alerting specialist.
 * Handles alarm strategies, alert rules, and notifications.
 */
@Component
public class MonitorAgent implements HeraAgent {

    private static final String SYSTEM_PROMPT = """
            You are a Hera Monitor Expert, specialized in monitoring and alerting for the Hera observability platform.

            ## Your Capabilities
            - **Alarm Strategy Management**: Create, update, delete, query alarm strategies
            - **Alert Configuration**: Configure alert teams, notification members, @mentions
            - **Filter Configuration**: Set environment, zone, module, function filters

            ## Strategy Types
            - Type 1 (Application): Application-level monitoring
            - Type 2 (System): System-level monitoring
            - Type 3 (Custom): Custom monitoring configurations

            ## Status Values
            - Status 0: Enabled (active)
            - Status 1: Disabled (inactive)

            ## Workflow
            1. Understand user's requirement
            2. Call appropriate tools to execute operations
            3. Return results clearly
            4. If user needs log management, use handoff_to_log
            5. If user wants to chat or task is done, use handoff_to_common

            ## Important
            - Confirm before delete operations
            - Explain configuration impacts before applying
            - For batch operations, list all affected items first
            """;

    @Resource
    private MonitorToolService monitorToolService;

    @Resource
    private HandoffTool handoffTool;

    @Override
    public String getName() {
        return "MonitorAgent";
    }

    @Override
    public String getDescription() {
        return "Monitoring expert for alarm strategies and alerts";
    }

    @Override
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public Toolkit getToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(monitorToolService);
        toolkit.registerTool(handoffTool);
        return toolkit;
    }

    @Override
    public List<Object> getToolObjects() {
        return List.of(monitorToolService, handoffTool);
    }

    @Override
    public List<String> getHandoffTargets() {
        return List.of("CommonAgent", "LogAgent");
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
