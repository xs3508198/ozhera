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
package org.apache.ozhera.mind.server.config;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.xiaomi.mone.tpc.login.filter.HttpReqUserFilter;
import com.xiaomi.mone.tpc.login.util.ConstUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfiguration {

    @NacosValue(value = "${cas.public.key:}", autoRefreshed = true)
    private String casPublicKey;

    @Value("${inner.auth:true}")
    private String innerAuth;

    @Value("${dev.mode:false}")
    private String devMode;

    @Bean
    public FilterRegistrationBean<HttpReqUserFilter> filterCasBean() {
        FilterRegistrationBean<HttpReqUserFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new HttpReqUserFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.addInitParameter(ConstUtil.innerAuth, innerAuth);
        registrationBean.addInitParameter(ConstUtil.CAS_PUBLIC_KEY, casPublicKey);
        registrationBean.addInitParameter(ConstUtil.ignoreUrl, "/health,/actuator/*");
        registrationBean.addInitParameter(ConstUtil.devMode, devMode);

        registrationBean.setOrder(0);
        return registrationBean;
    }
}
