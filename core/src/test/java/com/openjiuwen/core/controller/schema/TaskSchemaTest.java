/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for the task schema model.
 *
 * <p>Mirrors Python's {@code Task} and {@code TaskStatus} in
 * {@code openjiuwen/core/controller/schema/task.py}.</p>
 */
class TaskSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void taskStatusUsesPythonWireValues() throws Exception {
        assertThat(TaskStatus.SUBMITTED.getValue()).isEqualTo("submitted");
        assertThat(TaskStatus.INPUT_REQUIRED.getValue()).isEqualTo("input-required");
        assertThat(TaskStatus.fromValue("input-required")).isEqualTo(TaskStatus.INPUT_REQUIRED);
        assertThat(TaskStatus.fromValue("INPUT_REQUIRED")).isEqualTo(TaskStatus.INPUT_REQUIRED);
        assertThat(mapper.writeValueAsString(TaskStatus.CANCELED)).isEqualTo("\"canceled\"");
        assertThat(mapper.readValue("\"failed\"", TaskStatus.class)).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void constructorsAndSettersApplyPythonValidators() {
        Task task = new Task(" session ", " task ", " type ");

        assertThat(task.getSessionId()).isEqualTo("session");
        assertThat(task.getTaskId()).isEqualTo("task");
        assertThat(task.getTaskType()).isEqualTo("type");
        assertThat(task.getPriority()).isEqualTo(1);
        assertThat(task.getOutputs()).isEmpty();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.UNKNOWN);

        assertThatThrownBy(() -> new Task(" ", "task", "type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task field cannot be empty");
        assertThatThrownBy(() -> task.setPriority(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Priority must be a non-negative integer");
        assertThatThrownBy(() -> task.setParentTaskId(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent_task_id cannot be an empty string");
        assertThatThrownBy(() -> task.setOutputs(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputs cannot be null");
    }

    @Test
    void taskConsistencyValidationMatchesStatusRequirements() {
        Task selfParent = new Task("s", "same", "type");
        selfParent.setParentTaskId("same");
        assertThatThrownBy(selfParent::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("circular reference");

        Task failed = new Task("s", "failed", "type");
        failed.setStatus(TaskStatus.FAILED);
        assertThatThrownBy(failed::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error_message is required when status is FAILED");

        Task inputRequired = new Task("s", "input", "type");
        inputRequired.setStatus(TaskStatus.INPUT_REQUIRED);
        assertThatThrownBy(inputRequired::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input_required_fields is required when status is INPUT_REQUIRED");

        inputRequired.setInputRequiredFields(Map.of("field", "value"));
        inputRequired.validate();
    }

    @Test
    void mapRoundTripPreservesPythonFieldNamesAndCollections() {
        InputEvent input = InputEvent.fromUserInput("hello");
        ControllerOutputChunk output = new ControllerOutputChunk(
                2,
                ControllerOutputPayload.allTasksProcessed("done"),
                true
        );
        Task task = new Task("s1", "t1", "worker");
        task.setDescription("demo");
        task.setPriority(3);
        task.setInputs(List.of(input));
        task.setOutputs(List.of(output));
        task.setStatus(TaskStatus.INPUT_REQUIRED);
        task.setParentTaskId("parent");
        task.setContextId("ctx");
        task.setInputRequiredFields(Map.of("answer", "string"));
        task.setMetadata(Map.of("user_id", "u1"));
        task.setExtensions(Map.of("trace", 7));

        Map<String, Object> dumped = task.toMap();

        assertThat(dumped).containsEntry("session_id", "s1")
                .containsEntry("task_id", "t1")
                .containsEntry("task_type", "worker")
                .containsEntry("status", "input-required")
                .containsEntry("parent_task_id", "parent")
                .containsEntry("context_id", "ctx")
                .containsKey("input_required_fields")
                .containsKey("inputs")
                .containsKey("outputs");

        Task restored = Task.fromMap(dumped);

        assertThat(restored.getSessionId()).isEqualTo("s1");
        assertThat(restored.getTaskId()).isEqualTo("t1");
        assertThat(restored.getTaskType()).isEqualTo("worker");
        assertThat(restored.getInputs()).containsExactly(input);
        assertThat(restored.getOutputs()).containsExactly(output);
        assertThat(restored.getStatus()).isEqualTo(TaskStatus.INPUT_REQUIRED);
        assertThat(restored.getInputRequiredFields()).isEqualTo(Map.of("answer", "string"));
        assertThat(restored.getMetadata()).containsEntry("user_id", "u1");
        assertThat(restored.getExtensions()).containsEntry("trace", 7);
    }

    @Test
    void jacksonSerializationUsesPythonFieldNames() throws Exception {
        Task task = new Task("s1", "t1", "worker");
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage("boom");
        task.setInputRequiredFields(Map.of("ignored", true));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(mapper.writeValueAsString(task), Map.class);

        assertThat(payload).containsEntry("session_id", "s1")
                .containsEntry("task_id", "t1")
                .containsEntry("task_type", "worker")
                .containsEntry("status", "failed")
                .containsEntry("error_message", "boom")
                .containsKey("input_required_fields");
        assertThat(payload).doesNotContainKeys("sessionId", "taskId", "taskType", "errorMessage");

        Task restored = mapper.readValue(
                """
                {
                  "session_id": "s2",
                  "task_id": "t2",
                  "task_type": "worker",
                  "status": "submitted",
                  "outputs": []
                }
                """,
                Task.class
        );

        assertThat(restored.getSessionId()).isEqualTo("s2");
        assertThat(restored.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
        assertThat(restored.getOutputs()).isEmpty();
    }
}
