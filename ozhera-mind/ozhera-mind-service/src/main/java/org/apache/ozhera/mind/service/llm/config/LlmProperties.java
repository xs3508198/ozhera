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
package org.apache.ozhera.mind.service.llm.config;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class LlmProperties {

    @NacosValue(value = "${llm.temperature:0.7}", autoRefreshed = true)
    private Double temperature;

    @NacosValue(value = "${llm.max-tokens:4096}", autoRefreshed = true)
    private Integer maxTokens;

    @NacosValue(value = "${llm.top-p:1.0}", autoRefreshed = true)
    private Double topP;

    @NacosValue(value = "${llm.dashscope.enable-thinking:false}", autoRefreshed = true)
    private Boolean dashscopeEnableThinking;

    @NacosValue(value = "${llm.dashscope.enable-search:false}", autoRefreshed = true)
    private Boolean dashscopeEnableSearch;
}
