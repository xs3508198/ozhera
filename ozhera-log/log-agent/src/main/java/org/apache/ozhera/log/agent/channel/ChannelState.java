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
package org.apache.ozhera.log.agent.channel;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author shanwb
 * @date 2021-08-26
 */
@Data
public class ChannelState implements Serializable {

    private Long tailId;

    private String tailName;

    private Long appId;

    private String appName;

    private String logPattern;
    /**
     * Generated by appId + logPattern
     */
    private String logPatternCode;
    /**
     * Total number of collected and sent rows.
     */
    private Long totalSendCnt;

    private List<String> ipList;

    private Long collectTime;

    private Map<String, StateProgress> stateProgressMap;

    @Data
    public static class StateProgress implements Serializable {
        /**
         * ip
         */
        private String ip;
        /**
         * Current collection file
         */
        private String currentFile;

        private String fileInode;
        /**
         * The latest line number currently being collected.
         */
        private Long currentRowNum;
        /**
         * The latest character symbol currently being collected.
         */
        private Long pointer;

        /**
         * The maximum character count of the current file.
         */
        private Long fileMaxPointer;

        /**
         * Collection time
         */
        private Long ctTime;
    }
}
