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
package org.apache.ozhera.log.manager.service.extension.ai.llm;

import com.xiaomi.youpin.docean.Ioc;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.log.common.Config;

import static org.apache.ozhera.log.manager.service.extension.ai.llm.LlmService.DEFAULT_LLM_SERVICE_KEY;

/**
 * Factory for obtaining LlmService implementation.
 * The actual implementation is determined by configuration.
 */
@Slf4j
public class LlmServiceFactory {

    private static String factualServiceName;

    public static LlmService getLlmService() {
        factualServiceName = Config.ins().get("ai.llm.service", DEFAULT_LLM_SERVICE_KEY);
        log.debug("LlmServiceFactory factualServiceName:{}", factualServiceName);
        return Ioc.ins().getBean(factualServiceName);
    }
}
