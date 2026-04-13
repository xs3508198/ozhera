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
package org.apache.ozhera.mind.service.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thread-local context for storing current user information.
 * Used to pass user info to Tool methods without explicit parameters.
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> CURRENT_USER = new ThreadLocal<>();

    /**
     * Set current user info for this thread.
     */
    public static void set(UserInfo user) {
        CURRENT_USER.set(user);
    }

    /**
     * Get current user info.
     * @return UserInfo or null if not set
     */
    public static UserInfo get() {
        return CURRENT_USER.get();
    }

    /**
     * Clear current user info. Must be called after request processing.
     */
    public static void clear() {
        CURRENT_USER.remove();
    }

    /**
     * User information holder.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String username;
        private Integer userType;
    }
}
