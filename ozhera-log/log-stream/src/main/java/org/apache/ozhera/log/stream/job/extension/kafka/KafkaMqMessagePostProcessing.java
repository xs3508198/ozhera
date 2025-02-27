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
package org.apache.ozhera.log.stream.job.extension.kafka;

import org.apache.ozhera.log.stream.common.LogStreamConstants;
import org.apache.ozhera.log.stream.job.SinkJobConfig;
import org.apache.ozhera.log.stream.job.extension.MqMessagePostProcessing;
import com.xiaomi.youpin.docean.anno.Service;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/12/1 18:20
 */
@Service(name = "kafka" + LogStreamConstants.postProcessingProviderBeanSuffix)
public class KafkaMqMessagePostProcessing implements MqMessagePostProcessing {
    @Override
    public void postProcessing(SinkJobConfig sinkJobConfig, String message) {

    }
}
