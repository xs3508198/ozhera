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
package org.apache.ozhera.mind.service.concurrency;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * LLM API Rate Limiter.
 * Limits API calls per provider to prevent hitting external rate limits.
 * Simple implementation using Semaphore with time-based refill.
 */
@Slf4j
@Component
public class LLMRateLimiter {

    @NacosValue(value = "${agent.rate-limit.openai-rpm:60}", autoRefreshed = true)
    private int openaiRpm;

    @NacosValue(value = "${agent.rate-limit.dashscope-rpm:100}", autoRefreshed = true)
    private int dashscopeRpm;

    @NacosValue(value = "${agent.rate-limit.default-rpm:50}", autoRefreshed = true)
    private int defaultRpm;

    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        buckets.put("openai", new RateLimitBucket(openaiRpm));
        buckets.put("dashscope", new RateLimitBucket(dashscopeRpm));
        log.info("LLMRateLimiter initialized: openai={}rpm, dashscope={}rpm",
                openaiRpm, dashscopeRpm);
    }

    /**
     * Acquire permission (blocking).
     *
     * @param provider the LLM provider name
     */
    public void acquire(String provider) {
        RateLimitBucket bucket = buckets.computeIfAbsent(
                provider.toLowerCase(),
                p -> new RateLimitBucket(defaultRpm)
        );
        bucket.acquire();
    }

    /**
     * Try to acquire permission (non-blocking).
     *
     * @param provider the LLM provider name
     * @return true if acquired, false otherwise
     */
    public boolean tryAcquire(String provider) {
        RateLimitBucket bucket = buckets.computeIfAbsent(
                provider.toLowerCase(),
                p -> new RateLimitBucket(defaultRpm)
        );
        return bucket.tryAcquire();
    }

    /**
     * Simple rate limit bucket using token bucket algorithm.
     */
    private static class RateLimitBucket {
        private final int maxTokens;
        private final Semaphore tokens;
        private volatile long lastRefillTime;
        private final long refillIntervalMs = 60_000; // 1 minute

        RateLimitBucket(int rpm) {
            this.maxTokens = rpm;
            this.tokens = new Semaphore(rpm);
            this.lastRefillTime = System.currentTimeMillis();
        }

        void acquire() {
            refillIfNeeded();
            try {
                tokens.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Rate limit acquire interrupted", e);
            }
        }

        boolean tryAcquire() {
            refillIfNeeded();
            return tokens.tryAcquire();
        }

        private synchronized void refillIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - lastRefillTime >= refillIntervalMs) {
                int toRefill = maxTokens - tokens.availablePermits();
                if (toRefill > 0) {
                    tokens.release(toRefill);
                }
                lastRefillTime = now;
            }
        }
    }
}
