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

package org.apache.ozhera.monitor.service.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Query alarm strategy request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlarmStrategyQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User information
     */
    private UserInfo userInfo;

    /**
     * Strategy ID (for detailed query)
     */
    private Integer strategyId;

    /**
     * App ID (for search)
     */
    private Integer appId;

    /**
     * App name (for search)
     */
    private String appName;

    /**
     * Strategy type (for search)
     */
    private Integer strategyType;

    /**
     * Strategy name (for search)
     */
    private String strategyName;

    /**
     * Status: 0-enabled, 1-disabled (for search)
     */
    private Integer status;

    /**
     * Page number
     */
    private int page = 1;

    /**
     * Page size
     */
    private int pageSize = 10;

    /**
     * Sort by field
     */
    private String sortBy = "update_time";

    /**
     * Sort order: asc or desc
     */
    private String sortOrder = "desc";

    /**
     * Whether to filter by owner
     */
    private boolean owner;

    /**
     * Validate required parameters
     *
     * @return error message if validation fails, null if success
     */
    public String validate() {
        if (userInfo == null || userInfo.getUser() == null || userInfo.getUser().isEmpty()) {
            return "User information is required";
        }
        return null;
    }

    /**
     * Initialize page query parameters
     */
    public void pageQryInit() {
        if (page <= 0) {
            page = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
    }
}
