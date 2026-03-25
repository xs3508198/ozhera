package org.apache.ozhera.mind.server.controller;


import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ModelInfo;
import org.apache.ozhera.mind.api.dto.UserModelConfigDTO;
import org.apache.ozhera.mind.server.dto.Result;
import org.apache.ozhera.mind.service.llm.LlmModelService;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/mind/user/config")
public class UserConfigController {

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private LlmModelService llmModelService;

    /**
     * Save platform and apiKey (step 1: save credentials first, then query models, then update model)
     */
    @PostMapping("/save")
    public Result<UserModelConfigDTO> saveConfig(@RequestBody UserModelConfigDTO dto) {
        try {
            UserConfig config = UserConfig.builder()
                    .modelPlatform(dto.getModelPlatform())
                    .apiKey(dto.getApiKey())
                    .build();

            UserConfig saved = userConfigService.saveOrUpdate(config);
            return Result.success(toDTO(saved));
        } catch (Exception e) {
            log.error("Save user config failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * Update model type and owner (step 2: after querying available models, update the selected model)
     */
    @PostMapping("/model")
    public Result<UserModelConfigDTO> updateModel(@RequestBody ModelInfo modelInfo) {
        try {
            UserConfig existing = userConfigService.getMyConfig();
            if (existing == null) {
                return Result.error("Please save platform and apiKey first");
            }

            existing.setModelType(modelInfo.getModelId());
            existing.setModelOwner(modelInfo.getOwner());
            UserConfig updated = userConfigService.saveOrUpdate(existing);
            return Result.success(toDTO(updated));
        } catch (Exception e) {
            log.error("Update model failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * Get current user's config (only returns own data)
     */
    @GetMapping("/get")
    public Result<UserModelConfigDTO> getMyConfig() {
        try {
            UserConfig config = userConfigService.getMyConfig();
            if (config == null) {
                return Result.success(null);
            }
            return Result.success(toDTO(config));
        } catch (Exception e) {
            log.error("Get user config failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * Update platform and apiKey
     */
    @PostMapping("/update")
    public Result<UserModelConfigDTO> updateConfig(@RequestBody UserModelConfigDTO dto) {
        try {
            UserConfig existing = userConfigService.getMyConfig();
            if (existing == null) {
                return Result.error("Config not found, please create first");
            }

            existing.setModelPlatform(dto.getModelPlatform());
            existing.setApiKey(dto.getApiKey());
            // Clear model info when platform/apiKey changes, user needs to re-select
            existing.setModelType(null);
            existing.setModelOwner(null);

            UserConfig updated = userConfigService.saveOrUpdate(existing);
            return Result.success(toDTO(updated));
        } catch (Exception e) {
            log.error("Update user config failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * Delete current user's config
     */
    @DeleteMapping("/delete")
    public Result<Boolean> deleteConfig() {
        try {
            UserConfig existing = userConfigService.getMyConfig();
            if (existing == null) {
                return Result.error("Config not found");
            }
            userConfigService.deleteMyConfig();
            return Result.success(true);
        } catch (Exception e) {
            log.error("Delete user config failed", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * List available models using saved config (platform and apiKey)
     *
     * @return list of available models with owner info
     */
    @GetMapping("/models")
    public Result<List<ModelInfo>> listModels() {
        try {
            UserConfig config = userConfigService.getMyConfig();
            if (config == null) {
                return Result.error("Please save platform and apiKey first");
            }
            if (config.getModelPlatform() == null || config.getApiKey() == null) {
                return Result.error("Platform and apiKey are required");
            }

            List<ModelInfo> models = llmModelService.listModels(config.getModelPlatform(), config.getApiKey());
            return Result.success(models);
        } catch (Exception e) {
            log.error("List models failed", e);
            return Result.error(e.getMessage());
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
