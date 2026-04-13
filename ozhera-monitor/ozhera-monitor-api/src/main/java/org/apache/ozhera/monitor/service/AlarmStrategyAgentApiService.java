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

package org.apache.ozhera.monitor.service;

import org.apache.ozhera.monitor.service.bo.AlarmStrategyCreateRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyDeleteRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyQueryRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyUpdateRequest;

/**
 * Dubbo Agent API Service for Alarm Strategy CRUD operations
 */
public interface AlarmStrategyAgentApiService {

    /**
     * Create alarm strategy
     *
     * @param request create request
     * @return JSON string result
     */
    String createAlarmStrategy(AlarmStrategyCreateRequest request);

    /**
     * Delete alarm strategy
     *
     * @param request delete request
     * @return JSON string result
     */
    String deleteAlarmStrategy(AlarmStrategyDeleteRequest request);

    /**
     * Update alarm strategy
     *
     * @param request update request
     * @return JSON string result
     */
    String updateAlarmStrategy(AlarmStrategyUpdateRequest request);

    /**
     * Query alarm strategy by id or search by conditions
     *
     * @param request query request
     * @return JSON string result
     */
    String queryAlarmStrategy(AlarmStrategyQueryRequest request);

}
