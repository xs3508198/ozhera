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
package org.apache.ozhera.log.manager.test;

import cn.hutool.Hutool;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.ozhera.log.model.MiLogStreamConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2021/7/8 10:30
 */
@Slf4j
public class StreamTest {

    Gson gson;

    @Before
    public void init() {
        gson = new Gson();
    }

    @Test
    public void test1() {
        Stream.of("one", "two", "three", "four")
                .filter(e -> e.length() > 3)
                .peek(e -> System.out.println("Filtered value: " + e))
                .map(String::toUpperCase)
                .peek(e -> System.out.println("Mapped value: " + e))
                .collect(Collectors.toList());
    }

    @Test
    public void testMapRemove() {
        String rules = "";
        MiLogStreamConfig miLogStreamConfig = gson.fromJson(rules, MiLogStreamConfig.class);
        if(miLogStreamConfig != null) {
            Map<String, Map<Long, String>> config = miLogStreamConfig.getConfig();
            config.values().forEach(longStringMap -> {
                longStringMap.keySet().removeIf(key -> key.equals(1l));
                log.info(gson.toJson(longStringMap));
            });
            log.info(gson.toJson(config));
        }
    }

    @Test
    public void test() {
        String str = "127.0.0.1:54014";
        String ip = StringUtils.substringBefore(str, ":");
        log.info("ip:{}", ip);
    }

    @Test
    public void testGson() {
        String data = "{\"code\":1210,\"message\":\"This name has been used!\",\"requestId\":\"d46567ad-458e-4d9c-aa22-1ced1fe70a46\",\"cost\":\"6 ms\",\"data\":null}";
        Map<String, Object> map = gson.fromJson(data, new TypeToken<Map>() {
        }.getType());

        Double dd = (Double) map.get("code");
        log.info("return data:{}", dd.compareTo(1210.0));
    }

    /**
     * Test hutools tool class
     */
    @Test
    public void testHutool() {
        Hutool.printAllUtils();
    }

    @Test
    public void testIncrement() {
        int i = 1;
        i = i++;
        System.out.println(i);

        System.out.println(Instant.now().toEpochMilli());
    }
}
