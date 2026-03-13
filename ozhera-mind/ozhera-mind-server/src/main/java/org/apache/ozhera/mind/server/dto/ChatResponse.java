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
package org.apache.ozhera.mind.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private Integer code;
    private String message;
    private ChatData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatData {
        private String content;
        private String model;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }

    public static ChatResponse success(ChatData data) {
        return ChatResponse.builder()
                .code(0)
                .message("success")
                .data(data)
                .build();
    }

    public static ChatResponse error(String message) {
        return ChatResponse.builder()
                .code(-1)
                .message(message)
                .build();
    }
}
