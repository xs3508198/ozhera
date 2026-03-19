package org.apache.ozhera.mind.server.controller;


import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.UserModelConfigDTO;
import org.apache.ozhera.mind.server.dto.Result;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/mind/user/config")
public class UserConfigController {

    @Resource
    private UserConfigService userConfigService;

    /**
     * Add or update user config (each user can only have one config)
     */
    @PostMapping("/save")
    public Result<UserModelConfigDTO> saveConfig(@RequestBody UserModelConfigDTO dto) {
        try {
            UserConfig config = UserConfig.builder()
                    .modelPlatform(dto.getModelPlatform())
                    .modelType(dto.getModelType())
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
     * Update current user's config
     */
    @PostMapping("/update")
    public Result<UserModelConfigDTO> updateConfig(@RequestBody UserModelConfigDTO dto) {
        try {
            UserConfig existing = userConfigService.getMyConfig();
            if (existing == null) {
                return Result.error("Config not found, please create first");
            }

            UserConfig config = UserConfig.builder()
                    .modelPlatform(dto.getModelPlatform())
                    .modelType(dto.getModelType())
                    .apiKey(dto.getApiKey())
                    .build();

            UserConfig updated = userConfigService.saveOrUpdate(config);
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

    private UserModelConfigDTO toDTO(UserConfig config) {
        UserModelConfigDTO dto = new UserModelConfigDTO();
        dto.setId(config.getId());
        dto.setModelPlatform(config.getModelPlatform());
        dto.setModelType(config.getModelType());
        dto.setApiKey(config.getApiKey());
        return dto;
    }
}
