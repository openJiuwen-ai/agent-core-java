/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.modules.IntentToolkits.IntentResult;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for {@link IntentToolkits}.
 *
 * <p>Mirrors Python's {@code IntentToolkits} in
 * {@code openjiuwen/core/controller/modules/intent_toolkits.py}.</p>
 */
class IntentToolkitsTest {

    private static final String CONFIDENCE_DESCRIPTION = "Your confidence score for this operation (0-1.0), "
            + "typically used when confidence is low";
    private static final String LOW_CONFIDENCE_PROMPT = "Sorry, I couldn't understand your meaning. "
            + "Please clarify whether you want to create a new task or modify an existing one.";

    private final Event event = new Event();
    private final IntentToolkits toolkits = new IntentToolkits(event, 0.7);

    @Test
    void openaiToolSchemasMatchPythonOrderAndDescriptions() {
        List<Map<String, Object>> schemas = toolkits.getOpenaiToolSchemas(null);

        assertThat(functionNames(schemas)).containsExactly(
                "create_task",
                "pause_task",
                "cancel_task",
                "resume_task",
                "unknown_task",
                "create_dependent_task",
                "modify_task",
                "supplement_task"
        );
        assertThat(functionNames(toolkits.getOpenaiToolSchemas(List.of("pause_task"))))
                .containsExactlyElementsOf(functionNames(schemas));

        Map<String, Object> createTaskFunction = function(schemas.get(0));
        assertThat(createTaskFunction)
                .containsEntry("name", "create_task")
                .containsEntry("description",
                        "Create a new task. Use this method when the user wants to start a new task or activity.");

        Map<String, Object> createTaskParameters = mapValue(createTaskFunction, "parameters");
        assertThat(createTaskParameters)
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(stringListValue(createTaskParameters, "required"))
                .containsExactly("confidence", "task_description");

        Map<String, Object> createTaskProperties = mapValue(createTaskParameters, "properties");
        assertThat(createTaskProperties.keySet())
                .containsExactly("confidence", "task_description", "dependent_task_id");
        assertProperty(createTaskProperties, "confidence", "number", CONFIDENCE_DESCRIPTION);
        assertProperty(createTaskProperties, "task_description", "string",
                "Detailed description of the task, specifying what the user wants to accomplish");
        assertProperty(createTaskProperties, "dependent_task_id", "string",
                "Optional parameter specifying the ID of the predecessor task on which this task depends, "
                        + "used for task dependencies");

        Map<String, Object> dependentTaskFunction = function(schemas.get(5));
        assertThat(dependentTaskFunction)
                .containsEntry("name", "create_dependent_task")
                .containsEntry("description",
                        "Create a new task that depends on one or more existing tasks. Use when the user wants "
                                + "to start a task that requires completion of other tasks first.");
        Map<String, Object> dependentTaskProperties =
                mapValue(mapValue(dependentTaskFunction, "parameters"), "properties");
        Map<String, Object> dependentTaskIds = mapValue(dependentTaskProperties, "dependent_task_ids");
        assertThat(dependentTaskIds)
                .containsEntry("type", "array")
                .containsEntry("description", "List of task IDs that this task depends on");
        assertThat(mapValue(dependentTaskIds, "items")).containsEntry("type", "string");
    }

    @Test
    void createsTaskManagementIntentsWithPythonFieldValues() {
        IntentResult createResult = toolkits.createTask(0.7, "write report");
        Intent createIntent = createResult.intent();
        assertThat(createIntent.getIntentType()).isEqualTo(IntentType.CREATE_TASK);
        assertThat(createIntent.getEvent()).isSameAs(event);
        assertThat(createIntent.getTargetTaskId()).isNotBlank();
        assertThat(createIntent.getTargetTaskDescription()).isEqualTo("write report");
        assertThat(createIntent.getDependTaskId()).isEmpty();
        assertThat(createIntent.getConfidence()).isEqualTo(0.7);
        assertThat(createResult.message()).isEqualTo("Task ID: " + createIntent.getTargetTaskId()
                + ", Task Description: write report, Current Status: Created and submitted for execution");

        assertTargetedIntent(toolkits.pauseTask(0.9, "task-1"), IntentType.PAUSE_TASK,
                "task-1", "Task ID: task-1, Current Status: Paused");
        assertTargetedIntent(toolkits.cancelTask(0.9, "task-2"), IntentType.CANCEL_TASK,
                "task-2", "Task ID: task-2, Current Status: Canceled");
        assertTargetedIntent(toolkits.resumeTask(0.9, "task-3"), IntentType.RESUME_TASK,
                "task-3", "Task ID: task-3, Current Status: Resumed");
    }

