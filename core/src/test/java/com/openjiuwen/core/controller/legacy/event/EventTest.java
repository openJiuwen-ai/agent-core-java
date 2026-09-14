/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests legacy event model factories and serialization.
 *
 * <p>Mirrors Python's {@code Event} in
 * {@code openjiuwen/core/controller/legacy/event/event.py}.</p>
 */
class EventTest {

    @Test
    void createUserEventStoresQuerySourceContextAndExtensions() {
        Event event = Event.createUserEvent(
                "hello",
                "conv-1",
                "user-1",
                Map.of("extra", 42)
        );

        assertThat(event.getEventType()).isEqualTo(Event.EventType.USER_INPUT);
        assertThat(event.getSource().getConversationId()).isEqualTo("conv-1");
        assertThat(event.getSource().getSourceType()).isEqualTo(Event.SourceType.USER);
        assertThat(event.getSource().getUserId()).isEqualTo("user-1");
        assertThat(event.getContent().getQuery()).isEqualTo("hello");
        assertThat(event.getContent().getQueryText()).isEqualTo("hello");
        assertThat(event.getContent().getExtensions()).containsEntry("extra", 42);
        assertThat(event.getContext().getConversationId()).isEqualTo("conv-1");
        assertThat(event.getContext().getCorrelationId()).isNotBlank();
    }

    @Test
    void taskInterruptedUsesHighPriorityAndTaskContext() {
        Event event = Event.createTaskInterrupted(
                "conv-1",
                "task-1",
                "paused",
                Map.of("ok", false),
                "wf-1",
                List.of("chunk")
        );

        assertThat(event.getEventType()).isEqualTo(Event.EventType.TASK_INTERRUPTED);
        assertThat(event.getPriority()).isEqualTo(Event.EventPriority.HIGH);
        assertThat(event.getContent().getQuery()).isEqualTo("paused");
        assertThat(event.getContent().getStreamData()).containsExactly("chunk");
        assertThat(event.getContext().getTaskId()).isEqualTo("task-1");
        assertThat(event.getContext().getWorkflowId()).isEqualTo("wf-1");
        assertThat(event.isTaskRelated()).isTrue();
        assertThat(event.isWorkflowRelated()).isTrue();
    }

    @Test
    void helperMethodsAndToDictPreservePythonEnumValues() {
        Event event = Event.createAgentResponse("done", "conv-1", "corr-1");
        event.setReceiverId("agent-2");
        event.setCustomEventType("custom");
        event.setConversation("conv-2");
        event.setCorrelation("corr-2");

        Map<String, Object> value = event.toDict();
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) value.get("source");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) value.get("context");

        assertThat(event.isFromAgent()).isTrue();
        assertThat(event.getDisplayContent()).isEqualTo("done");
        assertThat(value).containsEntry("event_type", "agent_response");
        assertThat(value).containsEntry("priority", 2);
        assertThat(source).containsEntry("source_type", "single_agent");
        assertThat(context).containsEntry("conversation_id", "conv-2");
        assertThat(context).containsEntry("correlation_id", "corr-2");
        assertThat(value).containsEntry("receiver_id", "agent-2");
        assertThat(value).containsEntry("custom_event_type", "custom");
    }
}
