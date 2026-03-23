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
package org.apache.ozhera.mind.service.llm;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of LlmModelService.
 * Uses UserConfigService to get user's configuration and ChatModelFactory to create models.
 *
 * Internal network versions can override this by creating their own implementation
 * with @Primary annotation.
 */
@Slf4j
@Service
public class DefaultLlmModelService implements LlmModelService {

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private ChatModelFactory chatModelFactory;

    /**
     * Cache for user models to avoid recreating on every request.
     * Cache expires after 30 minutes or when user updates config.
     */
    private final Cache<String, Model> modelCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    @Override
    public Model getModel(String username) {
        Model cachedModel = modelCache.getIfPresent(username);
        if (cachedModel != null) {
            log.debug("Using cached model for user: {}", username);
            return cachedModel;
        }

        UserConfig config = userConfigService.getByUsername(username);
        if (config == null) {
            throw new RuntimeException("User config not found. Please configure your API key and model settings first.");
        }

        log.info("Creating new model for user: {}, platform: {}, model: {}",
                username, config.getModelPlatform(), config.getModelType());

        Model model = chatModelFactory.createModel(config);
        modelCache.put(username, model);
        return model;
    }

    @Override
    public void invalidateCache(String username) {
        log.info("Invalidating model cache for user: {}", username);
        modelCache.invalidate(username);
    }

    @Override
    public boolean hasUserConfig(String username) {
        return userConfigService.getByUsername(username) != null;
    }
}
