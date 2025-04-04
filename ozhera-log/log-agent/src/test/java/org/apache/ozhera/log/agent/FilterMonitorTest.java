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
package org.apache.ozhera.log.agent;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.ozhera.log.agent.channel.file.FileListener;
import org.apache.ozhera.log.agent.channel.file.LogFileAlterationObserver;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author: wtt
 * @date: 2022/6/2 12:12
 * @description:
 */
@Slf4j
public class FilterMonitorTest {

    @Test
    public void testScheduleExecutor() throws IOException {
//        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(5);
//        scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
//            log.info("current thread:{}", Thread.currentThread().getName());
//        }, 0, 100, TimeUnit.MILLISECONDS);
//        new Thread(() -> {
//            ScheduledFuture<?> scheduledFuture = scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
//                log.info("child current thread:{}", Thread.currentThread().getName());
//            }, 0, 100, TimeUnit.MILLISECONDS);
//            try {
//                TimeUnit.MINUTES.sleep(1);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            scheduledFuture.cancel(true);
//        }).start();
//        System.in.read();
    }

    @Test
    public void test() throws IOException {
        Consumer<String> consumer = s -> {
            System.out.println("file come:" + s);
        };
        List<String> watchList = Lists.newArrayList("/home/work/log/test/");
        FileAlterationMonitor monitor = new FileAlterationMonitor(5000);
        log.info("agent monitor files:{}", new Gson().toJson(watchList));
        for (String watch : watchList) {
            File watchFile = new File(watch);
//            if (!watchFile.exists()) {
//                log.error("##!!## agent monitor file not exists:{}, filePattern:{}", watch, filePattern);
//                continue;
//            }

//            FileAlterationObserver observer = new FileAlterationObserver(new File(watch));
            FileAlterationObserver observer = new LogFileAlterationObserver(new File(watch), file -> file.exists() && file.isFile());
            observer.addListener(new FileListener(consumer));

            log.info("## agent monitor file:{}", watch);
            monitor.addObserver(observer);
        }

        try {
            monitor.start();
            log.info("## agent monitor started");
        } catch (Exception e) {
            log.error(String.format("agent file monitor start err,monitor filePattern:%s"), e);
        }
//        System.in.read();
    }
}
