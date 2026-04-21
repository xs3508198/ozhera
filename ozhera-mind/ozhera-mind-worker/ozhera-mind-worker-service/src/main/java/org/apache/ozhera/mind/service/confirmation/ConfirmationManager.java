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
package org.apache.ozhera.mind.service.confirmation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ConfirmationManager {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final long EXPIRE_TIME_MS = 5 * 60 * 1000;

    private final Map<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    @Data
    public static class PendingConfirmation {
        private String username;
        private String operation;
        private Map<String, Object> params;
        private CompletableFuture<Boolean> future;
        private long createTime;
        private final AtomicBoolean processed = new AtomicBoolean(false);
        private final AtomicBoolean executed = new AtomicBoolean(false);

        public PendingConfirmation(String username, String operation,
                                   Map<String, Object> params,
                                   CompletableFuture<Boolean> future,
                                   long createTime) {
            this.username = username;
            this.operation = operation;
            this.params = params;
            this.future = future;
            this.createTime = createTime;
        }
    }

    public String register(String username, String operation, Map<String, Object> params) {
        String token = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        PendingConfirmation pending = new PendingConfirmation(
                username, operation, params, future, System.currentTimeMillis()
        );

        pendingConfirmations.put(token, pending);
        log.info("Registered confirmation: token={}, user={}, operation={}", token, username, operation);

        return token;
    }

    public boolean waitForConfirmation(String token, long timeoutSeconds) {
        PendingConfirmation pending = pendingConfirmations.get(token);
        if (pending == null) {
            log.warn("Confirmation not found: token={}", token);
            return false;
        }

        try {
            log.info("Waiting for confirmation: token={}, timeout={}s", token, timeoutSeconds);
            return pending.getFuture().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            if (pending.getProcessed().compareAndSet(false, true)) {
                pendingConfirmations.remove(token);
                log.warn("Confirmation timeout: token={}", token);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.getProcessed().compareAndSet(false, true);
            pendingConfirmations.remove(token);
            log.warn("Confirmation interrupted: token={}", token);
            return false;
        } catch (Exception e) {
            pending.getProcessed().compareAndSet(false, true);
            pendingConfirmations.remove(token);
            log.error("Confirmation error: token={}", token, e);
            return false;
        }
    }

    public boolean waitForConfirmation(String token) {
        return waitForConfirmation(token, DEFAULT_TIMEOUT_SECONDS);
    }

    public boolean confirm(String token, String username, boolean confirmed) {
        PendingConfirmation pending = pendingConfirmations.get(token);

        if (pending == null) {
            log.warn("Confirmation not found or expired: token={}", token);
            return false;
        }

        if (!pending.getProcessed().compareAndSet(false, true)) {
            log.warn("Confirmation already processed (timeout): token={}", token);
            return false;
        }

        if (!pending.getUsername().equals(username)) {
            log.warn("User mismatch: token={}, expected={}, actual={}",
                    token, pending.getUsername(), username);
            return false;
        }

        if (System.currentTimeMillis() - pending.getCreateTime() > EXPIRE_TIME_MS) {
            log.warn("Confirmation expired: token={}", token);
            pendingConfirmations.remove(token);
            return false;
        }

        boolean completed = pending.getFuture().complete(confirmed);
        log.info("Confirmation completed: token={}, confirmed={}, success={}",
                token, confirmed, completed);

        return completed;
    }

    public boolean tryAcquireExecution(String token) {
        PendingConfirmation pending = pendingConfirmations.get(token);

        if (pending == null) {
            log.warn("Cannot acquire execution, token not found: {}", token);
            return false;
        }

        boolean acquired = pending.getExecuted().compareAndSet(false, true);

        if (acquired) {
            log.info("Execution acquired: token={}", token);
            pendingConfirmations.remove(token);
        } else {
            log.warn("Execution already acquired by another thread: token={}", token);
        }

        return acquired;
    }

    public PendingConfirmation getPending(String token) {
        return pendingConfirmations.get(token);
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        pendingConfirmations.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getCreateTime() > EXPIRE_TIME_MS) {
                entry.getValue().getFuture().complete(false);
                log.info("Cleaned up expired confirmation: token={}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
