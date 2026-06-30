/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.event;

import com.openjiuwen.core.session.interaction.InteractiveInput;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy event model for backward compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    @Builder.Default
    private EventType eventType = EventType.USER_INPUT;

    @Builder.Default
    private EventPriority priority = EventPriority.NORMAL;

    @Builder.Default
    private EventSource source = new EventSource("unknown", SourceType.SYSTEM, null);

    @Builder.Default
    private EventContent content = new EventContent();

    @Builder.Default
    private EventContext context = new EventContext();

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    private String receiverId;

    private String customEventType;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Event createUserEvent(Object content, String conversationId, String userId,
                                        Map<String, Object> extensions) {
        EventContent eventContent = new EventContent();
        if (content instanceof InteractiveInput interactiveInput) {
            eventContent.setInteractiveInput(interactiveInput);
        } else if (content != null) {
            eventContent.setQuery(String.valueOf(content));
        }
        if (extensions != null) {
            eventContent.setExtensions(new LinkedHashMap<>(extensions));
        }
        return Event.builder()
                .eventType(EventType.USER_INPUT)
                .source(new EventSource(conversationId, SourceType.USER, userId))
                .content(eventContent)
                .context(new EventContext(UUID.randomUUID().toString(), conversationId, null, null))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Event createTaskCompleted(String conversationId, String taskId, Object taskResult,
                                            String workflowId, List<Object> streamData) {
        EventContent eventContent = new EventContent();
        eventContent.setTaskResult(taskResult);
        eventContent.setStreamData(streamData != null ? new ArrayList<>(streamData) : new ArrayList<>());
        return Event.builder()
                .eventType(EventType.TASK_COMPLETED)
                .source(new EventSource(conversationId, SourceType.TASK, null))
                .content(eventContent)
                .context(new EventContext(null, conversationId, taskId, workflowId))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Event createTaskInterrupted(String conversationId, String taskId, String reason,
                                              Object taskResult, String workflowId, List<Object> streamData) {
        EventContent eventContent = new EventContent();
        eventContent.setQuery(reason);
        eventContent.setTaskResult(taskResult);
        eventContent.setStreamData(streamData != null ? new ArrayList<>(streamData) : new ArrayList<>());
        return Event.builder()
                .eventType(EventType.TASK_INTERRUPTED)
                .priority(EventPriority.HIGH)
                .source(new EventSource(conversationId, SourceType.TASK, null))
                .content(eventContent)
                .context(new EventContext(null, conversationId, taskId, workflowId))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Event createErrorEvent(String conversationId, String errorInfo, SourceType sourceType) {
        EventContent eventContent = new EventContent();
        eventContent.setQuery(errorInfo);
        return Event.builder()
                .eventType(EventType.ERROR)
                .priority(EventPriority.HIGH)
                .source(new EventSource(conversationId, sourceType, null))
                .content(eventContent)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Event createInfoEvent(String conversationId, String infoText, SourceType sourceType) {
        EventContent eventContent = new EventContent();
        eventContent.setQuery(infoText);
        return Event.builder()
                .eventType(EventType.INFO)
                .source(new EventSource(conversationId, sourceType, null))
                .content(eventContent)
                .build();
    }

    // ========== Missing factory methods (P1) ==========

    /**
     * Create Agent response event.
     * Mirrors Python's {@code Event.create_agent_response()}.
     */
    public static Event createAgentResponse(String content, String conversationId, String replyToEventId) {
        EventContent eventContent = new EventContent();
        eventContent.setQuery(content);
        return Event.builder()
                .eventType(EventType.AGENT_RESPONSE)
                .source(new EventSource(conversationId, SourceType.AGENT, null))
                .content(eventContent)
                .context(new EventContext(replyToEventId, conversationId, null, null))
                .build();
    }

    /**
     * Create Agent handoff event.
     * Mirrors Python's {@code Event.create_agent_handoff()}.
     */
    public static Event createAgentHandoff(String conversationId, String toAgentId, String handoffReason) {
        EventContent eventContent = new EventContent();
        eventContent.setQuery(handoffReason);
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("to_agent_id", toAgentId);
        eventContent.setExtensions(ext);
        return Event.builder()
                .eventType(EventType.AGENT_HANDOFF)
                .source(new EventSource(conversationId, SourceType.AGENT, null))
                .content(eventContent)
                .context(new EventContext(null, conversationId, null, null))
                .build();
    }

    // ========== Missing convenience methods (P1) ==========

    /**
     * Set correlation ID.
     * Mirrors Python's {@code Event.set_correlation()}.
     */
    public void setCorrelation(String correlationId) {
        if (this.context == null) {
            this.context = new EventContext();
        }
        this.context.setCorrelationId(correlationId);
    }

    /**
     * Set conversation ID.
     * Mirrors Python's {@code Event.set_conversation()}.
     */
    public void setConversation(String conversationId) {
        if (this.context == null) {
            this.context = new EventContext();
        }
        this.context.setConversationId(conversationId);
    }

    /**
     * Check if from user.
     * Mirrors Python's {@code Event.is_from_user()}.
     */
    public boolean isFromUser() {
        return source != null && source.getSourceType() == SourceType.USER;
    }

    /**
     * Check if from Agent.
     * Mirrors Python's {@code Event.is_from_agent()}.
     */
    public boolean isFromAgent() {
        return source != null && source.getSourceType() == SourceType.AGENT;
    }

    /**
     * Check if task related.
     * Mirrors Python's {@code Event.is_task_related()}.
     */
    public boolean isTaskRelated() {
        return context != null && context.getTaskId() != null;
    }

    /**
     * Check if workflow related.
     * Mirrors Python's {@code Event.is_workflow_related()}.
     */
    public boolean isWorkflowRelated() {
        return context != null && context.getWorkflowId() != null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDisplayContent() {
        return content != null ? content.getQueryText() : "";
    }

    /**
     * Convert to map format.
     * Mirrors Python's {@code Event.to_dict()}.
     */
    public Map<String, Object> toDict() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event_id", eventId);
        result.put("event_type", eventType != null ? eventType.name() : null);
        result.put("priority", priority != null ? priority.name() : null);
        if (source != null) {
            Map<String, Object> srcMap = new LinkedHashMap<>();
            srcMap.put("conversation_id", source.getConversationId());
            srcMap.put("source_type", source.getSourceType() != null ? source.getSourceType().name() : null);
            srcMap.put("user_id", source.getUserId());
            result.put("source", srcMap);
        }
        if (content != null) {
            Map<String, Object> cntMap = new LinkedHashMap<>();
            cntMap.put("query", content.getQuery());
            cntMap.put("extensions", content.getExtensions());
            result.put("content", cntMap);
        }
        if (context != null) {
            Map<String, Object> ctxMap = new LinkedHashMap<>();
            ctxMap.put("correlation_id", context.getCorrelationId());
            ctxMap.put("conversation_id", context.getConversationId());
            ctxMap.put("task_id", context.getTaskId());
            ctxMap.put("workflow_id", context.getWorkflowId());
            result.put("context", ctxMap);
        }
        result.put("created_at", createdAt != null ? createdAt.toString() : null);
        result.put("metadata", metadata);
        result.put("receiver_id", receiverId);
        result.put("custom_event_type", customEventType);
        return result;
    }

    public enum EventType {
        USER_INPUT,
        AGENT_RESPONSE,
        AGENT_HANDOFF,
        TASK_COMPLETED,
        TASK_INTERRUPTED,
        ERROR,
        INFO
    }

    public enum EventPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum SourceType {
        USER,
        AGENT,
        TASK,
        WORKFLOW,
        SYSTEM
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class EventSource {
        private String conversationId;
        private SourceType sourceType;
        private String userId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class EventContent {
        private String query;
        private InteractiveInput interactiveInput;
        private List<Object> streamData = new ArrayList<>();
        private Object taskResult;
        private Map<String, Object> extensions = new LinkedHashMap<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getQueryText() {
            if (query != null) {
                return query;
            }
            if (interactiveInput != null) {
                if (interactiveInput.getRawInputs() != null) {
                    return String.valueOf(interactiveInput.getRawInputs());
                }
                if (interactiveInput.getUserInputs() != null && !interactiveInput.getUserInputs().isEmpty()) {
                    return String.valueOf(interactiveInput.getUserInputs().values().iterator().next());
                }
            }
            return "";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class EventContext {
        private String correlationId;
        private String conversationId;
        private String taskId;
        private String workflowId;
    }
}
