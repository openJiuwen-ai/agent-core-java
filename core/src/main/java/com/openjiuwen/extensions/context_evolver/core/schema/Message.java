/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code Message} in
 * {@code openjiuwen/extensions/context_evolver/core/schema/message.py}.
 */
public class Message {

    private Role role;
    private String content;
    private Map<String, Object> metadata;

    public Message(Role role, String content) {
        this(role, content, null);
    }

    public Message(Role role, String content, Map<String, Object> metadata) {
        this.role = role;
        this.content = content;
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
    }

    public Map<String, Object> toDict() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("role", role.getValue());
        data.put("content", content);
        data.put("metadata", new LinkedHashMap<>(metadata));
        return data;
    }

    public static Message fromDict(Map<String, Object> data) {
        Role role = Role.fromValue((String) data.get("role"));
        String content = (String) data.get("content");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        return new Message(role, content, metadata);
    }

    @Override
    public String toString() {
        String preview = content != null && content.length() > 50
            ? content.substring(0, 50) + "..."
            : content;
        return "Message(role=" + role + ", content='" + preview + "')";
    }
}
