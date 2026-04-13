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

package org.apache.ozhera.monitor.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.apache.ozhera.monitor.bo.AlarmStrategyInfo;
import org.apache.ozhera.monitor.bo.AlarmStrategyParam;
import org.apache.ozhera.monitor.dao.AppMonitorDao;
import org.apache.ozhera.monitor.dao.model.AlarmStrategy;
import org.apache.ozhera.monitor.dao.model.AppMonitor;
import org.apache.ozhera.monitor.result.ErrorCode;
import org.apache.ozhera.monitor.result.Result;
import org.apache.ozhera.monitor.service.AlarmStrategyAgentApiService;
import org.apache.ozhera.monitor.service.AlarmStrategyService;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyCreateRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyDeleteRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyQueryRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyUpdateRequest;
import org.apache.ozhera.monitor.service.bo.ApiResult;
import org.apache.ozhera.monitor.service.bo.UserInfo;
import org.apache.ozhera.monitor.service.model.PageData;
import org.apache.ozhera.monitor.service.model.prometheus.AlarmRuleRequest;
import org.apache.ozhera.monitor.service.user.LocalUser;
import org.apache.ozhera.monitor.service.user.UseDetailInfo;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * Dubbo Agent API Service Implementation for Alarm Strategy CRUD operations
 */
@Slf4j
@Service(registry = "registryConfig", interfaceClass = AlarmStrategyAgentApiService.class, retries = 0, group = "${dubbo.group}")
public class AlarmStrategyAgentApiServiceImpl implements AlarmStrategyAgentApiService {

    @Resource
    private AlarmStrategyService alarmStrategyService;

    @Resource
    private AppMonitorDao appMonitorDao;

    @Override
    public String createAlarmStrategy(AlarmStrategyCreateRequest request) {
        log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.createAlarmStrategy request: {}", request);
        try {
            // Validate request
            String validationError = request.validate();
            if (validationError != null) {
                return ApiResult.fail(validationError);
            }

            // Set user context
            setLocalUser(request.getUserInfo());

            // Get app monitor
            AppMonitor app = appMonitorDao.getByIamTreeIdAndAppId(request.getIamId(), request.getProjectId());
            if (app == null) {
                return ApiResult.fail("App not found for iamId: " + request.getIamId() + ", projectId: " + request.getProjectId());
            }

            // Build AlarmRuleRequest
            AlarmRuleRequest alarmRuleRequest = buildAlarmRuleRequest(request);

            // Create strategy
            AlarmStrategy strategy = alarmStrategyService.create(alarmRuleRequest, app);
            if (strategy == null) {
                return ApiResult.fail("Failed to create alarm strategy");
            }

            log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.createAlarmStrategy success, strategyId: {}", strategy.getId());
            return ApiResult.successBuilder()
                    .addData("strategyId", strategy.getId())
                    .build();
        } catch (Exception e) {
            log.error("Dubbo.AlarmStrategyAgentApiServiceImpl.createAlarmStrategy error: {}", e.getMessage(), e);
            return ApiResult.fail("Failed to create alarm strategy: " + e.getMessage());
        } finally {
            LocalUser.clear();
        }
    }

    @Override
    public String deleteAlarmStrategy(AlarmStrategyDeleteRequest request) {
        log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.deleteAlarmStrategy request: {}", request);
        try {
            // Validate request
            String validationError = request.validate();
            if (validationError != null) {
                return ApiResult.fail(validationError);
            }

            // Set user context
            setLocalUser(request.getUserInfo());

            String user = request.getUserInfo().getUser();

            // Single delete
            if (request.getStrategyId() != null) {
                Result result = alarmStrategyService.deleteByStrategyId(user, request.getStrategyId());
                if (result.getCode() != ErrorCode.success.getCode()) {
                    return ApiResult.fail("Failed to delete alarm strategy: " + result.getMessage());
                }
                log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.deleteAlarmStrategy success, strategyId: {}", request.getStrategyId());
                return ApiResult.success();
            }

            // Batch delete
            if (!CollectionUtils.isEmpty(request.getStrategyIds())) {
                Result result = alarmStrategyService.batchDeleteStrategy(user, request.getStrategyIds());
                if (result.getCode() != ErrorCode.success.getCode()) {
                    return ApiResult.fail("Failed to batch delete alarm strategies: " + result.getMessage());
                }
                log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.deleteAlarmStrategy success, strategyIds: {}", request.getStrategyIds());
                return ApiResult.success();
            }

            return ApiResult.fail("Strategy ID or strategy ID list is required");
        } catch (Exception e) {
            log.error("Dubbo.AlarmStrategyAgentApiServiceImpl.deleteAlarmStrategy error: {}", e.getMessage(), e);
            return ApiResult.fail("Failed to delete alarm strategy: " + e.getMessage());
        } finally {
            LocalUser.clear();
        }
    }

