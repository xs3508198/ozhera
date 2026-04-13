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

import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.net.InetAddress;

/**
 * Registers this Worker instance to Nacos service discovery.
 */
@Slf4j
@Component
public class NacosServiceRegistry {

    @Value("${mind.worker.service.name:ozhera-mind-worker}")
    private String serviceName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Resource(name = "nacosNamingService")
    private NamingService namingService;

    private String ip;

    @PostConstruct
    public void register() {
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
            namingService.registerInstance(serviceName, ip, serverPort);
            log.info("Registered worker to Nacos: serviceName={}, ip={}, port={}", serviceName, ip, serverPort);
        } catch (Exception e) {
            log.error("Failed to register worker to Nacos", e);
            throw new RuntimeException("Failed to register to Nacos", e);
        }
    }

    @PreDestroy
    public void deregister() {
        try {
            namingService.deregisterInstance(serviceName, ip, serverPort);
            log.info("Deregistered worker from Nacos: serviceName={}, ip={}, port={}", serviceName, ip, serverPort);
        } catch (Exception e) {
            log.error("Failed to deregister worker from Nacos", e);
        }
    }
}
