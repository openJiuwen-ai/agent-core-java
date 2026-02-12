// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.controller.schema;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Intent schema models.
 * Tests IntentType enum, Intent validation rules, and intent-specific field requirements.
 */
@DisplayName("Intent Schema Tests")
class IntentTest {

    private Event makeEvent() {
        return new Event(EventType.INPUT);
    }

    @Nested
    @DisplayName("IntentType Tests")
    class IntentTypeTests {

        @Test
        @DisplayName("IntentType should have exactly 9 members")
        void testIntentTypeHasNineMembers() {
            assertEquals(9, IntentType.values().length);
        }

        @Test
        @DisplayName("All IntentType values should match expected strings")
        void testIntentTypeValues() {
            assertEquals("create_task", IntentType.CREATE_TASK.getValue());
            assertEquals("pause_task", IntentType.PAUSE_TASK.getValue());
            assertEquals("resume_task", IntentType.RESUME_TASK.getValue());
            assertEquals("continue_task", IntentType.CONTINUE_TASK.getValue());
            assertEquals("supplement_task", IntentType.SUPPLEMENT_TASK.getValue());
            assertEquals("cancel_task", IntentType.CANCEL_TASK.getValue());
            assertEquals("modify_task", IntentType.MODIFY_TASK.getValue());
            assertEquals("switch_task", IntentType.SWITCH_TASK.getValue());
            assertEquals("unknown_task", IntentType.UNKNOWN_TASK.getValue());
        }
    }

    @Nested
    @DisplayName("Intent Validation Tests")
    class IntentValidationTests {

        // ---- Confidence validation ----