    @Override
    public String updateAlarmStrategy(AlarmStrategyUpdateRequest request) {
        log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.updateAlarmStrategy request: {}", request);
        try {
            // Validate request
            String validationError = request.validate();
            if (validationError != null) {
                return ApiResult.fail(validationError);
            }

            // Set user context
            setLocalUser(request.getUserInfo());

            // Build AlarmRuleRequest
            AlarmRuleRequest alarmRuleRequest = new AlarmRuleRequest();
            alarmRuleRequest.setStrategyId(request.getStrategyId());
            alarmRuleRequest.setStrategyName(request.getStrategyName());
            alarmRuleRequest.setStrategyDesc(request.getStrategyDesc());
            alarmRuleRequest.setAlertTeam(request.getAlertTeam());
            alarmRuleRequest.setIncludeEnvs(request.getIncludeEnvs());
            alarmRuleRequest.setExceptEnvs(request.getExceptEnvs());
            alarmRuleRequest.setIncludeZones(request.getIncludeZones());
            alarmRuleRequest.setExceptZones(request.getExceptZones());
            alarmRuleRequest.setIncludeModules(request.getIncludeModules());
            alarmRuleRequest.setExceptModules(request.getExceptModules());
            alarmRuleRequest.setIncludeFunctions(request.getIncludeFunctions());
            alarmRuleRequest.setExceptFunctions(request.getExceptFunctions());
            alarmRuleRequest.setIncludeContainerName(request.getIncludeContainerName());
            alarmRuleRequest.setExceptContainerName(request.getExceptContainerName());
            alarmRuleRequest.setAlertMembers(request.getAlertMembers());
            alarmRuleRequest.setAtMembers(request.getAtMembers());
            alarmRuleRequest.setUser(request.getUserInfo().getUser());

            // Update strategy
            Result<AlarmStrategy> result = alarmStrategyService.updateByParam(alarmRuleRequest);
            if (result.getCode() != ErrorCode.success.getCode()) {
                return ApiResult.fail("Failed to update alarm strategy: " + result.getMessage());
            }

            log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.updateAlarmStrategy success, strategyId: {}", request.getStrategyId());
            return ApiResult.success();
        } catch (Exception e) {
            log.error("Dubbo.AlarmStrategyAgentApiServiceImpl.updateAlarmStrategy error: {}", e.getMessage(), e);
            return ApiResult.fail("Failed to update alarm strategy: " + e.getMessage());
        } finally {
            LocalUser.clear();
        }
    }

