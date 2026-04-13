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

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swarm session state.
 * Records the current agent for the user.
 */
@Data
public class SwarmSession {

    /**
     * Username.
     */
    private String username;

    /**
     * Current active agent name.
     */
    private String currentAgentName;

    /**
     * Agent switch history.
     */
    private List<String> agentHistory = new ArrayList<>();

    /**
     * Session creation time.
     */
    private long createTime;

    /**
     * Last active time.
     */
    private long lastActiveTime;

    /**
     * Handoff temporary context (persists across requests).
     */
    private Map<String, Object> handoffContext = new HashMap<>();

    /**
     * Update current agent.
     *
     * @param agentName the new agent name
     */
    public void switchAgent(String agentName) {
        this.currentAgentName = agentName;
        this.agentHistory.add(agentName);
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * Create new session.
     *
     * @param username the username
     * @return the new session
     */
    public static SwarmSession create(String username) {
        SwarmSession session = new SwarmSession();
        session.setUsername(username);
        session.setCurrentAgentName("CommonAgent");
        session.setCreateTime(System.currentTimeMillis());
        session.setLastActiveTime(System.currentTimeMillis());
        session.getAgentHistory().add("CommonAgent");
        return session;
    }
}
