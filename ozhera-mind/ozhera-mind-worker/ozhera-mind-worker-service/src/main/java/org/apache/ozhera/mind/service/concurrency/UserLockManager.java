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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * User-level Lock Manager.
 * Ensures requests from the same user are processed serially.
 */
@Slf4j
@Component
public class UserLockManager {

    /**
     * User lock mapping.
     * Each user has a Semaphore(1) to ensure serial processing.
     */
    private final ConcurrentHashMap<String, Semaphore> userLocks = new ConcurrentHashMap<>();

    /**
     * Try to acquire user lock.
     *
     * @param username the username
     * @param timeout  timeout value
     * @param unit     time unit
     * @return true if lock acquired, false if timeout
     */
    public boolean tryLock(String username, long timeout, TimeUnit unit) throws InterruptedException {
        Semaphore lock = userLocks.computeIfAbsent(username, k -> new Semaphore(1));
        boolean acquired = lock.tryAcquire(timeout, unit);
        if (!acquired) {
            log.warn("Failed to acquire lock for user: {}, timeout: {}ms",
                    username, unit.toMillis(timeout));
        }
        return acquired;
    }

    /**
     * Release user lock.
     *
     * @param username the username
     */
    public void unlock(String username) {
        Semaphore lock = userLocks.get(username);
        if (lock != null) {
            lock.release();
        }
    }

    /**
     * Check if user is locked.
     *
     * @param username the username
     * @return true if locked
     */
    public boolean isLocked(String username) {
        Semaphore lock = userLocks.get(username);
        return lock != null && lock.availablePermits() == 0;
    }

    /**
     * Cleanup inactive locks (call periodically).
     */
    public void cleanup() {
        userLocks.entrySet().removeIf(entry ->
                entry.getValue().availablePermits() == 1);
    }
}
