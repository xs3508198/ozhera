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
package org.apache.ozhera.mind.service.redis;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import redis.clients.jedis.*;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class RedisService {

    @NacosValue("${redis.address}")
    private String address;

    @NacosValue("${redis.password:}")
    private String pwd;

    @NacosValue("${redis.cluster:no}")
    private String cluster;

    @NacosValue("${redis.maxActive:8}")
    private int maxActive;

    @NacosValue("${redis.maxWait:3000}")
    private int maxWait;

    @NacosValue("${redis.maxIdle:8}")
    private int maxIdle;

    @NacosValue("${redis.timeout:3000}")
    private int timeout;

    private JedisPool jedisPool = null;
    private JedisCluster jedisCluster = null;

    /**
     * Get the JedisPool instance (for JedisSession usage).
     * Returns null if running in cluster mode.
     */
    public JedisPool getJedisPool() {
        return jedisPool;
    }

    /**
     * Check if running in cluster mode.
     */
    public boolean isClusterMode() {
        return "yes".equals(cluster);
    }

    @PostConstruct
    public void init() {
        log.info("RedisService init, address: {}, cluster: {}", address, cluster);

        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException("Redis address is null");
        }

        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxIdle(maxIdle);
        config.setMinIdle(0);
        config.setMaxTotal(maxActive);
        config.setMaxWaitMillis(maxWait);

        try {
            if ("yes".equals(cluster)) {
                Set<HostAndPort> nodes = new HashSet<>();
                for (String addr : address.split(",")) {
                    String[] infos = addr.split(":");
                    nodes.add(new HostAndPort(infos[0].trim(), Integer.parseInt(infos[1].trim())));
                }
                if (!StringUtils.hasText(pwd)) {
                    jedisCluster = new JedisCluster(nodes, 3000, timeout, 3, config);
                } else {
                    jedisCluster = new JedisCluster(nodes, 3000, timeout, 3, pwd, config);
                }
            } else {
                String[] infos = address.split(":");
                if (!StringUtils.hasText(pwd)) {
                    jedisPool = new JedisPool(config, infos[0].trim(), Integer.parseInt(infos[1].trim()), timeout);
                } else {
                    jedisPool = new JedisPool(config, infos[0].trim(), Integer.parseInt(infos[1].trim()), timeout, pwd);
                }
            }
            log.info("RedisService init success");
        } catch (Exception e) {
            log.error("Redis init error", e);
        }
    }

    public String get(String key) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(key);
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.get(key);
        }
        throw new RuntimeException("redis not initialized");
    }

    public boolean del(String key) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.del(key) == 1;
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.del(key) == 1;
        }
        throw new RuntimeException("redis not initialized");
    }

    public Long decr(String key) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.decr(key);
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.decr(key);
        }
        throw new RuntimeException("redis not initialized");
    }

    // ============ List operations for chat history ============

    /**
     * Append value to the end of a list
     */
    public Long rpush(String key, String value) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.rpush(key, value);
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.rpush(key, value);
        }
        throw new RuntimeException("redis not initialized");
    }

    /**
     * Get a range of elements from a list
     * @param start start index (0-based)
     * @param stop stop index (-1 means last element)
     */
    public List<String> lrange(String key, long start, long stop) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.lrange(key, start, stop);
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.lrange(key, start, stop);
        }
        throw new RuntimeException("redis not initialized");
    }

    /**
     * Get the length of a list
     */
    public Long llen(String key) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.llen(key);
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.llen(key);
        }
        throw new RuntimeException("redis not initialized");
    }

    /**
     * Trim a list to the specified range
     */
    public String ltrim(String key, long start, long stop) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.ltrim(key, start, stop);
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.ltrim(key, start, stop);
        }
        throw new RuntimeException("redis not initialized");
    }

    /**
     * Set expiration time for a key
     */
    public boolean expire(String key, long seconds) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.expire(key, seconds) == 1;
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.expire(key, seconds) == 1;
        }
        throw new RuntimeException("redis not initialized");
    }

    /**
     * Check if a key exists
     */
    public boolean exists(String key) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.exists(key);
            }
        }
        if (jedisCluster != null) {
            return jedisCluster.exists(key);
        }
        throw new RuntimeException("redis not initialized");
    }

    /**
     * Set value with expiration time
     */
    public boolean setEx(String key, long seconds, String value) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return "OK".equals(jedis.setex(key, seconds, value));
            }
        }
        if (jedisCluster != null) {
            return "OK".equals(jedisCluster.setex(key, seconds, value));
        }
        throw new RuntimeException("redis not initialized");
    }
}
