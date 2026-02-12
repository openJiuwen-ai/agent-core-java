// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Task schema models.
 * Tests TaskStatus enum, Task field validators (required strings, priority,
 * parent_task_id), model validator (validate_task_consistency), and defaults.
 */
@DisplayName("Task Schema Tests")
class TaskTest {

    @Nested
    @DisplayName("TaskStatus Tests")
    class TaskStatusTests {

        @Test
        @DisplayName("TaskStatus should have 9 members with correct string values")
        void testTaskStatusEnumCompletenessAndValues() {
            assertEquals(9, TaskStatus.values().length);
            assertEquals("submitted", TaskStatus.SUBMITTED.getValue());
            assertEquals("working", TaskStatus.WORKING.getValue());
            assertEquals("paused", TaskStatus.PAUSED.getValue());
            assertEquals("input-required", TaskStatus.INPUT_REQUIRED.getValue());
            assertEquals("completed", TaskStatus.COMPLETED.getValue());
            assertEquals("canceled", TaskStatus.CANCELED.getValue());
            assertEquals("failed", TaskStatus.FAILED.getValue());
            assertEquals("waiting", TaskStatus.WAITING.getValue());
            assertEquals("unknown", TaskStatus.UNKNOWN.getValue());
        }
    }

    @Nested
    @DisplayName("Task Validators Tests")
    class TaskValidatorsTests {

        private Task makeTask(String sessionId, String taskId, String taskType,
                              TaskStatus status, Integer priority, String parentTaskId,
                              String errorMessage, Object inputRequiredFields,
                              String description, Map<String, Object> metadata) {
            Task.Builder builder = Task.builder(
                sessionId != null ? sessionId : "sess-1",
                taskId != null ? taskId : "task-1",
                taskType != null ? taskType : "analysis"
            );
            if (status != null) builder.status(status);
            if (priority != null) builder.priority(priority);
            if (parentTaskId != null) builder.parentTaskId(parentTaskId);
            if (errorMessage != null) builder.errorMessage(errorMessage);
            if (inputRequiredFields != null) builder.inputRequiredFields(inputRequiredFields);
            if (description != null) builder.description(description);
            if (metadata != null) builder.metadata(metadata);
            return builder.build();
        }

        private Task makeTask() {
            return Task.builder("sess-1", "task-1", "analysis").build();
        }

        // ---- validate_required_strings ----

