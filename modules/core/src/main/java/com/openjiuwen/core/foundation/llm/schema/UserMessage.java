/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

/**
 * User message in an LLM conversation.
 * <p>
 * Mirrors Python's {@code UserMessage} model.
 * 
 * @since 0.1.7
 */
public class UserMessage extends BaseMessage {
    /**
     * UserMessage.
     * 
     * @since 0.1.7
     */
    public UserMessage() {
    }

    /**
     * Creates a user message with the given content.
     * 
     * @param content the message content
     * @since 0.1.7
     */
    public UserMessage(String content) {
        super("user", content);
    }

    /**
     * Creates a user message with the given content and name.
     * 
     * @param content the message content
     * @param name the sender name
     * @since 0.1.7
     */
    public UserMessage(String content, String name) {
        this(content);
        setName(name);
    }

    /**
     * getRole.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "user";
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder extends BaseMessage.Builder {
        /**
         * role.
         * 
         * @param role role
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder role(String role) {
            super.role(role);
            return this;
        }

        /**
         * content.
         * 
         * @param content content
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder content(Object content) {
            super.content(content);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * metadata.
         * 
         * @param metadata metadata
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder metadata(java.util.Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
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
