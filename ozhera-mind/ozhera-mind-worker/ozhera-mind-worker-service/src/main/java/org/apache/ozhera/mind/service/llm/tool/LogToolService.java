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
import org.apache.ozhera.log.api.model.agent.SpaceInfo;
import org.apache.ozhera.log.api.model.agent.StoreInfo;
import org.apache.ozhera.log.api.model.agent.TailInfo;
import org.apache.ozhera.log.api.model.agent.UserInfo;
import org.apache.ozhera.log.api.service.LogAgentApiService;
import org.apache.ozhera.mind.service.context.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class LogToolService {

    @DubboReference(interfaceClass = LogAgentApiService.class, group = "${log.agent.group}", check = false)
    private LogAgentApiService logAgentApiService;

    // ==================== Space Tools ====================

    @Tool(name = "createLogSpace", description = "Create a new log space. A space is a logical container for organizing log stores and tails.")
    public String createSpace(
            @ToolParam(name = "spaceName", description = "The name of the space to create", required = true) String spaceName,
            @ToolParam(name = "spaceDescription", description = "A description of the space", required = true) String spaceDescription) {
        log.info("Creating log space: {}", spaceName);
        SpaceInfo spaceInfo = new SpaceInfo();
        spaceInfo.setSpaceName(spaceName);
        spaceInfo.setSpaceDescription(spaceDescription);
        spaceInfo.setUserInfo(getCurrentUserInfo());
        return logAgentApiService.createSpace(spaceInfo);
    }

    @Tool(name = "updateLogSpace", description = "Update an existing log space's name or description.")
    public String updateSpace(
            @ToolParam(name = "spaceId", description = "The ID of the space to update", required = true) Long spaceId,
            @ToolParam(name = "spaceName", description = "The new name of the space", required = true) String spaceName,
            @ToolParam(name = "spaceDescription", description = "The new description of the space", required = true) String spaceDescription) {
        log.info("Updating log space: {}", spaceId);
        SpaceInfo spaceInfo = new SpaceInfo();
        spaceInfo.setSpaceId(spaceId);
        spaceInfo.setSpaceName(spaceName);
        spaceInfo.setSpaceDescription(spaceDescription);
        spaceInfo.setUserInfo(getCurrentUserInfo());
        return logAgentApiService.updateSpace(spaceInfo);
    }

    // deleteLogSpace moved to DeleteLogSpaceTool (request-scoped, with Human-in-the-Loop confirmation)

    @Tool(name = "getLogSpaceById", description = "Get detailed information about a log space by its ID.")
    public String getSpaceById(
            @ToolParam(name = "spaceId", description = "The ID of the space to retrieve", required = true) Long spaceId) {
        log.info("Getting log space by ID: {}", spaceId);
        return logAgentApiService.getSpaceById(spaceId);
    }

    // ==================== Store Tools ====================

    @Tool(name = "getStoresInSpace", description = "Get all log stores within a specific space. Returns a tree structure of stores and their tails.")
    public String getStoresInSpace(
            @ToolParam(name = "spaceId", description = "The ID of the space", required = true) Long spaceId) {
        log.info("Getting stores in space: {}", spaceId);
        return logAgentApiService.getStoresInSpace(spaceId);
    }

    @Tool(name = "getLogStoreById", description = "Get detailed information about a log store by its ID.")
    public String getStoreInfoById(
            @ToolParam(name = "storeId", description = "The ID of the store to retrieve", required = true) Long storeId) {
        log.info("Getting log store by ID: {}", storeId);
        StoreInfo storeInfo = new StoreInfo();
        storeInfo.setStoreId(storeId);
        storeInfo.setUserInfo(getCurrentUserInfo());
        return logAgentApiService.getStoreInfoById(storeInfo);
    }

    @Tool(name = "createLogStore", description = "Create a new log store within a space. A store is a collection point for log data with specific configuration.")
    public String createStore(
            @ToolParam(name = "spaceId", description = "The ID of the space where the store will be created", required = true) Long spaceId,
            @ToolParam(name = "storeName", description = "The name of the store", required = true) String storeName,
            @ToolParam(name = "machineRoom", description = "The machine room ID", required = false) Integer machineRoom,
            @ToolParam(name = "logType", description = "The log type", required = false) String logType,
            @ToolParam(name = "storePeriod", description = "The storage period in days (default 180)", required = false) Integer storePeriod,
            @ToolParam(name = "shardCnt", description = "The shard count (default 1)", required = false) Integer shardCnt) {
        log.info("Creating log store: {} in space: {}", storeName, spaceId);
        StoreInfo storeInfo = new StoreInfo();
        storeInfo.setSpaceId(spaceId);
        storeInfo.setStoreName(storeName);
        storeInfo.setUserInfo(getCurrentUserInfo());
        if (machineRoom != null) {
            storeInfo.setMachineRoom(machineRoom);
        }
        if (logType != null) {
            storeInfo.setLogType(logType);
        }
        if (storePeriod != null) {
            storeInfo.setStorePeriod(storePeriod);
        }
        if (shardCnt != null) {
            storeInfo.setShardCnt(shardCnt);
        }
        return logAgentApiService.createStore(storeInfo);
    }

    @Tool(name = "updateLogStore", description = "Update an existing log store's configuration.")
    public String updateStore(
            @ToolParam(name = "storeId", description = "The ID of the store to update", required = true) Long storeId,
            @ToolParam(name = "spaceId", description = "The space ID", required = true) Long spaceId,
            @ToolParam(name = "storeName", description = "The new name of the store", required = true) String storeName,
            @ToolParam(name = "storePeriod", description = "The storage period in days", required = false) Integer storePeriod,
            @ToolParam(name = "shardCnt", description = "The shard count", required = false) Integer shardCnt) {
        log.info("Updating log store: {}", storeId);
        StoreInfo storeInfo = new StoreInfo();
        storeInfo.setStoreId(storeId);
        storeInfo.setSpaceId(spaceId);
        storeInfo.setStoreName(storeName);
        storeInfo.setUserInfo(getCurrentUserInfo());
        if (storePeriod != null) {
            storeInfo.setStorePeriod(storePeriod);
        }
        if (shardCnt != null) {
            storeInfo.setShardCnt(shardCnt);
        }
        return logAgentApiService.updateStore(storeInfo);
    }

    @Tool(name = "deleteLogStore", description = "Delete a log store by its ID. This will also delete all tails within the store.")
    public String deleteStore(
            @ToolParam(name = "storeId", description = "The ID of the store to delete", required = true) Long storeId) {
        log.info("Deleting log store: {}", storeId);
        StoreInfo storeInfo = new StoreInfo();
        storeInfo.setStoreId(storeId);
        storeInfo.setUserInfo(getCurrentUserInfo());
        return logAgentApiService.deleteStore(storeInfo);
    }

    // ==================== Tail Tools ====================

    @Tool(name = "createLogTail", description = "Create a new log tail (log collection configuration) for collecting logs from a specific path.")
    public String createTail(
            @ToolParam(name = "spaceId", description = "The space ID", required = true) Long spaceId,
            @ToolParam(name = "storeId", description = "The store ID where logs will be stored", required = true) Long storeId,
            @ToolParam(name = "tail", description = "The tail name (identifier for this log collection)", required = true) String tail,
            @ToolParam(name = "logPath", description = "The log file path to collect (e.g., /var/log/app/*.log)", required = true) String logPath,
            @ToolParam(name = "appId", description = "The application ID", required = false) Long appId,
            @ToolParam(name = "appName", description = "The application name", required = false) String appName,
            @ToolParam(name = "envId", description = "The environment ID", required = false) Long envId,
            @ToolParam(name = "envName", description = "The environment name", required = false) String envName,
            @ToolParam(name = "ips", description = "The list of IP addresses to collect logs from", required = false) List<String> ips,
            @ToolParam(name = "parseType", description = "The parse type for log parsing", required = false) Integer parseType,
            @ToolParam(name = "parseScript", description = "The parse script for custom log parsing", required = false) String parseScript) {
        log.info("Creating log tail: {} in store: {}", tail, storeId);
        TailInfo tailInfo = new TailInfo();
        tailInfo.setSpaceId(spaceId);
        tailInfo.setStoreId(storeId);
        tailInfo.setTail(tail);
        tailInfo.setLogPath(logPath);
        tailInfo.setUserInfo(getCurrentUserInfo());
        if (appId != null) {
            tailInfo.setAppId(appId);
        }
        if (appName != null) {
            tailInfo.setAppName(appName);
        }
        if (envId != null) {
            tailInfo.setEnvId(envId);
        }
        if (envName != null) {
            tailInfo.setEnvName(envName);
        }
        if (ips != null) {
            tailInfo.setIps(ips);
        }
        if (parseType != null) {
            tailInfo.setParseType(parseType);
        }
        if (parseScript != null) {
            tailInfo.setParseScript(parseScript);
        }
        return logAgentApiService.createTail(tailInfo);
    }

    @Tool(name = "updateLogTail", description = "Update an existing log tail configuration.")
    public String updateTail(
            @ToolParam(name = "id", description = "The tail ID to update", required = true) Long id,
            @ToolParam(name = "spaceId", description = "The space ID", required = true) Long spaceId,
            @ToolParam(name = "storeId", description = "The store ID", required = true) Long storeId,
            @ToolParam(name = "tail", description = "The tail name", required = true) String tail,
            @ToolParam(name = "logPath", description = "The log file path", required = true) String logPath,
            @ToolParam(name = "appId", description = "The application ID", required = false) Long appId,
            @ToolParam(name = "appName", description = "The application name", required = false) String appName,
            @ToolParam(name = "envId", description = "The environment ID", required = false) Long envId,
            @ToolParam(name = "envName", description = "The environment name", required = false) String envName,
            @ToolParam(name = "ips", description = "The list of IP addresses", required = false) List<String> ips,
            @ToolParam(name = "parseType", description = "The parse type", required = false) Integer parseType,
            @ToolParam(name = "parseScript", description = "The parse script", required = false) String parseScript) {
        log.info("Updating log tail: {}", id);
        TailInfo tailInfo = new TailInfo();
        tailInfo.setId(id);
        tailInfo.setSpaceId(spaceId);
        tailInfo.setStoreId(storeId);
        tailInfo.setTail(tail);
        tailInfo.setLogPath(logPath);
        tailInfo.setUserInfo(getCurrentUserInfo());
        if (appId != null) {
            tailInfo.setAppId(appId);
        }
        if (appName != null) {
            tailInfo.setAppName(appName);
        }
        if (envId != null) {
            tailInfo.setEnvId(envId);
        }
        if (envName != null) {
            tailInfo.setEnvName(envName);
        }
        if (ips != null) {
            tailInfo.setIps(ips);
        }
        if (parseType != null) {
            tailInfo.setParseType(parseType);
        }
        if (parseScript != null) {
            tailInfo.setParseScript(parseScript);
        }
        return logAgentApiService.updateTail(tailInfo);
    }

    @Tool(name = "deleteLogTail", description = "Delete a log tail by its ID.")
    public String deleteTail(
            @ToolParam(name = "id", description = "The tail ID to delete", required = true) Long id) {
        log.info("Deleting log tail: {}", id);
        TailInfo tailInfo = new TailInfo();
        tailInfo.setId(id);
        tailInfo.setUserInfo(getCurrentUserInfo());
        return logAgentApiService.deleteTail(tailInfo);
    }

    @Tool(name = "getLogTailById", description = "Get detailed information about a log tail by its ID.")
    public String getTailById(
            @ToolParam(name = "tailId", description = "The tail ID to retrieve", required = true) Long tailId) {
        log.info("Getting log tail by ID: {}", tailId);
        return logAgentApiService.getTailById(tailId);
    }

    // ==================== Helper Methods ====================

    /**
     * Get current user info from ThreadLocal context.
     */
    private UserInfo getCurrentUserInfo() {
        UserContext.UserInfo contextUser = UserContext.get();
        if (contextUser == null) {
            log.warn("User context not found, using default values");
            return buildUserInfo("unknown", 0, null);
        }
        return buildUserInfo(contextUser.getUsername(), contextUser.getUserType(), null);
    }

    private UserInfo buildUserInfo(String user, Integer userType, String zone) {
        UserInfo userInfo = new UserInfo();
//        userInfo.setUser(user);
        userInfo.setUser("xueshan");
        return userInfo;
    }
}
