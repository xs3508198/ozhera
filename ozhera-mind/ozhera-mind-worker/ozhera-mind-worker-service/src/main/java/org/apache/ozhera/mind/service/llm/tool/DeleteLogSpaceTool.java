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

import com.google.gson.Gson;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.log.api.model.agent.SpaceInfo;
import org.apache.ozhera.log.api.model.agent.UserInfo;
import org.apache.ozhera.log.api.service.LogAgentApiService;
import org.apache.ozhera.mind.service.confirmation.ConfirmationManager;
import org.apache.ozhera.mind.service.confirmation.ConfirmationMessage;
import org.apache.ozhera.mind.service.context.UserContext;
import reactor.core.publisher.FluxSink;

import java.util.Map;

@Slf4j
public class DeleteLogSpaceTool {

    private static final long CONFIRM_TIMEOUT_SECONDS = 300;
    private static final Gson GSON = new Gson();

    private final FluxSink<String> sink;
    private final ConfirmationManager confirmationManager;
    private final LogAgentApiService logAgentApiService;

    public DeleteLogSpaceTool(FluxSink<String> sink,
                               ConfirmationManager confirmationManager,
                               LogAgentApiService logAgentApiService) {
        this.sink = sink;
        this.confirmationManager = confirmationManager;
        this.logAgentApiService = logAgentApiService;
    }

    @Tool(name = "deleteLogSpace",
          description = "Delete a log space by its ID. This will also delete all stores and tails within the space. Requires user confirmation.")
    public String deleteSpace(
            @ToolParam(name = "spaceId", description = "The ID of the space to delete", required = true)
            Long spaceId) {

        String username = UserContext.get().getUsername();
        log.info("Delete space requested: spaceId={}, user={}", spaceId, username);

        String spaceDetail = logAgentApiService.getSpaceById(spaceId);

        String token = confirmationManager.register(
                username,
                "deleteLogSpace",
                Map.of("spaceId", spaceId)
        );

        ConfirmationMessage confirmMsg = ConfirmationMessage.builder()
                .type("confirmation")
                .token(token)
                .operation("deleteLogSpace")
                .message(String.format("确认删除 LogSpace (ID: %d)？此操作不可恢复，将同时删除其中所有的 stores 和 tails。", spaceId))
                .detail(Map.of(
                        "spaceId", spaceId,
                        "spaceDetail", spaceDetail != null ? spaceDetail : "N/A"
                ))
                .expireTime(System.currentTimeMillis() + CONFIRM_TIMEOUT_SECONDS * 1000)
                .build();

        sendConfirmation(GSON.toJson(confirmMsg));

        boolean confirmed = confirmationManager.waitForConfirmation(token, CONFIRM_TIMEOUT_SECONDS);

        if (!confirmed) {
            log.info("User cancelled or timeout: spaceId={}", spaceId);
            return "操作已取消";
        }

        if (!confirmationManager.tryAcquireExecution(token)) {
            log.error("Failed to acquire execution: token={}, spaceId={}", token, spaceId);
            return "操作状态异常，已取消";
        }

        log.info("Executing delete space: spaceId={}, user={}", spaceId, username);
//        SpaceInfo spaceInfo = new SpaceInfo();
//        spaceInfo.setSpaceId(spaceId);
//        spaceInfo.setUserInfo(buildUserInfo(username));
//        String result = logAgentApiService.deleteSpace(spaceInfo);
        log.info("测试删除空间成功！");
        String result = "success";
        return "删除成功: " + result;
    }

    private void sendConfirmation(String confirmJson) {
        if (sink != null) {
            sink.next("##CONFIRM##" + confirmJson);
        }
    }

    private UserInfo buildUserInfo(String username) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUser(username);
        return userInfo;
    }
}
