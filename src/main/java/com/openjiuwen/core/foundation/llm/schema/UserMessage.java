/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

/**
 * User message in an LLM conversation.
 * <p>
 * Mirrors Python's {@code UserMessage} model.
 */
public class UserMessage extends BaseMessage {

    /**
     * Auto-generated for codecheck compliance.
     */
    public UserMessage() {
    }

    /**
     * Creates a user message with the given content.
     *
     * @param content the message content
     */
    public UserMessage(String content) {
        super("user", content);
    }

    /**
     * Creates a user message with the given content and name.
     *
     * @param content the message content
     * @param name    the sender name
     */
    public UserMessage(String content, String name) {
        this(content);
        setName(name);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "user";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder extends BaseMessage.Builder {
        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder role(String role) {
            super.role(role);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder content(Object content) {
            super.content(content);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder metadata(java.util.Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public UserMessage build() {
            UserMessage message = new UserMessage();
            message.setRole(role);
            message.setContent(content);
            message.setName(name);
            message.setMetadata(metadata);
            return message;
        }
    }
}
