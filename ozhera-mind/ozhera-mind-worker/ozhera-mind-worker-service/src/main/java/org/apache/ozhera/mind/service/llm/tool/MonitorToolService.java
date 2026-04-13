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
package org.apache.ozhera.mind.service.llm.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.ozhera.mind.service.context.UserContext;
import org.apache.ozhera.monitor.service.AlarmStrategyAgentApiService;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyCreateRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyDeleteRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyQueryRequest;
import org.apache.ozhera.monitor.service.bo.AlarmStrategyUpdateRequest;
import org.apache.ozhera.monitor.service.bo.UserInfo;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Monitor Tool Service for Agent
 * Provides alarm strategy CRUD operations through Dubbo
 */
@Slf4j
@Service
public class MonitorToolService {

    @DubboReference(interfaceClass = AlarmStrategyAgentApiService.class, group = "${monitor.agent.group}", check = false)
    private AlarmStrategyAgentApiService alarmStrategyAgentApiService;

    // ==================== Alarm Strategy Tools ====================

    @Tool(name = "createAlarmStrategy",
          description = "Create a new alarm strategy for monitoring. An alarm strategy defines alert rules, notification settings, and filtering conditions for an application.")
    public String createAlarmStrategy(
            @ToolParam(name = "iamId", description = "The IAM ID of the application", required = true)
            Integer iamId,
            @ToolParam(name = "projectId", description = "The project ID of the application", required = true)
            Integer projectId,
            @ToolParam(name = "projectName", description = "The project name", required = false)
            String projectName,
            @ToolParam(name = "strategyType", description = "The strategy type: 1-application, 2-system, 3-custom", required = true)
            Integer strategyType,
            @ToolParam(name = "strategyName", description = "The name of the alarm strategy", required = true)
            String strategyName,
            @ToolParam(name = "strategyDesc", description = "The description of the alarm strategy", required = false)
            String strategyDesc,
            @ToolParam(name = "alertTeam", description = "The alert team name for notifications", required = false)
            String alertTeam,
            @ToolParam(name = "alertMembers", description = "Comma-separated list of alert member usernames", required = false)
            String alertMembers,
            @ToolParam(name = "atMembers", description = "Comma-separated list of members to @mention", required = false)
            String atMembers,
            @ToolParam(name = "includeEnvs", description = "Comma-separated list of environments to include", required = false)
            String includeEnvs,
            @ToolParam(name = "exceptEnvs", description = "Comma-separated list of environments to exclude", required = false)
            String exceptEnvs,
            @ToolParam(name = "includeZones", description = "Comma-separated list of zones to include", required = false)
            String includeZones,
            @ToolParam(name = "exceptZones", description = "Comma-separated list of zones to exclude", required = false)
            String exceptZones) {

        log.info("Creating alarm strategy: {} for project: {}", strategyName, projectId);

        AlarmStrategyCreateRequest request = new AlarmStrategyCreateRequest();
        request.setUserInfo(getCurrentUserInfo());
        request.setIamId(iamId);
        request.setProjectId(projectId);
        request.setProjectName(projectName);
        request.setStrategyType(strategyType);
        request.setStrategyName(strategyName);
        request.setStrategyDesc(strategyDesc);
        request.setAlertTeam(alertTeam);

        if (alertMembers != null && !alertMembers.isEmpty()) {
            request.setAlertMembers(parseCommaSeparatedList(alertMembers));
        }
        if (atMembers != null && !atMembers.isEmpty()) {
            request.setAtMembers(parseCommaSeparatedList(atMembers));
        }
        if (includeEnvs != null && !includeEnvs.isEmpty()) {
            request.setIncludeEnvs(parseCommaSeparatedList(includeEnvs));
        }
        if (exceptEnvs != null && !exceptEnvs.isEmpty()) {
            request.setExceptEnvs(parseCommaSeparatedList(exceptEnvs));
        }
        if (includeZones != null && !includeZones.isEmpty()) {
            request.setIncludeZones(parseCommaSeparatedList(includeZones));
        }
        if (exceptZones != null && !exceptZones.isEmpty()) {
            request.setExceptZones(parseCommaSeparatedList(exceptZones));
        }

        return alarmStrategyAgentApiService.createAlarmStrategy(request);
    }

