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
package org.apache.ozhera.mind.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ModelInfo;
import org.apache.ozhera.mind.api.dto.UserModelConfigDTO;
import org.apache.ozhera.mind.gateway.controller.dto.ApiResult;
import org.apache.ozhera.mind.gateway.entity.UserConfig;
import org.apache.ozhera.mind.gateway.service.LlmModelService;
import org.apache.ozhera.mind.gateway.service.UserConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/mind/config")
public class UserConfigController {

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private LlmModelService llmModelService;

    /**
     * Save platform and apiKey
     */
    @PostMapping("/save")
    public ApiResult<UserModelConfigDTO> saveConfig(@RequestBody UserModelConfigDTO dto) {
        try {
            UserConfig config = UserConfig.builder()
                    .modelPlatform(dto.getModelPlatform())
                    .apiKey(dto.getApiKey())
                    .build();

            UserConfig saved = userConfigService.saveOrUpdate(config);
            return ApiResult.success(toDTO(saved));
        } catch (Exception e) {
            log.error("Save user config failed", e);
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * Update model type and owner
     */
    @PostMapping("/model")
    public ApiResult<UserModelConfigDTO> updateModel(@RequestBody ModelInfo modelInfo) {
        try {
            UserConfig existing = userConfigService.getMyConfig();
            if (existing == null) {
                return ApiResult.error("Please save platform and apiKey first");
            }

            existing.setModelType(modelInfo.getModelId());
            existing.setModelOwner(modelInfo.getOwner());
            UserConfig updated = userConfigService.saveOrUpdate(existing);
            return ApiResult.success(toDTO(updated));
        } catch (Exception e) {
            log.error("Update model failed", e);
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * Get current user's config
     */
    @GetMapping("/get")
    public ApiResult<UserModelConfigDTO> getMyConfig() {
        try {
            UserConfig config = userConfigService.getMyConfig();
            if (config == null) {
                return ApiResult.success(null);
            }
            return ApiResult.success(toDTO(config));
        } catch (Exception e) {
            log.error("Get user config failed", e);
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * Update platform and apiKey
     */
    @PostMapping("/update")
    public ApiResult<UserModelConfigDTO> updateConfig(@RequestBody UserModelConfigDTO dto) {
        try {
            UserConfig existing = userConfigService.getMyConfig();
            if (existing == null) {
                return ApiResult.error("Config not found, please create first");
            }

            existing.setModelPlatform(dto.getModelPlatform());
            existing.setApiKey(dto.getApiKey());
            existing.setModelType(null);
            existing.setModelOwner(null);

            UserConfig updated = userConfigService.saveOrUpdate(existing);
            return ApiResult.success(toDTO(updated));
        } catch (Exception e) {
            log.error("Update user config failed", e);
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * Delete current user's config
     */
    @DeleteMapping("/delete")
    public ApiResult<Boolean> deleteConfig() {
        try {
            UserConfig existing = userConfigService.getMyConfig();
            if (existing == null) {
                return ApiResult.error("Config not found");
            }
            userConfigService.deleteMyConfig();
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error("Delete user config failed", e);
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * Get available models for the current user's platform
     */
    @GetMapping("/models")
    public ApiResult<List<ModelInfo>> listModels() {
        try {
            UserConfig config = userConfigService.getMyConfig();
            if (config == null) {
                return ApiResult.error("Please save platform and apiKey first");
            }
            if (config.getModelPlatform() == null || config.getApiKey() == null) {
                return ApiResult.error("Platform and apiKey are required");
            }

            List<ModelInfo> models = llmModelService.listModels(config.getModelPlatform(), config.getApiKey());
            return ApiResult.success(models);
        } catch (Exception e) {
            log.error("List models failed", e);
            return ApiResult.error(e.getMessage());
        }
    }

    private UserModelConfigDTO toDTO(UserConfig config) {
        UserModelConfigDTO dto = new UserModelConfigDTO();
        dto.setId(config.getId());
        dto.setModelPlatform(config.getModelPlatform());
        dto.setModelType(config.getModelType());
        dto.setModelOwner(config.getModelOwner());
        dto.setApiKey(config.getApiKey());
        return dto;
    }
}
