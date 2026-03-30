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
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.dao.mapper.UserConfigMapper;
import org.apache.ozhera.mind.service.llm.entity.UserConfig;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * User config service for Worker.
 * Only provides read access - write operations are handled by Gateway.
 */
@Slf4j
@Service
public class UserConfigServiceImpl implements UserConfigService {

    @Resource
    private UserConfigMapper userConfigMapper;

    @Override
    public UserConfig saveOrUpdate(UserConfig config) {
        // Write operations should be done through Gateway
        throw new UnsupportedOperationException("Use Gateway API to save config");
    }

    @Override
    public UserConfig getMyConfig() {
        // Worker doesn't have user context, use getByUsername instead
        throw new UnsupportedOperationException("Use getByUsername instead");
    }

    @Override
    public void deleteMyConfig() {
        // Write operations should be done through Gateway
        throw new UnsupportedOperationException("Use Gateway API to delete config");
    }

    @Override
    public UserConfig getByUsername(String username) {
        QueryWrapper query = QueryWrapper.create()
                .where("username = ?", username)
                .limit(1);
        return userConfigMapper.selectOneByQuery(query);
    }
}
