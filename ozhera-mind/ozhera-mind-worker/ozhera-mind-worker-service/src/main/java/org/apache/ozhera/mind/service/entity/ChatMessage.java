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
package org.apache.ozhera.mind.service.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat message entity for persisting conversation history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("hera_mind_chat_message")
public class ChatMessage {

    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * Username of the conversation owner
     */
    private String username;

    /**
     * Message role: USER or ASSISTANT
     */
    private String role;

    /**
     * Message content
     */
    private String content;

    /**
     * Creation timestamp in milliseconds
     */
    @Column("created_at")
    private Long createdAt;
}
