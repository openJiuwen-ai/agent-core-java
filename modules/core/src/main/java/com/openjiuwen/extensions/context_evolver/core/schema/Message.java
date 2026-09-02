/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.schema.message.Message}.
 * Chat message with role, content, and optional metadata.
 * 
 * @since 0.1.7
 */
public class Message {
    private final Role role;
    private final String content;
    private final Map<String, Object> metadata;

    /**
     * Message.
     * 
     * @param role role
     * @param content content
     * @since 0.1.7
     */
    public Message(Role role, String content) {
        this(role, content, new HashMap<>());
    }

    /**
     * Message.
     * 
     * @param role role
     * @param content content
     * @param metadata metadata
     * @since 0.1.7
     */
    public Message(Role role, String content, Map<String, Object> metadata) {
        this.role = role;
        this.content = content;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * getRole.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Role getRole() {
        return role;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContent() {
        return content;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * Convert to dictionary.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("role", role.getValue());
        dict.put("content", content);
        dict.put("metadata", metadata);
        return dict;
    }

    /**
     * Create from dictionary.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    public static Message fromDict(Map<String, Object> data) {
        Role role = Role.fromValue((String) data.get("role"));
        String content = (String) data.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        return new Message(role, content, metadata);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        String preview = content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content;
        return "Message(role=" + role + ", content='" + preview + "')";
    }
}
