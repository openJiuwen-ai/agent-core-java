/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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

    public static Event createInfoEvent(String conversationId, String infoText, SourceType sourceType) {
        EventContent eventContent = new EventContent();
        eventContent.setQuery(infoText);
        return Event.builder()
                .eventType(EventType.INFO)
                .source(new EventSource(conversationId, sourceType, null))
                .content(eventContent)
                .build();
    }

    public String getDisplayContent() {
        return content != null ? content.getQueryText() : "";
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
    public static class EventSource {
        private String conversationId;
        private SourceType sourceType;
        private String userId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventContent {
        private String query;
        private InteractiveInput interactiveInput;
        private List<Object> streamData = new ArrayList<>();
        private Object taskResult;
        private Map<String, Object> extensions = new LinkedHashMap<>();

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
    public static class EventContext {
        private String correlationId;
        private String conversationId;
        private String taskId;
        private String workflowId;
    }
}
