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
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * Hera Agent Interface.
 * All specialized agents must implement this interface.
 */
public interface HeraAgent {

    /**
     * Get agent name (unique identifier).
     *
     * @return the agent name
     */
    String getName();

    /**
     * Get agent description.
     *
     * @return the description
     */
    String getDescription();

    /**
     * Get system prompt.
     *
     * @return the system prompt
     */
    String getSystemPrompt();

    /**
     * Get the toolkit for this agent (business tools + handoff tools).
     *
     * @return the configured toolkit
     */
    Toolkit getToolkit();

    /**
     * Get list of agents this agent can handoff to.
     *
     * @return list of target agent names
     */
    List<String> getHandoffTargets();

    /**
     * Create AgentScope ReActAgent instance.
     *
     * @param model  the LLM model
     * @param memory the shared memory
     * @param hook   the streaming hook (can be null)
     * @return the created agent
     */
    ReActAgent createReActAgent(Model model, AutoContextMemory memory, Hook hook);

    /**
     * Whether this agent is the default entry point.
     *
     * @return true if this is the default agent
     */
    default boolean isDefaultEntry() {
        return false;
    }
}
