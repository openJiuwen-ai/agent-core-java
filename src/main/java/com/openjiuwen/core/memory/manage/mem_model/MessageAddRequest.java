/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;

/**
 * Mirrors Python's {@code MessageAddRequest} in
 * {@code openjiuwen/core/memory/manage/mem_model/message_manager.py}.
 */
public class MessageAddRequest {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("scope_id")
    private String scopeId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("role")
    private String role;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("timestamp")
    private ZonedDateTime timestamp = ZonedDateTime.now();

    public MessageAddRequest() {
    }

    private MessageAddRequest(Builder builder) {
        this.userId = builder.userId;
        this.scopeId = builder.scopeId;
        this.content = builder.content;
        this.role = builder.role;
        this.sessionId = builder.sessionId;
        this.timestamp = builder.timestampSet ? builder.timestamp : ZonedDateTime.now();
    }

    public static Builder builder() {
        return new Builder();
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

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public static final class Builder {
        private String userId;
        private String scopeId;
        private String content;
        private String role;
        private String sessionId;
        private ZonedDateTime timestamp;
        private boolean timestampSet;

        private Builder() {
        }

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

        public Builder timestamp(ZonedDateTime timestamp) {
            this.timestamp = timestamp;
            this.timestampSet = true;
            return this;
        }

        public MessageAddRequest build() {
            return new MessageAddRequest(this);
        }
    }
}