        @Test
        @DisplayName("Confidence > 1.0 should raise an error")
        void testConfidenceOutOfRangeHighRaises() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.CREATE_TASK, makeEvent())
                    .targetTaskDescription("test task")
                    .confidence(1.5)
                    .build()
            );
        }

        @Test
        @DisplayName("Confidence < 0.0 should raise an error")
        void testConfidenceOutOfRangeNegativeRaises() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.CREATE_TASK, makeEvent())
                    .targetTaskDescription("test task")
                    .confidence(-0.1)
                    .build()
            );
        }

        @Test
        @DisplayName("Confidence = 0.0 and 1.0 should both be valid")
        void testConfidenceBoundaryValuesValid() {
            for (double c : new double[]{0.0, 1.0}) {
                Intent intent = Intent.builder(IntentType.CREATE_TASK, makeEvent())
                    .targetTaskDescription("create something")
                    .confidence(c)
                    .build();
                assertEquals(c, intent.getConfidence());
            }
        }

        // ---- metadata default ----

        @Test
        @DisplayName("When metadata is null, should be set to empty map")
        void testMetadataNoneBecomesEmptyDict() {
            Intent intent = Intent.builder(IntentType.CREATE_TASK, makeEvent())
                .targetTaskDescription("build widget")
                .build();
            assertNotNull(intent.getMetadata());
            assertTrue(intent.getMetadata().isEmpty());
        }

        // ---- CREATE_TASK ----

        @Test
        @DisplayName("CREATE_TASK without target_task_description should raise")
        void testCreateTaskRequiresDescription() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.CREATE_TASK, makeEvent())
                    .build()
            );
        }

        @Test
        @DisplayName("CREATE_TASK with target_task_description should be valid")
        void testCreateTaskWithDescriptionValid() {
            Intent intent = Intent.builder(IntentType.CREATE_TASK, makeEvent())
                .targetTaskDescription("Analyze the data")
                .build();
            assertEquals(IntentType.CREATE_TASK, intent.getIntentType());
        }

        // ---- CONTINUE_TASK ----

        @Test
        @DisplayName("CONTINUE_TASK without depend_task_id should raise")
        void testContinueTaskRequiresDependTaskId() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.CONTINUE_TASK, makeEvent())
                    .build()
            );
        }

        @Test
        @DisplayName("CONTINUE_TASK with depend_task_id should be valid")
        void testContinueTaskWithDependTaskIdValid() {
            Intent intent = Intent.builder(IntentType.CONTINUE_TASK, makeEvent())
                .dependTaskId("prev-task-1")
                .build();
            assertEquals("prev-task-1", intent.getDependTaskId());
        }

        // ---- SUPPLEMENT_TASK ----

        @Test
        @DisplayName("SUPPLEMENT_TASK without target_task_id should raise")
        void testSupplementTaskRequiresTargetTaskId() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.SUPPLEMENT_TASK, makeEvent())
                    .supplementaryInfo(Map.of("key", "val"))
                    .build()
            );
        }

        @Test
        @DisplayName("SUPPLEMENT_TASK without supplementary_info should raise")
        void testSupplementTaskRequiresSupplementaryInfo() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.SUPPLEMENT_TASK, makeEvent())
                    .targetTaskId("task-1")
                    .build()
            );
        }

        @Test
        @DisplayName("SUPPLEMENT_TASK with both fields should be valid")
        void testSupplementTaskWithBothFieldsValid() {
            Intent intent = Intent.builder(IntentType.SUPPLEMENT_TASK, makeEvent())
                .targetTaskId("task-1")
                .supplementaryInfo(Map.of("additional_data", "extra"))
                .build();
            assertEquals("task-1", intent.getTargetTaskId());
        }

        // ---- MODIFY_TASK ----

        @Test
        @DisplayName("MODIFY_TASK without target_task_id should raise")
        void testModifyTaskRequiresTargetTaskId() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.MODIFY_TASK, makeEvent())
                    .modificationDetails(Map.of("param", "new_value"))
                    .build()
            );
        }

        @Test
        @DisplayName("MODIFY_TASK without modification_details should raise")
        void testModifyTaskRequiresModificationDetails() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.MODIFY_TASK, makeEvent())
                    .targetTaskId("task-1")
                    .build()
            );
        }

        @Test
        @DisplayName("MODIFY_TASK with both fields should be valid")
        void testModifyTaskWithBothFieldsValid() {
            Intent intent = Intent.builder(IntentType.MODIFY_TASK, makeEvent())
                .targetTaskId("task-1")
                .modificationDetails(Map.of("timeout", 300))
                .build();
            assertEquals(300, intent.getModificationDetails().get("timeout"));
        }

        // ---- PAUSE/RESUME/CANCEL_TASK ----

        @ParameterizedTest
        @EnumSource(value = IntentType.class, names = {"PAUSE_TASK", "RESUME_TASK", "CANCEL_TASK"})
        @DisplayName("PAUSE/RESUME/CANCEL without target_task_id should raise")
        void testPauseResumeCancelRequireTargetTaskId(IntentType intentType) {
            assertThrows(BaseError.class, () ->
                Intent.builder(intentType, makeEvent())
                    .build()
            );
        }

        @ParameterizedTest
        @EnumSource(value = IntentType.class, names = {"PAUSE_TASK", "RESUME_TASK", "CANCEL_TASK"})
        @DisplayName("PAUSE/RESUME/CANCEL with target_task_id should be valid")
        void testPauseResumeCancelWithTargetValid(IntentType intentType) {
            Intent intent = Intent.builder(intentType, makeEvent())
                .targetTaskId("task-target")
                .build();
            assertEquals("task-target", intent.getTargetTaskId());
        }

        // ---- SWITCH_TASK ----

        @Test
        @DisplayName("SWITCH_TASK without target_task_description should raise")
        void testSwitchTaskRequiresDescription() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.SWITCH_TASK, makeEvent())
                    .build()
            );
        }

        @Test
        @DisplayName("SWITCH_TASK with target_task_description should be valid")
        void testSwitchTaskWithDescriptionValid() {
            Intent intent = Intent.builder(IntentType.SWITCH_TASK, makeEvent())
                .targetTaskDescription("Do something else")
                .build();
            assertEquals("Do something else", intent.getTargetTaskDescription());
        }

        // ---- UNKNOWN_TASK ----

        @Test
        @DisplayName("UNKNOWN_TASK without clarification_prompt should raise")
        void testUnknownTaskRequiresClarificationPrompt() {
            assertThrows(BaseError.class, () ->
                Intent.builder(IntentType.UNKNOWN_TASK, makeEvent())
                    .build()
            );
        }

        @Test
        @DisplayName("UNKNOWN_TASK with clarification_prompt should be valid")
        void testUnknownTaskWithClarificationValid() {
            Intent intent = Intent.builder(IntentType.UNKNOWN_TASK, makeEvent())
                .clarificationPrompt("Could you clarify what you mean?")
                .build();
            assertEquals("Could you clarify what you mean?", intent.getClarificationPrompt());
        }
    }
}