    @Override
    public String queryAlarmStrategy(AlarmStrategyQueryRequest request) {
        log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.queryAlarmStrategy request: {}", request);
        try {
            // Validate request
            String validationError = request.validate();
            if (validationError != null) {
                return ApiResult.fail(validationError);
            }

            // Set user context
            setLocalUser(request.getUserInfo());

            String user = request.getUserInfo().getUser();

            // Query by ID (detailed)
            if (request.getStrategyId() != null) {
                AlarmStrategyParam param = new AlarmStrategyParam();
                param.setId(request.getStrategyId());
                Result<AlarmStrategyInfo> result = alarmStrategyService.detailed(user, param);
                if (result.getCode() != ErrorCode.success.getCode()) {
                    return ApiResult.fail("Failed to query alarm strategy: " + result.getMessage());
                }
                log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.queryAlarmStrategy success, strategyId: {}", request.getStrategyId());
                return ApiResult.successBuilder()
                        .addData("strategy", result.getData())
                        .build();
            }

            // Search by conditions
            request.pageQryInit();
            AlarmStrategyParam param = new AlarmStrategyParam();
            param.setAppId(request.getAppId());
            param.setAppName(request.getAppName());
            param.setStrategyType(request.getStrategyType());
            param.setStrategyName(request.getStrategyName());
            param.setStatus(request.getStatus());
            param.setPage(request.getPage());
            param.setPageSize(request.getPageSize());
            param.setSortBy(request.getSortBy());
            param.setSortOrder(request.getSortOrder());
            param.setOwner(request.isOwner());

            Result<PageData<List<AlarmStrategyInfo>>> result = alarmStrategyService.search(user, param);
            if (result.getCode() != ErrorCode.success.getCode()) {
                return ApiResult.fail("Failed to search alarm strategies: " + result.getMessage());
            }

            log.info("Dubbo.AlarmStrategyAgentApiServiceImpl.queryAlarmStrategy search success");
            return ApiResult.successBuilder()
                    .addData("page", result.getData().getPage())
                    .addData("pageSize", result.getData().getPageSize())
                    .addData("total", result.getData().getTotal())
                    .addData("list", result.getData().getList())
                    .build();
        } catch (Exception e) {
            log.error("Dubbo.AlarmStrategyAgentApiServiceImpl.queryAlarmStrategy error: {}", e.getMessage(), e);
            return ApiResult.fail("Failed to query alarm strategy: " + e.getMessage());
        } finally {
            LocalUser.clear();
        }
    }

    /**
     * Set local user context from UserInfo
     *
     * @param userInfo user information from request
     */
    private void setLocalUser(UserInfo userInfo) {
        UseDetailInfo useDetailInfo = new UseDetailInfo();
        useDetailInfo.setUserName(userInfo.getUser());
        useDetailInfo.setName(userInfo.getUser());
        useDetailInfo.setDisplayName(userInfo.getUser());
        LocalUser.set(useDetailInfo);
    }

    private AlarmRuleRequest buildAlarmRuleRequest(AlarmStrategyCreateRequest request) {
        AlarmRuleRequest alarmRuleRequest = new AlarmRuleRequest();
        alarmRuleRequest.setIamId(request.getIamId());
        alarmRuleRequest.setProjectId(request.getProjectId());
        alarmRuleRequest.setProjectName(request.getProjectName());
        alarmRuleRequest.setStrategyType(request.getStrategyType());
        alarmRuleRequest.setStrategyName(request.getStrategyName());
        alarmRuleRequest.setStrategyDesc(request.getStrategyDesc());
        alarmRuleRequest.setAlertTeam(request.getAlertTeam());
        alarmRuleRequest.setAppAlias(request.getAppAlias());
        alarmRuleRequest.setIncludeEnvs(request.getIncludeEnvs());
        alarmRuleRequest.setExceptEnvs(request.getExceptEnvs());
        alarmRuleRequest.setIncludeZones(request.getIncludeZones());
        alarmRuleRequest.setExceptZones(request.getExceptZones());
        alarmRuleRequest.setIncludeModules(request.getIncludeModules());
        alarmRuleRequest.setExceptModules(request.getExceptModules());
        alarmRuleRequest.setIncludeFunctions(request.getIncludeFunctions());
        alarmRuleRequest.setExceptFunctions(request.getExceptFunctions());
        alarmRuleRequest.setIncludeContainerName(request.getIncludeContainerName());
        alarmRuleRequest.setExceptContainerName(request.getExceptContainerName());
        alarmRuleRequest.setAlertMembers(request.getAlertMembers());
        alarmRuleRequest.setAtMembers(request.getAtMembers());
        alarmRuleRequest.setUser(request.getUserInfo().getUser());
        return alarmRuleRequest;
    }
}