    @Test
    void createsUnknownDependentModifyAndSupplementIntentsWithPythonFieldValues() {
        IntentResult unknownResult = toolkits.unknownTask(0.8, "Which task?");
        assertThat(unknownResult.intent().getIntentType()).isEqualTo(IntentType.UNKNOWN_TASK);
        assertThat(unknownResult.intent().getTargetTaskId()).isEmpty();
        assertThat(unknownResult.intent().getClarificationPrompt()).isEqualTo("Which task?");
        assertThat(unknownResult.message()).isEqualTo("Request sent, waiting for user response.");

        IntentResult dependentResult = toolkits.createDependentTask(0.8, "dependent work", List.of("task-1", "task-2"));
        Intent dependentIntent = dependentResult.intent();
        assertThat(dependentIntent.getIntentType()).isEqualTo(IntentType.CONTINUE_TASK);
        assertThat(dependentIntent.getTargetTaskId()).isNotBlank();
        assertThat(dependentIntent.getTargetTaskDescription()).isEqualTo("dependent work");
        assertThat(dependentIntent.getDependTaskId()).containsExactly("task-1", "task-2");
        assertThat(dependentResult.message()).isEqualTo("Task ID: " + dependentIntent.getTargetTaskId()
                + ", Task Description: dependent work, Current Status: Created and submitted for execution");

        IntentResult modifyResult = toolkits.modifyTask(0.8, "task-3", "new description");
        Intent modifyIntent = modifyResult.intent();
        assertThat(modifyIntent.getIntentType()).isEqualTo(IntentType.MODIFY_TASK);
        assertThat(modifyIntent.getTargetTaskId()).isNotEqualTo("task-3").isNotBlank();
        assertThat(modifyIntent.getTargetTaskDescription()).isEqualTo("new description");
        assertThat(modifyIntent.getDependTaskId()).containsExactly("task-3");
        assertThat(modifyIntent.getModificationDetails()).isEqualTo("new description");
        assertThat(modifyResult.message()).isEqualTo("Task ID: " + modifyIntent.getTargetTaskId()
                + ", Task Description: new description, Current Status: Created and submitted for execution");

        IntentResult supplementResult = toolkits.supplementTask(0.8, "task-4", "more context");
        Intent supplementIntent = supplementResult.intent();
        assertThat(supplementIntent.getIntentType()).isEqualTo(IntentType.SUPPLEMENT_TASK);
        assertThat(supplementIntent.getTargetTaskId()).isEqualTo("task-4");
        assertThat(supplementIntent.getSupplementaryInfo()).isEqualTo("more context");
        assertThat(supplementResult.message()).isEqualTo("Task supplementary information submitted.");
    }

    @Test
    void lowConfidenceAlwaysReturnsUnknownIntent() {
        IntentResult result = toolkits.pauseTask(0.69, "task-1");

        assertThat(result.message()).isEqualTo("Automatically converted to unknown_task due to low confidence");
        assertThat(result.intent().getIntentType()).isEqualTo(IntentType.UNKNOWN_TASK);
        assertThat(result.intent().getEvent()).isSameAs(event);
        assertThat(result.intent().getTargetTaskId()).isEmpty();
        assertThat(result.intent().getDependTaskId()).isEmpty();
        assertThat(result.intent().getConfidence()).isEqualTo(0.69);
        assertThat(result.intent().getClarificationPrompt()).isEqualTo(LOW_CONFIDENCE_PROMPT);
    }

    @Test
    void dispatchRoutesToolNamesToCompatibleMethods() {
        IntentResult result = toolkits.dispatch("supplement_task", Map.of(
                "confidence", 0.9,
                "task_id", "task-1",
                "supplement_info", "extra"
        ));

        assertThat(result.intent().getIntentType()).isEqualTo(IntentType.SUPPLEMENT_TASK);
        assertThat(result.intent().getTargetTaskId()).isEqualTo("task-1");
        assertThat(result.intent().getSupplementaryInfo()).isEqualTo("extra");
        assertThat(result.message()).isEqualTo("Task supplementary information submitted.");
    }

    private void assertTargetedIntent(IntentResult result, IntentType intentType, String taskId, String message) {
        assertThat(result.intent().getIntentType()).isEqualTo(intentType);
        assertThat(result.intent().getEvent()).isSameAs(event);
        assertThat(result.intent().getTargetTaskId()).isEqualTo(taskId);
        assertThat(result.intent().getTargetTaskDescription()).isNull();
        assertThat(result.intent().getDependTaskId()).isEmpty();
        assertThat(result.intent().getConfidence()).isEqualTo(0.9);
        assertThat(result.message()).isEqualTo(message);
    }

    private static List<String> functionNames(List<Map<String, Object>> schemas) {
        return schemas.stream().map(IntentToolkitsTest::functionName).toList();
    }

    private static String functionName(Map<String, Object> schema) {
        return (String) function(schema).get("name");
    }

    private static Map<String, Object> function(Map<String, Object> schema) {
        return mapValue(schema, "function");
    }

    private static void assertProperty(Map<String, Object> properties, String name, String type, String description) {
        Map<String, Object> property = mapValue(properties, name);
        assertThat(property)
                .containsEntry("type", type)
                .containsEntry("description", description);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListValue(Map<String, Object> source, String key) {
        return (List<String>) source.get(key);
    }
}
