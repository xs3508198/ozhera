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

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

import java.util.ArrayList;
import java.util.List;

public class MsgBuilder {

    private final List<Msg> messages = new ArrayList<>();

    public static MsgBuilder create() {
        return new MsgBuilder();
    }

    public static Msg systemMsg(String content) {
        return Msg.builder()
                .role(MsgRole.SYSTEM)
                .textContent(content)
                .build();
    }

    public static Msg userMsg(String content) {
        return Msg.builder()
                .role(MsgRole.USER)
                .textContent(content)
                .build();
    }

    public static Msg assistantMsg(String content) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent(content)
                .build();
    }

    public MsgBuilder system(String content) {
        messages.add(systemMsg(content));
        return this;
    }

    public MsgBuilder user(String content) {
        messages.add(userMsg(content));
        return this;
    }

    public MsgBuilder assistant(String content) {
        messages.add(assistantMsg(content));
        return this;
    }

    public MsgBuilder add(Msg msg) {
        messages.add(msg);
        return this;
    }

    public MsgBuilder addAll(List<Msg> msgs) {
        messages.addAll(msgs);
        return this;
    }

    public List<Msg> build() {
        return new ArrayList<>(messages);
    }
}
