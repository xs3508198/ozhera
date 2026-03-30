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
package org.apache.ozhera.mind.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AgentCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User ID
     */
    private String userId;

    /**
     * User's full account name
     */
    private String username;

    /**
     * Agent name (optional, for display)
     */
    private String agentName;

    /**
     * Tools to enable for this agent
     */
    private List<String> enabledTools;

    /**
     * System prompt for the agent
     */
    private String systemPrompt;
}
