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
package org.apache.ozhera.mind.service.service;

import io.agentscope.core.memory.autocontext.AutoContextMemory;

/**
 * Service for persisting AutoContextMemory state to Redis.
 * This allows memory state (including compression state) to survive agent recreation.
 */
public interface MemoryStateService {

    /**
     * Save memory state to Redis.
     *
     * @param username the user identifier
     * @param memory the AutoContextMemory to save
     */
    void saveState(String username, AutoContextMemory memory);

    /**
     * Load memory state from Redis and restore to the memory instance.
     *
     * @param username the user identifier
     * @param memory the AutoContextMemory to restore state into
     * @return true if state was found and restored, false if no state exists
     */
    boolean loadState(String username, AutoContextMemory memory);

    /**
     * Check if memory state exists for a user.
     *
     * @param username the user identifier
     * @return true if state exists
     */
    boolean hasState(String username);

    /**
     * Delete memory state for a user.
     *
     * @param username the user identifier
     */
    void deleteState(String username);
}
