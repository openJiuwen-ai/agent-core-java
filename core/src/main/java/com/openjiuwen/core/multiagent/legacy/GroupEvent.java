/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy;

/**
 * Legacy event envelope for controller groups.
 *
 * <p>Mirrors Python's legacy multi-agent group event in
 * {@code openjiuwen/core/multi_agent/legacy/group.py}.</p>
 */
public class GroupEvent {
    private final Object payload;
    private final String sessionId;
    private String customEventType;
    private Object queryPayload;
    private Object query;
    private String conversationId;
    private String userId;

    private GroupEvent(Object payload, String sessionId) {
        this.payload = payload;
        this.sessionId = sessionId;
    }

    public static GroupEvent createUserEvent(Object payload, String sessionId) {
        return new GroupEvent(payload, sessionId);
    }

    public Object getPayload() {
        return payload;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCustomEventType() {
        return customEventType;
    }

    public void setCustomEventType(String customEventType) {
        this.customEventType = customEventType;
    }

    public Object getQueryPayload() {
        return queryPayload;
    }

    public void setQueryPayload(Object queryPayload) {
        this.queryPayload = queryPayload;
    }

    public Object getQuery() {
        return query;
    }

    public void setQuery(Object query) {
        this.query = query;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
