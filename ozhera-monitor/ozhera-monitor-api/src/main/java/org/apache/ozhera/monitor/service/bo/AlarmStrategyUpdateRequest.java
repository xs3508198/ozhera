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
import java.util.List;

/**
 * Update alarm strategy request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlarmStrategyUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User information
     */
    private UserInfo userInfo;

    /**
     * Strategy ID
     */
    private Integer strategyId;

    /**
     * Strategy name
     */
    private String strategyName;

    /**
     * Strategy description
     */
    private String strategyDesc;

    /**
     * Alert team
     */
    private String alertTeam;

    /**
     * Included environment list
     */
    private List<String> includeEnvs;

    /**
     * Excluded environment list
     */
    private List<String> exceptEnvs;

    /**
     * Included zone list
     */
    private List<String> includeZones;

    /**
     * Excluded zone list
     */
    private List<String> exceptZones;

    /**
     * Included module list
     */
    private List<String> includeModules;

    /**
     * Excluded module list
     */
    private List<String> exceptModules;

    /**
     * Included function list
     */
    private List<String> includeFunctions;

    /**
     * Excluded function list
     */
    private List<String> exceptFunctions;

    /**
     * Included container name list
     */
    private List<String> includeContainerName;

    /**
     * Excluded container name list
     */
    private List<String> exceptContainerName;

    /**
     * Alert members list
     */
    private List<String> alertMembers;

    /**
     * At members list
     */
    private List<String> atMembers;

    /**
     * Validate required parameters
     *
     * @return error message if validation fails, null if success
     */
    public String validate() {
        if (userInfo == null || userInfo.getUser() == null || userInfo.getUser().isEmpty()) {
            return "User information is required";
        }
        if (strategyId == null) {
            return "Strategy ID is required";
        }
        return null;
    }
}
