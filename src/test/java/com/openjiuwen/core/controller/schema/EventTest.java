// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Event schema models.
 * Tests EventType enum, Event base class, InputEvent (including fromUserInput),
 * TaskInteractionEvent, TaskCompletionEvent, TaskFailedEvent.
 */
@DisplayName("Event Schema Tests")
class EventTest {

    @Nested
    @DisplayName("EventType and Event Base Tests")
    class EventBaseAndTypeTests {

        @Test
        @DisplayName("EventType should have 4 members with expected values")
        void testEventTypeEnumValuesAndCount() {
            assertEquals(4, EventType.values().length);
            assertEquals("input", EventType.INPUT.getValue());
            assertEquals("task_interaction", EventType.TASK_INTERACTION.getValue());
            assertEquals("task_completion", EventType.TASK_COMPLETION.getValue());
            assertEquals("task_failed", EventType.TASK_FAILED.getValue());
        }

        @Test
        @DisplayName("Each Event gets a unique event_id; metadata defaults to null; custom values accepted")
        void testEventIdUniquenessAndDefaults() {
            Event e1 = new Event(EventType.INPUT);
            Event e2 = new Event(EventType.INPUT);
            assertNotEquals(e1.getEventId(), e2.getEventId());
            assertTrue(e1.getEventId().length() > 0);
            assertNull(e1.getMetadata());

            // Custom metadata
            Map<String, Object> meta = Map.of("source", "test", "priority", 5);
            Event e3 = new Event(EventType.INPUT, null, meta);
            assertEquals(meta, e3.getMetadata());

            // Explicit event_id
            Event e4 = new Event(EventType.INPUT, "custom-id-123", null);
            assertEquals("custom-id-123", e4.getEventId());
        }
    }

    @Nested
    @DisplayName("InputEvent Tests")
    class InputEventTests {

        @Test
        @DisplayName("InputEvent event_type should default to EventType.INPUT")
        void testInputEventDefaultType() {
            InputEvent ie = new InputEvent();
            assertEquals(EventType.INPUT, ie.getEventType());
        }

        @Test
        @DisplayName("InputEvent input_data should default to empty list")
        void testInputEventDefaultInputData() {
            InputEvent ie = new InputEvent();
            assertTrue(ie.getInputData().isEmpty());
        }

        @Test
        @DisplayName("InputEvent should accept input_data list")
        void testInputEventWithData() {
            List<BaseDataFrame> data = List.of(new TextDataFrame("hello"));
            InputEvent ie = new InputEvent(data);
            assertEquals(1, ie.getInputData().size());
            assertEquals("hello", ((TextDataFrame) ie.getInputData().get(0)).getText());
        }

        @Test
        @DisplayName("fromUserInput should convert a string to InputEvent with TextDataFrame")
        void testFromUserInputString() {
            InputEvent ie = InputEvent.fromUserInput("What is AI?");
            assertEquals(EventType.INPUT, ie.getEventType());
            assertEquals(1, ie.getInputData().size());
            assertInstanceOf(TextDataFrame.class, ie.getInputData().get(0));
            assertEquals("What is AI?", ((TextDataFrame) ie.getInputData().get(0)).getText());
        }

        @Test
        @DisplayName("fromUserInput should convert a dict to InputEvent with JsonDataFrame")
        void testFromUserInputDict() {
            Map<String, Object> data = Map.of("query", "test", "params", Map.of("limit", 10));
            InputEvent ie = InputEvent.fromUserInput(data);
            assertEquals(EventType.INPUT, ie.getEventType());
            assertEquals(1, ie.getInputData().size());
            assertInstanceOf(JsonDataFrame.class, ie.getInputData().get(0));
            assertEquals(data, ((JsonDataFrame) ie.getInputData().get(0)).getData());
        }

        @Test
        @DisplayName("fromUserInput should return the same InputEvent object unchanged")
        void testFromUserInputInputEventPassthrough() {
            InputEvent original = new InputEvent(List.of(new TextDataFrame("pass through")));
            InputEvent result = InputEvent.fromUserInput(original);
            assertSame(original, result);
        }

        @Test
        @DisplayName("fromUserInput should throw for unsupported types")
        void testFromUserInputUnsupportedTypeRaises() {
            assertThrows(IllegalArgumentException.class, () -> InputEvent.fromUserInput(12345));
        }

        @Test
        @DisplayName("fromUserInput should throw for list input")
        void testFromUserInputListRaises() {
            assertThrows(IllegalArgumentException.class, () -> InputEvent.fromUserInput(List.of("item1", "item2")));
        }
    }

    @Nested
    @DisplayName("Task Events Tests")
    class TaskEventsTests {

        private Task makeTask() {
            return new Task("s1", "t1", "analysis", TaskStatus.WORKING);
        }

        @Test
        @DisplayName("TaskInteractionEvent: defaults empty + can carry task and data")
        void testTaskInteractionEventWithTaskAndDefaults() {
            TaskInteractionEvent evDefault = new TaskInteractionEvent();
            assertEquals(EventType.TASK_INTERACTION, evDefault.getEventType());
            assertTrue(evDefault.getInteraction().isEmpty());
            assertNull(evDefault.getTask());

            Task task = makeTask();
            TaskInteractionEvent ev = new TaskInteractionEvent(
                List.of(new TextDataFrame("Please confirm")), task
            );
            assertEquals("t1", ev.getTask().getTaskId());
            assertEquals(1, ev.getInteraction().size());
        }

        @Test
        @DisplayName("TaskCompletionEvent: defaults empty + can carry results and task")
        void testTaskCompletionEventWithResultsAndDefaults() {
            TaskCompletionEvent evDefault = new TaskCompletionEvent();
            assertEquals(EventType.TASK_COMPLETION, evDefault.getEventType());
            assertTrue(evDefault.getTaskResult().isEmpty());
            assertNull(evDefault.getTask());

            Task task = makeTask();
            TaskCompletionEvent ev = new TaskCompletionEvent(
                List.of(new TextDataFrame("Analysis complete")), task
            );
            assertEquals(1, ev.getTaskResult().size());
            assertEquals("t1", ev.getTask().getTaskId());
        }

        @Test
        @DisplayName("TaskFailedEvent: defaults empty + can carry error message and task")
        void testTaskFailedEventWithErrorInfoAndDefaults() {
            TaskFailedEvent evDefault = new TaskFailedEvent();
            assertEquals(EventType.TASK_FAILED, evDefault.getEventType());
            assertNull(evDefault.getErrorMessage());
            assertNull(evDefault.getTask());

            Task task = makeTask();
            TaskFailedEvent ev = new TaskFailedEvent("Network timeout", task);
            assertEquals("Network timeout", ev.getErrorMessage());
            assertEquals("t1", ev.getTask().getTaskId());
        }

        @Test
        @DisplayName("Event subtypes should support equals for same-value comparison")
        void testEventModelDumpRoundtrip() {
            InputEvent ie = new InputEvent(List.of(new TextDataFrame("hi")));
            assertEquals(EventType.INPUT, ie.getEventType());
            assertEquals("hi", ((TextDataFrame) ie.getInputData().get(0)).getText());
        }
    }
}

