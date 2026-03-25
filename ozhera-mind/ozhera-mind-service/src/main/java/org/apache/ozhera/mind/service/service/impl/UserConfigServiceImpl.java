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
package org.apache.ozhera.mind.service.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.xiaomi.mone.tpc.login.util.UserUtil;
import com.xiaomi.mone.tpc.login.vo.AuthUserVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.dao.mapper.UserConfigMapper;
import org.apache.ozhera.mind.service.llm.LlmModelService;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class UserConfigServiceImpl implements UserConfigService {

    @Resource
    private UserConfigMapper userConfigMapper;

    @Lazy
    @Resource
    private LlmModelService llmModelService;

    @Override
    public UserConfig saveOrUpdate(UserConfig config) {
        String username = getCurrentUsername();
        UserConfig existing = getByUsername(username);

        if (existing != null) {
            existing.setModelPlatform(config.getModelPlatform());
            existing.setModelType(config.getModelType());
            existing.setModelOwner(config.getModelOwner());
            existing.setApiKey(config.getApiKey());
            existing.setUpdateTime(System.currentTimeMillis());
            userConfigMapper.update(existing);
            // Invalidate model cache when config changes
            llmModelService.invalidateCache(username);
            log.info("Updated config for user: {}", username);
            return existing;
        } else {
            config.setUsername(username);
            config.setCreateTime(System.currentTimeMillis());
            config.setUpdateTime(System.currentTimeMillis());
            userConfigMapper.insert(config);
            log.info("Created config for user: {}", username);
            return config;
        }
    }

    @Override
    public UserConfig getMyConfig() {
        String username = getCurrentUsername();
        return getByUsername(username);
    }

    @Override
    public void deleteMyConfig() {
        String username = getCurrentUsername();
        UserConfig config = getByUsername(username);
        if (config != null) {
            userConfigMapper.deleteById(config.getId());
            // Invalidate model cache when config is deleted
            llmModelService.invalidateCache(username);
            log.info("Deleted config for user: {}", username);
        }
    }

    @Override
    public UserConfig getByUsername(String username) {
        QueryWrapper query = QueryWrapper.create()
                .where("username = ?", username)
                .limit(1);
        return userConfigMapper.selectOneByQuery(query);
    }

    private String getCurrentUsername() {
        AuthUserVo user = UserUtil.getUser();
        if (user == null) {
            throw new RuntimeException("User not logged in");
        }
        return user.genFullAccount();
    }
}