    @Tool(name = "deleteAlarmStrategy",
          description = "Delete one or more alarm strategies by their IDs.")
    public String deleteAlarmStrategy(
            @ToolParam(name = "strategyId", description = "The ID of the strategy to delete (for single delete)", required = false)
            Integer strategyId,
            @ToolParam(name = "strategyIds", description = "Comma-separated list of strategy IDs to delete (for batch delete)", required = false)
            String strategyIds) {

        log.info("Deleting alarm strategy: strategyId={}, strategyIds={}", strategyId, strategyIds);

        AlarmStrategyDeleteRequest request = new AlarmStrategyDeleteRequest();
        request.setUserInfo(getCurrentUserInfo());

        if (strategyId != null) {
            request.setStrategyId(strategyId);
        }
        if (strategyIds != null && !strategyIds.isEmpty()) {
            List<Integer> ids = Arrays.stream(strategyIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            request.setStrategyIds(ids);
        }

        return alarmStrategyAgentApiService.deleteAlarmStrategy(request);
    }

    @Tool(name = "updateAlarmStrategy",
          description = "Update an existing alarm strategy's configuration, including name, description, notification settings, and filtering conditions.")
    public String updateAlarmStrategy(
            @ToolParam(name = "strategyId", description = "The ID of the strategy to update", required = true)
            Integer strategyId,
            @ToolParam(name = "strategyName", description = "The new name of the alarm strategy", required = false)
            String strategyName,
            @ToolParam(name = "strategyDesc", description = "The new description of the alarm strategy", required = false)
            String strategyDesc,
            @ToolParam(name = "alertTeam", description = "The alert team name", required = false)
            String alertTeam,
            @ToolParam(name = "alertMembers", description = "Comma-separated list of alert member usernames", required = false)
            String alertMembers,
            @ToolParam(name = "atMembers", description = "Comma-separated list of members to @mention", required = false)
            String atMembers,
            @ToolParam(name = "includeEnvs", description = "Comma-separated list of environments to include", required = false)
            String includeEnvs,
            @ToolParam(name = "exceptEnvs", description = "Comma-separated list of environments to exclude", required = false)
            String exceptEnvs,
            @ToolParam(name = "includeZones", description = "Comma-separated list of zones to include", required = false)
            String includeZones,
            @ToolParam(name = "exceptZones", description = "Comma-separated list of zones to exclude", required = false)
            String exceptZones,
            @ToolParam(name = "includeModules", description = "Comma-separated list of modules to include", required = false)
            String includeModules,
            @ToolParam(name = "exceptModules", description = "Comma-separated list of modules to exclude", required = false)
            String exceptModules,
            @ToolParam(name = "includeFunctions", description = "Comma-separated list of functions to include", required = false)
            String includeFunctions,
            @ToolParam(name = "exceptFunctions", description = "Comma-separated list of functions to exclude", required = false)
            String exceptFunctions) {

        log.info("Updating alarm strategy: {}", strategyId);

        AlarmStrategyUpdateRequest request = new AlarmStrategyUpdateRequest();
        request.setUserInfo(getCurrentUserInfo());
        request.setStrategyId(strategyId);
        request.setStrategyName(strategyName);
        request.setStrategyDesc(strategyDesc);
        request.setAlertTeam(alertTeam);

        if (alertMembers != null && !alertMembers.isEmpty()) {
            request.setAlertMembers(parseCommaSeparatedList(alertMembers));
        }
        if (atMembers != null && !atMembers.isEmpty()) {
            request.setAtMembers(parseCommaSeparatedList(atMembers));
        }
        if (includeEnvs != null && !includeEnvs.isEmpty()) {
            request.setIncludeEnvs(parseCommaSeparatedList(includeEnvs));
        }
        if (exceptEnvs != null && !exceptEnvs.isEmpty()) {
            request.setExceptEnvs(parseCommaSeparatedList(exceptEnvs));
        }
        if (includeZones != null && !includeZones.isEmpty()) {
            request.setIncludeZones(parseCommaSeparatedList(includeZones));
        }
        if (exceptZones != null && !exceptZones.isEmpty()) {
            request.setExceptZones(parseCommaSeparatedList(exceptZones));
        }
        if (includeModules != null && !includeModules.isEmpty()) {
            request.setIncludeModules(parseCommaSeparatedList(includeModules));
        }
        if (exceptModules != null && !exceptModules.isEmpty()) {
            request.setExceptModules(parseCommaSeparatedList(exceptModules));
        }
        if (includeFunctions != null && !includeFunctions.isEmpty()) {
            request.setIncludeFunctions(parseCommaSeparatedList(includeFunctions));
        }
        if (exceptFunctions != null && !exceptFunctions.isEmpty()) {
            request.setExceptFunctions(parseCommaSeparatedList(exceptFunctions));
        }

        return alarmStrategyAgentApiService.updateAlarmStrategy(request);
    }

    @Tool(name = "queryAlarmStrategy",
          description = "Query alarm strategies. Can query by strategy ID for detailed info, or search by conditions like app name, strategy type, etc.")
    public String queryAlarmStrategy(
            @ToolParam(name = "strategyId", description = "The strategy ID to get detailed information (optional, if provided will return detailed info)", required = false)
            Integer strategyId,
            @ToolParam(name = "appId", description = "The application ID to filter by", required = false)
            Integer appId,
            @ToolParam(name = "appName", description = "The application name to filter by (supports fuzzy search)", required = false)
            String appName,
            @ToolParam(name = "strategyType", description = "The strategy type to filter by: 1-application, 2-system, 3-custom", required = false)
            Integer strategyType,
            @ToolParam(name = "strategyName", description = "The strategy name to filter by (supports fuzzy search)", required = false)
            String strategyName,
            @ToolParam(name = "status", description = "The status to filter by: 0-enabled, 1-disabled", required = false)
            Integer status,
            @ToolParam(name = "page", description = "The page number (default 1)", required = false)
            Integer page,
            @ToolParam(name = "pageSize", description = "The page size (default 10, max 100)", required = false)
            Integer pageSize,
            @ToolParam(name = "sortBy", description = "The field to sort by (default 'update_time')", required = false)
            String sortBy,
            @ToolParam(name = "sortOrder", description = "The sort order: 'asc' or 'desc' (default 'desc')", required = false)
            String sortOrder,
            @ToolParam(name = "owner", description = "Whether to filter by owner (true to show only user's own strategies)", required = false)
            Boolean owner) {

        log.info("Querying alarm strategy: strategyId={}, appName={}, strategyName={}", strategyId, appName, strategyName);

        AlarmStrategyQueryRequest request = new AlarmStrategyQueryRequest();
        request.setUserInfo(getCurrentUserInfo());

        if (strategyId != null) {
            request.setStrategyId(strategyId);
        }
        if (appId != null) {
            request.setAppId(appId);
        }
        if (appName != null) {
            request.setAppName(appName);
        }
        if (strategyType != null) {
            request.setStrategyType(strategyType);
        }
        if (strategyName != null) {
            request.setStrategyName(strategyName);
        }
        if (status != null) {
            request.setStatus(status);
        }
        if (page != null) {
            request.setPage(page);
        }
        if (pageSize != null) {
            request.setPageSize(pageSize);
        }
        if (sortBy != null) {
            request.setSortBy(sortBy);
        }
        if (sortOrder != null) {
            request.setSortOrder(sortOrder);
        }
        if (owner != null) {
            request.setOwner(owner);
        }

        return alarmStrategyAgentApiService.queryAlarmStrategy(request);
    }

    // ==================== Helper Methods ====================

    /**
     * Get current user info from ThreadLocal context.
     */
    private UserInfo getCurrentUserInfo() {
        UserContext.UserInfo contextUser = UserContext.get();
        if (contextUser == null) {
            log.warn("User context not found, using default values");
            return buildUserInfo("unknown", 0);
        }
        return buildUserInfo(contextUser.getUsername(), contextUser.getUserType());
    }

    /**
     * Build UserInfo object for Dubbo request.
     */
    private UserInfo buildUserInfo(String user, Integer userType) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUser(user);
        userInfo.setUserType(userType);
        return userInfo;
    }

    /**
     * Parse comma-separated string to list.
     */
    private List<String> parseCommaSeparatedList(String input) {
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
