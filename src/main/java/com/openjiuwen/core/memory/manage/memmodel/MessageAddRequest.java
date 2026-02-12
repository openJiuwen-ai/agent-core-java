/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.time.Instant;
import java.util.Optional;

/**
 * Request object for adding a message.
 * <p>
 * Corresponds to Python: manage/mem_model/message_manager.py MessageAddRequest
 */
public record MessageAddRequest(
        Optional<String> userId,
        Optional<String> scopeId,
        Optional<String> content,
        Optional<String> role,
        Optional<String> sessionId,
        Instant timestamp
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String scopeId;
        private String content;
        private String role;
        private String sessionId;
        private Instant timestamp;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MessageAddRequest build() {
            return new MessageAddRequest(
                    Optional.ofNullable(userId),
                    Optional.ofNullable(scopeId),
                    Optional.ofNullable(content),
                    Optional.ofNullable(role),
                    Optional.ofNullable(sessionId),
                    timestamp != null ? timestamp : Instant.now()
            );
        }
    }
}