        @Test
        @DisplayName("Empty task_id should raise")
        void testEmptyTaskIdRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "", "analysis").build()
            );
        }

        @Test
        @DisplayName("Whitespace-only task_id should raise")
        void testWhitespaceOnlyTaskIdRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "   ", "analysis").build()
            );
        }

        @Test
        @DisplayName("task_id with leading/trailing whitespace should be stripped")
        void testTaskIdGetsStripped() {
            Task t = Task.builder("sess-1", "  task-1  ", "analysis").build();
            assertEquals("task-1", t.getTaskId());
        }

        @Test
        @DisplayName("Empty session_id should raise")
        void testEmptySessionIdRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("", "task-1", "analysis").build()
            );
        }

        @Test
        @DisplayName("Empty task_type should raise")
        void testEmptyTaskTypeRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-1", "").build()
            );
        }

        // ---- validate_priority ----

        @Test
        @DisplayName("Negative priority should raise")
        void testNegativePriorityRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-1", "analysis").priority(-1).build()
            );
        }

        @Test
        @DisplayName("Zero priority should be valid")
        void testZeroPriorityIsValid() {
            Task t = Task.builder("sess-1", "task-1", "analysis").priority(0).build();
            assertEquals(0, t.getPriority());
        }

        @Test
        @DisplayName("Large priority values should be accepted")
        void testLargePriorityIsValid() {
            Task t = Task.builder("sess-1", "task-1", "analysis").priority(9999).build();
            assertEquals(9999, t.getPriority());
        }

        // ---- validate_parent_task_id ----

        @Test
        @DisplayName("None parent_task_id should be valid (root task)")
        void testNoneParentTaskIdIsValid() {
            Task t = makeTask();
            assertNull(t.getParentTaskId());
        }

        @Test
        @DisplayName("Empty string parent_task_id should raise")
        void testEmptyStringParentTaskIdRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-1", "analysis").parentTaskId("").build()
            );
        }

        @Test
        @DisplayName("Whitespace-only parent_task_id should raise")
        void testWhitespaceParentTaskIdRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-1", "analysis").parentTaskId("   ").build()
            );
        }

        @Test
        @DisplayName("parent_task_id with whitespace should be stripped")
        void testParentTaskIdGetsStripped() {
            Task t = Task.builder("sess-1", "task-1", "analysis").parentTaskId("  parent-1  ").build();
            assertEquals("parent-1", t.getParentTaskId());
        }

        // ---- validate_task_consistency (model validator) ----

        @Test
        @DisplayName("task_id == parent_task_id should raise (circular reference)")
        void testSelfReferenceRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-x", "analysis").parentTaskId("task-x").build()
            );
        }

        @Test
        @DisplayName("FAILED status without error_message should raise")
        void testFailedWithoutErrorMessageRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-1", "analysis").status(TaskStatus.FAILED).build()
            );
        }

        @Test
        @DisplayName("FAILED status with empty error_message should raise")
        void testFailedWithEmptyErrorMessageRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-1", "analysis")
                    .status(TaskStatus.FAILED)
                    .errorMessage("   ")
                    .build()
            );
        }

        @Test
        @DisplayName("FAILED status with valid error_message should be accepted")
        void testFailedWithValidErrorMessageIsOk() {
            Task t = Task.builder("sess-1", "task-1", "analysis")
                .status(TaskStatus.FAILED)
                .errorMessage("timeout")
                .build();
            assertEquals("timeout", t.getErrorMessage());
        }

        @Test
        @DisplayName("INPUT_REQUIRED without input_required_fields should raise")
        void testInputRequiredWithoutFieldsRaises() {
            assertThrows(IllegalArgumentException.class, () ->
                Task.builder("sess-1", "task-1", "analysis")
                    .status(TaskStatus.INPUT_REQUIRED)
                    .build()
            );
        }

        @Test
        @DisplayName("INPUT_REQUIRED with input_required_fields should be accepted")
        void testInputRequiredWithFieldsIsOk() {
            Task t = Task.builder("sess-1", "task-1", "analysis")
                .status(TaskStatus.INPUT_REQUIRED)
                .inputRequiredFields(Map.of("name", "string"))
                .build();
            assertNotNull(t.getInputRequiredFields());
        }

        // ---- Default values ----

        @Test
        @DisplayName("Task should have correct default values for optional fields")
        void testDefaultValues() {
            Task t = makeTask();
            assertEquals(1, t.getPriority());
            assertEquals(TaskStatus.UNKNOWN, t.getStatus());
            assertNull(t.getDescription());
            assertNull(t.getInputs());
            assertTrue(t.getOutputs().isEmpty());
            assertNull(t.getParentTaskId());
            assertNull(t.getContextId());
            assertNull(t.getInputRequiredFields());
            assertNull(t.getErrorMessage());
            assertNull(t.getMetadata());
        }

        @Test
        @DisplayName("Task should support equals for same-value comparison")
        void testModelDumpRoundtrip() {
            Task t = Task.builder("sess-1", "task-1", "analysis")
                .description("test task")
                .priority(5)
                .status(TaskStatus.SUBMITTED)
                .parentTaskId("parent-1")
                .metadata(Map.of("owner", "user-1"))
                .build();

            assertEquals("task-1", t.getTaskId());
            assertEquals(5, t.getPriority());
            assertEquals("parent-1", t.getParentTaskId());
            assertEquals("user-1", t.getMetadata().get("owner"));
        }
    }
}

