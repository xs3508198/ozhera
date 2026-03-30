-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- User LLM Configuration Table
CREATE TABLE IF NOT EXISTS `user_llm_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `username` VARCHAR(128) NOT NULL COMMENT 'Username',
    `provider` VARCHAR(32) NOT NULL COMMENT 'LLM provider: openai, dashscope, ollama',
    `model` VARCHAR(64) NOT NULL COMMENT 'Model name, e.g., gpt-4o, qwen-max, llama3',
    `api_key` VARCHAR(512) DEFAULT NULL COMMENT 'API Key (encrypted)',
    `base_url` VARCHAR(256) DEFAULT NULL COMMENT 'Base URL for custom endpoints or Ollama',
    `is_default` TINYINT(1) DEFAULT 0 COMMENT 'Is default config: 0=no, 1=yes',
    `status` TINYINT DEFAULT 1 COMMENT 'Status: 0=disabled, 1=enabled',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    PRIMARY KEY (`id`),
    INDEX `idx_username` (`username`),
    INDEX `idx_username_provider` (`username`, `provider`),
    UNIQUE INDEX `uk_username_provider_model` (`username`, `provider`, `model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User LLM Configuration';
