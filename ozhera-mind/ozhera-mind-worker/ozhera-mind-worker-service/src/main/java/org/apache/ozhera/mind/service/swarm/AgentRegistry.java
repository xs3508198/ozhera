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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Registry.
 * Manages all agent definitions.
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, HeraAgent> agents = new ConcurrentHashMap<>();

    private HeraAgent defaultAgent;

    @Resource
    private List<HeraAgent> heraAgents;

    @PostConstruct
    public void init() {
        for (HeraAgent agent : heraAgents) {
            register(agent);
            if (agent.isDefaultEntry()) {
                defaultAgent = agent;
            }
        }

        if (defaultAgent == null && !agents.isEmpty()) {
            defaultAgent = agents.get("CommonAgent");
        }

        log.info("Registered {} agents: {}, default: {}",
                agents.size(), agents.keySet(),
                defaultAgent != null ? defaultAgent.getName() : "none");
    }

    /**
     * Register an agent.
     *
     * @param agent the agent to register
     */
    public void register(HeraAgent agent) {
        agents.put(agent.getName(), agent);
        log.info("Registered agent: {} - {}", agent.getName(), agent.getDescription());
    }

    /**
     * Get agent by name.
     *
     * @param name the agent name
     * @return the agent
     * @throws IllegalArgumentException if agent not found
     */
    public HeraAgent getAgent(String name) {
        HeraAgent agent = agents.get(name);
        if (agent == null) {
            log.warn("Unknown agent: {}, returning default agent", name);
            return defaultAgent;
        }
        return agent;
    }

    /**
     * Get the default entry agent.
     *
     * @return the default agent
     */
    public HeraAgent getDefaultAgent() {
        return defaultAgent;
    }

    /**
     * Get all agents.
     *
     * @return collection of all agents
     */
    public Collection<HeraAgent> getAllAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }

    /**
     * Check if agent exists.
     *
     * @param name the agent name
     * @return true if exists
     */
    public boolean hasAgent(String name) {
        return agents.containsKey(name);
    }
}
