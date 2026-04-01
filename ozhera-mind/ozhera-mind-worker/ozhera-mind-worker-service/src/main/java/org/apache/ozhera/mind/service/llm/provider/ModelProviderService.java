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
package org.apache.ozhera.mind.service.llm.provider;

import io.agentscope.core.model.Model;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;

/**
 * Model Provider Service Interface.
 *
 * Implementations create Model instances based on user configuration.
 * The platform is selected via llm.provider property:
 * - dashscope: Use DashScopeModelProvider
 * - openai: Use OpenAIModelProvider
 * - Internal network versions can implement their own provider with @Primary
 */
public interface ModelProviderService {

    /**
     * Create a Model instance based on user configuration.
     *
     * @param userConfig User's configuration containing apiKey, modelType, etc.
     * @return Model instance for Agent to use
     */
    Model createModel(UserConfig userConfig);
}
