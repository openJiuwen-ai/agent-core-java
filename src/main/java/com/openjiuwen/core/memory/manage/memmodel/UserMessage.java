/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.util.Objects;

/**
 * User message entity.
 * Corresponds to Python: manage/mem_model/message.py - UserMessage
 *
 * <p>Python equivalent:
 * <pre>
 * class UserMessage(MessageMixin, Base):
 *     __tablename__ = "user_message"
 * </pre>
 */
public class UserMessage implements MessageBase {

    private static final String TABLE_NAME = "user_message";

    private final String messageId;
    private final String userId;
    private final String scopeId;
    private final String content;
    private final String sessionId;
    private final String role;
    private final String timestamp;

    private UserMessage(Builder builder) {
        this.messageId = builder.messageId;
        this.userId = builder.userId;
        this.scopeId = builder.scopeId;
        this.content = builder.content;
        this.sessionId = builder.sessionId;
        this.role = builder.role;
        this.timestamp = builder.timestamp;
    }

    public static String getTableName() {
        return TABLE_NAME;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getUserId() {
        return userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getContent() {
        return content;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRole() {
        return role;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String messageId;
        private String userId;
        private String scopeId;
        private String content;
        private String sessionId;
        private String role;
        private String timestamp;

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
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

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public UserMessage build() {
            Objects.requireNonNull(messageId, "messageId is required");
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(scopeId, "scopeId is required");
            Objects.requireNonNull(content, "content is required");
            return new UserMessage(this);
        }
    }

    @Override
    public String toString() {
        return "UserMessage{" +
               "messageId='" + messageId + '\'' +
               ", userId='" + userId + '\'' +
               ", scopeId='" + scopeId + '\'' +
               ", content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
               '}';
    }
}

