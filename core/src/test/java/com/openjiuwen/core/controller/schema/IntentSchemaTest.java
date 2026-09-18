/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for the intent schema model.
 *
 * <p>Mirrors Python's {@code Intent} and {@code IntentType} in
 * {@code openjiuwen/core/controller/schema/intent.py}.</p>
 */
class IntentSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Event event = new Event();

    @Test
    void intentTypeUsesPythonWireValues() throws Exception {
        assertThat(IntentType.CREATE_TASK.getValue()).isEqualTo("create_task");
        assertThat(IntentType.PAUSE_TASK.getValue()).isEqualTo("pause_task");
        assertThat(IntentType.RESUME_TASK.getValue()).isEqualTo("resume_task");
        assertThat(IntentType.CONTINUE_TASK.getValue()).isEqualTo("continue_task");
        assertThat(IntentType.SUPPLEMENT_TASK.getValue()).isEqualTo("supplement_task");
        assertThat(IntentType.CANCEL_TASK.getValue()).isEqualTo("cancel_task");
        assertThat(IntentType.MODIFY_TASK.getValue()).isEqualTo("modify_task");
        assertThat(IntentType.SWITCH_TASK.getValue()).isEqualTo("switch_task");
        assertThat(IntentType.UNKNOWN_TASK.getValue()).isEqualTo("unknown_task");

        assertThat(IntentType.fromValue("continue_task")).isEqualTo(IntentType.CONTINUE_TASK);
        assertThat(mapper.writeValueAsString(IntentType.CANCEL_TASK)).isEqualTo("\"cancel_task\"");
        assertThat(mapper.readValue("\"supplement_task\"", IntentType.class))
                .isEqualTo(IntentType.SUPPLEMENT_TASK);
    }

    @Test
    void constructorsApplyPythonDefaultsAndValidationRules() {
        Intent pause = new Intent(IntentType.PAUSE_TASK, event, "task-1");

        assertThat(pause.getIntentType()).isEqualTo(IntentType.PAUSE_TASK);
        assertThat(pause.getEvent()).isSameAs(event);
        assertThat(pause.getTargetTaskId()).isEqualTo("task-1");
        assertThat(pause.getConfidence()).isEqualTo(1.0);
        assertThat(pause.getMetadata()).isEmpty();

        assertValidationMessage(
                () -> new Intent(IntentType.PAUSE_TASK, event, ""),
                "pause_task intent requires target_task_id"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.CREATE_TASK, event, "task-1"),
                "CREATE_TASK intent requires target_task_description"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.CONTINUE_TASK, event, "task-1", "continue",
                        List.of(), null, null, 1.0, null),
                "CONTINUE_TASK intent requires depend_task_id"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.SUPPLEMENT_TASK, event, "", null,
                        null, "info", null, 1.0, null),
                "SUPPLEMENT_TASK intent requires target_task_id"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.SUPPLEMENT_TASK, event, "task-1", null,
                        null, "", null, 1.0, null),
                "SUPPLEMENT_TASK intent requires supplementary_info"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.MODIFY_TASK, event, "task-1", null,
                        null, null, "", 1.0, null),
                "MODIFY_TASK intent requires modification_details"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.SWITCH_TASK, event, "task-1", "",
                        null, null, null, 1.0, null),
                "SWITCH_TASK intent requires target_task_description"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.UNKNOWN_TASK, event, "", null,
                        null, null, null, 1.0, ""),
                "UNKNOWN_TASK intent requires clarification_prompt"
        );
        assertValidationMessage(
                () -> new Intent(IntentType.PAUSE_TASK, event, "task-1", null,
                        null, null, null, 1.1, null),
                "Confidence must be between 0.0 and 1.0, got 1.1"
        );
    }

    @Test
    void validationUsesPythonStringTruthinessInsteadOfJavaBlankness() {
        assertThatCode(() -> new Intent(IntentType.CREATE_TASK, event, "task-1", " ",
                null, null, null, 1.0, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Intent(IntentType.PAUSE_TASK, event, " "))
                .doesNotThrowAnyException();
        assertThatCode(() -> new Intent(IntentType.SUPPLEMENT_TASK, event, " ", null,
                null, " ", null, 1.0, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Intent(IntentType.MODIFY_TASK, event, " ", null,
                null, null, " ", 1.0, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Intent(IntentType.SWITCH_TASK, event, "task-1", " ",
                null, null, null, 1.0, null)).doesNotThrowAnyException();
        assertThatCode(() -> new Intent(IntentType.UNKNOWN_TASK, event, "", null,
                null, null, null, 1.0, " ")).doesNotThrowAnyException();
    }

    @Test
    void jacksonSerializationUsesPythonFieldNames() throws Exception {
        Intent intent = new Intent(IntentType.CONTINUE_TASK, event, "task-2", "continue",
                List.of("dep-1"), null, null, 0.42, Map.of("origin", "unit"), null);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(mapper.writeValueAsString(intent), Map.class);

        assertThat(payload).containsEntry("intent_type", "continue_task")
                .containsEntry("event", mapper.convertValue(event, Map.class))
                .containsEntry("target_task_id", "task-2")
                .containsEntry("target_task_description", "continue")
                .containsEntry("depend_task_id", List.of("dep-1"))
                .containsEntry("confidence", 0.42)
                .containsEntry("metadata", Map.of("origin", "unit"))
                .containsKey("supplementary_info")
                .containsKey("modification_details")
                .containsKey("clarification_prompt");
        assertThat(payload).doesNotContainKeys("intentType", "targetTaskId",
                "targetTaskDescription", "dependTaskId", "supplementaryInfo",
                "modificationDetails", "clarificationPrompt");
    }

    @Test
    void jacksonDeserializationUsesPythonFieldNamesAndPostInitDefaults() throws Exception {
        Intent restored = mapper.readValue(
                """
                {
                  "intent_type": "unknown_task",
                  "event": {},
                  "target_task_id": "",
                  "confidence": 0.5,
                  "metadata": null,
                  "clarification_prompt": "Which task?"
                }
                """,
                Intent.class
        );

        assertThat(restored.getIntentType()).isEqualTo(IntentType.UNKNOWN_TASK);
        assertThat(restored.getTargetTaskId()).isEmpty();
        assertThat(restored.getConfidence()).isEqualTo(0.5);
        assertThat(restored.getMetadata()).isEmpty();
        assertThat(restored.getClarificationPrompt()).isEqualTo("Which task?");
    }

    private static void assertValidationMessage(ThrowingCallable callable, String message) {
        Throwable thrown = catchThrowable(callable);

        assertThat(thrown).isInstanceOf(BaseError.class)
                .hasMessageContaining(message);
        BaseError error = (BaseError) thrown;
        assertThat(error.getStatus()).isEqualTo(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR);
        assertThat(error.getParams()).containsEntry("error_msg", message);
    }
}
