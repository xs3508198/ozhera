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

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Universal Handoff Tool for Multi-Agent collaboration.
 * All agents use this tool to transfer to other agents.
 */
@Component
public class HandoffTool {

    public static final String HANDOFF_PREFIX = "__HANDOFF__:";

    @Tool(name = "handoff_to_log",
            description = """
                    Transfer to LogAgent for log-related operations.
                    Use when user wants to:
                    - Create, update, delete log spaces
                    - Configure log stores
                    - Set up log collection (tails)
                    - Search or analyze logs

                    Do NOT handoff if user is just asking questions about logs.
                    Only handoff when user wants to perform actual operations.
                    """)
    public String handoffToLog(
            @ToolParam(name = "reason", description = "Why transferring to LogAgent", required = true)
            String reason,
            @ToolParam(name = "task", description = "What task LogAgent should do", required = true)
            String task) {
        return formatHandoff("LogAgent", reason, task);
    }

    @Tool(name = "handoff_to_monitor",
            description = """
                    Transfer to MonitorAgent for monitoring and alerting operations.
                    Use when user wants to:
                    - Create, update, delete alarm strategies
                    - Configure alert notifications
                    - Query monitoring metrics
                    - Manage alert rules

                    Do NOT handoff if user is just asking questions about monitoring.
                    Only handoff when user wants to perform actual operations.
                    """)
    public String handoffToMonitor(
            @ToolParam(name = "reason", description = "Why transferring to MonitorAgent", required = true)
            String reason,
            @ToolParam(name = "task", description = "What task MonitorAgent should do", required = true)
            String task) {
        return formatHandoff("MonitorAgent", reason, task);
    }

    @Tool(name = "handoff_to_common",
            description = """
                    Transfer back to CommonAgent for general conversation.
                    Use when:
                    - User wants to chat or asks unrelated questions
                    - User says "never mind" or "forget it"
                    - Current task is completed and user has no follow-up
                    """)
    public String handoffToCommon(
            @ToolParam(name = "reason", description = "Why transferring back to CommonAgent", required = true)
            String reason) {
        return formatHandoff("CommonAgent", reason, "");
    }

    /**
     * Format handoff result.
     * SwarmExecutor will parse this format to detect handoff.
     */
    private String formatHandoff(String targetAgent, String reason, String task) {
        return String.format("%s{\"target\":\"%s\",\"reason\":\"%s\",\"task\":\"%s\"}",
                HANDOFF_PREFIX, targetAgent, escapeJson(reason), escapeJson(task));
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Check if content contains handoff signal.
     */
    public static boolean isHandoff(String content) {
        return content != null && content.contains(HANDOFF_PREFIX);
    }

    /**
     * Parse target agent from handoff content.
     */
    public static String parseTargetAgent(String content) {
        if (!isHandoff(content)) return null;
        try {
            int start = content.indexOf("\"target\":\"") + 10;
            int end = content.indexOf("\"", start);
            return content.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
