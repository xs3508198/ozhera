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

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Model Provider Service Interface.
 * All LLM providers should implement this interface.
 * Internal network versions can implement their own providers.
 */
public interface ModelProviderService {

    /**
     * Get the provider name
     */
    String getProviderName();

    /**
     * Non-streaming chat
     */
    ChatResponse chat(List<Msg> messages);

    /**
     * Streaming chat
     */
    Flux<ChatResponse> chatStream(List<Msg> messages);
}
