/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Request object for adding a message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAddRequest {
    private String userId;
    private String scopeId;
    private String content;
    private String role;
    private String sessionId;
    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

    public static MessageAddRequestBuilder builder() {
        return new MessageAddRequestBuilder();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public static final class MessageAddRequestBuilder {
        private String userId;
        private String scopeId;
        private String content;
        private String role;
        private String sessionId;
        private OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

        public MessageAddRequestBuilder userId(String userId) { this.userId = userId; return this; }
        public MessageAddRequestBuilder scopeId(String scopeId) { this.scopeId = scopeId; return this; }
        public MessageAddRequestBuilder content(String content) { this.content = content; return this; }
        public MessageAddRequestBuilder role(String role) { this.role = role; return this; }
        public MessageAddRequestBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public MessageAddRequestBuilder timestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; return this; }

        public MessageAddRequest build() {
            return new MessageAddRequest(userId, scopeId, content, role, sessionId, timestamp);
        }
    }
}
