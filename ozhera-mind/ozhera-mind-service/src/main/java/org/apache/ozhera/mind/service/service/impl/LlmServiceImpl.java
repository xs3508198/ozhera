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
package org.apache.ozhera.mind.service.service.impl;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.service.LlmService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class LlmServiceImpl implements LlmService {

    @Resource
    private ModelProviderService modelProviderService;

    @Override
    public ChatResponse chat(String username, List<Msg> messages) {
        log.debug("Chat with provider: {}, username: {}", modelProviderService.getProviderName(), username);
        return modelProviderService.chat(messages);
    }

    @Override
    public Flux<ChatResponse> chatStream(String username, List<Msg> messages) {
        log.debug("Chat stream with provider: {}, username: {}", modelProviderService.getProviderName(), username);
        return modelProviderService.chatStream(messages);
    }
}
