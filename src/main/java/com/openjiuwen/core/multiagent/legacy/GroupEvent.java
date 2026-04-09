  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.multiagent.legacy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event class for agent group message routing.
 * <p>
 * Mirrors the subset of Python's legacy {@code Event} used by the multi_agent module,
 * including content, context, source, receiver_id, and custom_event_type fields.
 * <p>
 * This is a simplified event tailored for group routing; it does not replace
 * the controller module's {@link com.openjiuwen.core.controller.schema.Event}.
 *
 * @deprecated Legacy event for backward compatibility with ControllerGroup pattern.
 */
@Deprecated
public class GroupEvent {

    private String eventId;
    private String query;
    private Object queryPayload;
    private String conversationId;
    private String userId;
    private String receiverId;
    private String customEventType;
    private Map<String, Object> metadata;

    public GroupEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
    }

    // ========== Factory methods ==========

    /**
     * Create a user event from content string.
     *
     * @param content        text content
     * @param conversationId conversation ID
     * @return GroupEvent instance
     */
    public static GroupEvent createUserEvent(String content, String conversationId) {
        return createUserEvent(content, conversationId, null);
    }

    /**
     * Create a user event from content string with user ID.
     *
     * @param content        text content
     * @param conversationId conversation ID
     * @param userId         user ID (nullable)
     * @return GroupEvent instance
     */
    public static GroupEvent createUserEvent(String content, String conversationId, String userId) {
        GroupEvent event = new GroupEvent();
        event.query = content;
        event.queryPayload = content;
        event.conversationId = conversationId != null ? conversationId : "default";
        event.userId = userId;
        return event;
    }

    /**
     * Create a GroupEvent from a Map (backward compatibility).
     *
     * @param map input map with "content"/"query", "conversation_id", "user_id" keys
     * @return GroupEvent instance
     */
    @SuppressWarnings("unchecked")
    public static GroupEvent fromMap(Map<String, Object> map) {
        GroupEvent event = new GroupEvent();
        Object content = map.get("content");
        if (content == null) {
            content = map.get("query");
        }
        event.query = content != null ? content.toString() : "";
        event.queryPayload = content;
        Object convId = map.get("conversation_id");
        event.conversationId = convId != null ? convId.toString() : "default_session";
        Object uid = map.get("user_id");
        event.userId = uid != null ? uid.toString() : null;
        Object receiverId = map.get("receiver_id");
        event.receiverId = receiverId != null ? receiverId.toString() : null;
        Object customEventType = map.get("custom_event_type");
        event.customEventType = customEventType != null ? customEventType.toString() : null;
        event.metadata = new HashMap<>(map);
        return event;
    }

    // ========== Accessors ==========

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
        this.queryPayload = query;
    }

    public Object getQueryPayload() {
        return queryPayload;
    }

    public void setQueryPayload(Object queryPayload) {
        this.queryPayload = queryPayload;
        this.query = queryPayload != null ? queryPayload.toString() : null;
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

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getCustomEventType() {
        return customEventType;
    }

    public void setCustomEventType(String customEventType) {
        this.customEventType = customEventType;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}
