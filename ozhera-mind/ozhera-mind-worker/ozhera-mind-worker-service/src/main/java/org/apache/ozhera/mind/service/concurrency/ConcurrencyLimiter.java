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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Global Concurrency Limiter.
 * Limits the number of concurrent requests the system can handle.
 */
@Slf4j
@Component
public class ConcurrencyLimiter {

    @NacosValue(value = "${agent.concurrency.max-concurrent-requests:1000}", autoRefreshed = true)
    private int maxConcurrentRequests;

    private Semaphore semaphore;

    @PostConstruct
    public void init() {
        this.semaphore = new Semaphore(maxConcurrentRequests);
        log.info("ConcurrencyLimiter initialized with max={}", maxConcurrentRequests);
    }

    /**
     * Try to acquire a permit.
     *
     * @param timeout the timeout value
     * @param unit    the time unit
     * @return true if permit acquired, false if timeout
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return semaphore.tryAcquire(timeout, unit);
    }

    /**
     * Release a permit.
     */
    public void release() {
        semaphore.release();
    }

    /**
     * Get available permits count.
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Get queue length (waiting threads).
     */
    public int getQueueLength() {
        return semaphore.getQueueLength();
    }
}
