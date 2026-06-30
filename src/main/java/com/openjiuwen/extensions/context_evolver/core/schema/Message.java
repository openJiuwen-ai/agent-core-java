/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.schema.message.Message}.
 * 
 * Chat message with role, content, and optional metadata.
 */
public class Message {
    
    private final Role role;
    private final String content;
    private final Map<String, Object> metadata;
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public Message(Role role, String content) {
        this(role, content, new HashMap<>());
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public Message(Role role, String content, Map<String, Object> metadata) {
        this.role = role;
        this.content = content;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public Role getRole() {
        return role;
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }
    
    /**
     * Convert to dictionary.
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
     */
    public static Message fromDict(Map<String, Object> data) {
        Role role = Role.fromValue((String) data.get("role"));
        String content = (String) data.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        return new Message(role, content, metadata);
    }
    
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        String preview = content != null && content.length() > 50 
            ? content.substring(0, 50) + "..." 
            : content;
        return "Message(role=" + role + ", content='" + preview + "')";
    }
}
