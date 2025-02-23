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
package org.apache.ozhera.log.stream.job.extension.impl;

import com.xiaomi.mone.es.EsProcessor;
import org.apache.ozhera.log.stream.job.compensate.MqMessageDTO;
import org.apache.ozhera.log.stream.job.extension.MessageSender;
import org.apache.ozhera.log.stream.job.extension.MqMessageProduct;
import org.apache.ozhera.log.utils.DateUtils;

import java.util.Map;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2022/8/22 15:36
 */
public class EsMessageSender implements MessageSender {

    private EsProcessor esProcessor;

    private final String index;

    /**
     * Compensating message MQ queue.
     */
    private MqMessageProduct compensateMsgProduct;

    public EsMessageSender(String index, MqMessageProduct compensateMsgProduct) {
        this.index = index;
        this.compensateMsgProduct = compensateMsgProduct;
    }

    public EsProcessor getEsProcessor() {
        return esProcessor;
    }

    public void setEsProcessor(EsProcessor esProcessor) {
        this.esProcessor = esProcessor;
    }

    @Override
    public Boolean send(Map<String, Object> data) throws Exception {
        String time = DateUtils.getTime();
        String esIndex = index + "-" + time;
        esProcessor.bulkInsert(esIndex, data);
        return true;
    }

    @Override
    public boolean compensateSend(MqMessageDTO compensateMsg) {
        if (null != compensateMsgProduct) {
            compensateMsgProduct.product(compensateMsg);
            return true;
        }

        return false;
    }
}
