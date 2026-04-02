-- Chat message table for storing conversation history
-- Used by both Gateway (query) and Worker (read/write)

CREATE TABLE IF NOT EXISTS `hera_mind_chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `username` VARCHAR(64) NOT NULL COMMENT 'Username of the conversation owner',
    `role` VARCHAR(16) NOT NULL COMMENT 'Message role: USER or ASSISTANT',
    `content` TEXT NOT NULL COMMENT 'Message content',
    `created_at` BIGINT NOT NULL COMMENT 'Creation timestamp in milliseconds',
    PRIMARY KEY (`id`),
    INDEX `idx_username_time` (`username`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat message history';
