/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for the event schema model.
 *
 * <p>Mirrors Python's {@code EventType}, {@code Event}, {@code InputEvent},
 * {@code TaskInteractionEvent}, {@code TaskCompletionEvent},
 * {@code TaskFailedEvent}, and {@code FollowUpEvent} in
 * {@code openjiuwen/core/controller/schema/event.py}.</p>
 */
class EventSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void eventTypeUsesPythonWireValues() throws Exception {
        assertThat(EventType.INPUT.getValue()).isEqualTo("input");
        assertThat(EventType.TASK_INTERACTION.getValue()).isEqualTo("task_interaction");
        assertThat(EventType.TASK_COMPLETION.getValue()).isEqualTo("task_completion");
        assertThat(EventType.TASK_FAILED.getValue()).isEqualTo("task_failed");
        assertThat(EventType.FOLLOW_UP.getValue()).isEqualTo("follow_up");

        assertThat(EventType.fromValue("task_interaction")).isEqualTo(EventType.TASK_INTERACTION);
        assertThat(EventType.fromValue(null)).isNull();
        assertThat(mapper.writeValueAsString(EventType.FOLLOW_UP)).isEqualTo("\"follow_up\"");
        assertThat(mapper.readValue("\"task_failed\"", EventType.class)).isEqualTo(EventType.TASK_FAILED);
    }

    @Test
    void eventAppliesPythonDefaultsAndMetadataPostInitIntent() throws Exception {
        Event event = new Event(EventType.INPUT);

        assertThat(event.getEventType()).isEqualTo(EventType.INPUT);
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getMetadata()).isEmpty();

        event.setMetadata(Map.of("source", "unit"));
        assertThat(event.getMetadata()).containsEntry("source", "unit");
        event.setMetadata(null);
        assertThat(event.getMetadata()).isEmpty();

        Event restored = mapper.readValue(
                """
                {
                  "event_type": "task_completion",
                  "event_id": "fixed-id",
                  "metadata": null
                }
                """,
                Event.class
        );
        assertThat(restored.getEventType()).isEqualTo(EventType.TASK_COMPLETION);
        assertThat(restored.getEventId()).isEqualTo("fixed-id");
        assertThat(restored.getMetadata()).isEmpty();
    }

    @Test
    void inputEventFromUserInputMatchesPythonConversions() {
        InputEvent existing = new InputEvent();
        assertSame(existing, InputEvent.fromUserInput(existing));

        InputEvent fromText = InputEvent.fromUserInput("hello");
        assertThat(fromText.getEventType()).isEqualTo(EventType.INPUT);
        assertThat(fromText.getInputData()).containsExactly(new DataFrame.TextDataFrame("hello"));

        Map<String, Object> payload = Map.of("answer", 42);
        InputEvent fromMap = InputEvent.fromUserInput(payload);
        assertThat(fromMap.getInputData()).containsExactly(new DataFrame.JsonDataFrame(payload));

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("node", "value");
        InputEvent fromInteractiveInput = InputEvent.fromUserInput(interactiveInput);
        assertThat(fromInteractiveInput.getInputData()).hasSize(1);
        DataFrame.JsonDataFrame frame = (DataFrame.JsonDataFrame) fromInteractiveInput.getInputData().get(0);
        assertThat(frame.data()).containsEntry("query", interactiveInput);

        assertThatThrownBy(() -> InputEvent.fromUserInput(List.of("unsupported")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported user input type");
    }

    @Test
    void taskEventSubclassesApplyPythonEventTypesAndDefaultLists() {
        Task task = new Task("session", "task", "worker");

        TaskInteractionEvent interactionEvent = new TaskInteractionEvent(null, task);
        assertThat(interactionEvent.getEventType()).isEqualTo(EventType.TASK_INTERACTION);
        assertThat(interactionEvent.getInteraction()).isEmpty();
        assertThat(interactionEvent.getTask()).isSameAs(task);

        TaskCompletionEvent completionEvent = new TaskCompletionEvent(
                List.of(new DataFrame.TextDataFrame("done")),
                task
        );
        assertThat(completionEvent.getEventType()).isEqualTo(EventType.TASK_COMPLETION);
        assertThat(completionEvent.getTaskResult()).containsExactly(new DataFrame.TextDataFrame("done"));
        assertThat(completionEvent.getTask()).isSameAs(task);

        TaskFailedEvent failedEvent = new TaskFailedEvent("boom", task);
        assertThat(failedEvent.getEventType()).isEqualTo(EventType.TASK_FAILED);
        assertThat(failedEvent.getErrorMessage()).isEqualTo("boom");
        assertThat(failedEvent.getTask()).isSameAs(task);
    }

    @Test
    void followUpEventFromTextCreatesTextDataFrame() {
        FollowUpEvent event = FollowUpEvent.fromText("next");

        assertThat(event.getEventType()).isEqualTo(EventType.FOLLOW_UP);
        assertThat(event.getInputData()).containsExactly(new DataFrame.TextDataFrame("next"));
    }

    @Test
    void jacksonSerializationUsesPythonFieldNames() throws Exception {
        InputEvent inputEvent = InputEvent.fromUserInput("hello");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(mapper.writeValueAsString(inputEvent), Map.class);

        assertThat(payload).containsEntry("event_type", "input")
                .containsKey("event_id")
                .containsKey("input_data")
                .containsEntry("metadata", Map.of());
        assertThat(payload).doesNotContainKeys("eventType", "eventId", "inputData");

        TaskFailedEvent failedEvent = new TaskFailedEvent("boom", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> failedPayload = mapper.readValue(mapper.writeValueAsString(failedEvent), Map.class);

        assertThat(failedPayload).containsEntry("event_type", "task_failed")
                .containsEntry("error_message", "boom");
        assertThat(failedPayload).doesNotContainKeys("eventType", "errorMessage");
    }
}
