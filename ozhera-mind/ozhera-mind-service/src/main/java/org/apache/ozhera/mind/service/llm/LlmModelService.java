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
package org.apache.ozhera.mind.service.llm;

import io.agentscope.core.model.Model;

/**
 * LLM Model Service Interface.
 * This is the extension point for internal network versions to customize model creation.
 *
 * Default implementation: DefaultLlmModelService (uses UserConfigService + ChatModelFactory)
 *
 * To override in internal network version:
 * <pre>
 * {@code
 * @Primary
 * @Service
 * public class InternalLlmModelService implements LlmModelService {
 *     // Your implementation using internal platform
 * }
 * }
 * </pre>
 */
public interface LlmModelService {

    /**
     * Get the model for a specific user.
     * Each user can have their own API key, platform, and model configuration.
     *
     * @param username the username
     * @return the Model instance for this user
     * @throws RuntimeException if user config not found or model creation fails
     */
    Model getModel(String username);

    /**
     * Invalidate cached model for a user (if caching is implemented).
     * Should be called when user updates their config.
     *
     * @param username the username
     */
    default void invalidateCache(String username) {
        // Default no-op, implementations can override
    }

    /**
     * Check if user has configured their model settings.
     *
     * @param username the username
     * @return true if user has a valid configuration
     */
    boolean hasUserConfig(String username);
}
