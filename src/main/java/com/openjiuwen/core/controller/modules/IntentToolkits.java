/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.modules;

import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intent toolkits for intent recognition.
 * <p>
 * Provides OpenAI-compatible tool schemas and intent creation methods
 * for each supported intent type.
 * <p>
 * Mirrors Python's {@code IntentToolkits} in
 * {@code openjiuwen/core/controller/modules/intent_toolkits.py}.</p>
 */
public class IntentToolkits {

    private static final String CONFIDENCE_DESCRIPTION = "Your confidence score for this operation (0-1.0), "
            + "typically used when confidence is low";

    private final Event event;
    private final double confidenceThreshold;
    private final Map<String, Map<String, Object>> toolSchemaChoices;

    public IntentToolkits(Event event, double confidenceThreshold) {
        this.event = event;
        this.confidenceThreshold = confidenceThreshold;
        this.toolSchemaChoices = buildToolSchemaChoices();
    }

    /**
     * Result of intent creation: an intent plus a descriptive message.
     *
     * <p>Mirrors Python's intent tool method tuple returns in
     * {@code openjiuwen/core/controller/modules/intent_toolkits.py}.</p>
     */
    public record IntentResult(Intent intent, String message) {}

    // ==================== Intent creation methods ====================

    public IntentResult createTask(double confidence, String taskDescription) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        String targetTaskId = UUID.randomUUID().toString();
        return new IntentResult(
                new Intent(IntentType.CREATE_TASK, event, targetTaskId, taskDescription,
                        List.of(), null, null, confidence, null),
                "Task ID: " + targetTaskId + ", Task Description: " + taskDescription
                        + ", Current Status: Created and submitted for execution"
        );
    }

    public IntentResult pauseTask(double confidence, String taskId) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.PAUSE_TASK, event, taskId, null,
                        List.of(), null, null, confidence, null),
                "Task ID: " + taskId + ", Current Status: Paused"
        );
    }

    public IntentResult cancelTask(double confidence, String taskId) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.CANCEL_TASK, event, taskId, null,
                        List.of(), null, null, confidence, null),
                "Task ID: " + taskId + ", Current Status: Canceled"
        );
    }

    public IntentResult resumeTask(double confidence, String taskId) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.RESUME_TASK, event, taskId, null,
                        List.of(), null, null, confidence, null),
                "Task ID: " + taskId + ", Current Status: Resumed"
        );
    }

    public IntentResult unknownTask(double confidence, String questionForUser) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.UNKNOWN_TASK, event, "", null,
                        List.of(), null, null, confidence, questionForUser),
                "Request sent, waiting for user response."
        );
    }

    public IntentResult createDependentTask(double confidence, String taskDescription, List<String> dependentTaskIds) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        String targetTaskId = UUID.randomUUID().toString();
        return new IntentResult(
                new Intent(IntentType.CONTINUE_TASK, event, targetTaskId, taskDescription,
                        dependentTaskIds, null, null, confidence, null),
                "Task ID: " + targetTaskId + ", Task Description: " + taskDescription
                        + ", Current Status: Created and submitted for execution"
        );
    }

    public IntentResult modifyTask(double confidence, String taskId, String newTaskDescription) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        String targetTaskId = UUID.randomUUID().toString();
        return new IntentResult(
                new Intent(IntentType.MODIFY_TASK, event, targetTaskId, newTaskDescription,
                        List.of(taskId), null, newTaskDescription, confidence, null),
                "Task ID: " + targetTaskId + ", Task Description: " + newTaskDescription
                        + ", Current Status: Created and submitted for execution"
        );
    }

    public IntentResult supplementTask(double confidence, String taskId, String supplementInfo) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.SUPPLEMENT_TASK, event, taskId, null,
                        List.of(), supplementInfo, null, confidence, null),
                "Task supplementary information submitted."
        );
    }

    /**
     * Get OpenAI-compatible tool schemas.
     *
     * @param choices optional list from the Python API; current Python source ignores non-empty values
     * @return list of tool schema maps
     */
    public List<Map<String, Object>> getOpenaiToolSchemas(List<String> choices) {
        return new ArrayList<>(toolSchemaChoices.values());
    }

    /**
     * Dispatch a tool call by name.
     *
     * @param toolName  the tool function name
     * @param arguments parsed arguments map
     * @return intent result
     */
    public IntentResult dispatch(String toolName, Map<String, Object> arguments) {
        double confidence = ((Number) arguments.get("confidence")).doubleValue();
        return switch (toolName) {
            case "create_task" -> createTask(confidence, (String) arguments.get("task_description"));
            case "pause_task" -> pauseTask(confidence, (String) arguments.get("task_id"));
            case "cancel_task" -> cancelTask(confidence, (String) arguments.get("task_id"));
            case "resume_task" -> resumeTask(confidence, (String) arguments.get("task_id"));
            case "unknown_task" -> unknownTask(confidence, (String) arguments.get("question_for_user"));
            case "create_dependent_task" -> {
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) arguments.get("dependent_task_ids");
                yield createDependentTask(confidence, (String) arguments.get("task_description"), ids);
            }
            case "modify_task" -> modifyTask(confidence, (String) arguments.get("task_id"),
                    (String) arguments.get("new_task_description"));
            case "supplement_task" -> supplementTask(confidence, (String) arguments.get("task_id"),
                    (String) arguments.get("supplement_info"));
            default -> lowConfidenceIntent(0.0);
        };
    }

    // ==================== Internal ====================

    private IntentResult lowConfidenceIntent(double confidence) {
        return new IntentResult(
                new Intent(IntentType.UNKNOWN_TASK, event, "", null,
                        List.of(), null, null, confidence,
                        "Sorry, I couldn't understand your meaning. Please clarify whether "
                                + "you want to create a new task or modify an existing one."),
                "Automatically converted to unknown_task due to low confidence"
        );
    }

    private Map<String, Map<String, Object>> buildToolSchemaChoices() {
        Map<String, Map<String, Object>> choices = new LinkedHashMap<>();

        choices.put("create_task", buildFunctionTool("create_task",
                "Create a new task. Use this method when the user wants to start a new task or activity.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("task_description", propString("Detailed description of the task, specifying what "
                                + "the user wants to accomplish")),
                        property("dependent_task_id", propString("Optional parameter specifying the ID of the "
                                + "predecessor task on which this task depends, used for task dependencies"))
                ),
                List.of("confidence", "task_description")
        ));

        choices.put("pause_task", buildFunctionTool("pause_task",
                "Pause a specific task. Use when the user wants to temporarily interrupt or suspend an ongoing task.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("task_id", propString("Unique identifier of the task to be paused"))
                ),
                List.of("confidence", "task_id")
        ));

        choices.put("cancel_task", buildFunctionTool("cancel_task",
                "Cancel a specific task. Use when the user wants to completely terminate or abandon a task.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("task_id", propString("Unique identifier of the task to be canceled"))
                ),
                List.of("confidence", "task_id")
        ));

        choices.put("resume_task", buildFunctionTool("resume_task",
                "Resume a specific task. Use when the user wants to continue a previously paused or interrupted task.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("task_id", propString("Unique identifier of the task to be resumed"))
                ),
                List.of("confidence", "task_id")
        ));

        choices.put("unknown_task", buildFunctionTool("unknown_task",
                "Handle unknown or ambiguous user intents. Use this method when the exact user intent cannot be "
                        + "determined to create clarification questions for the user.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("question_for_user", propString("Clarification question to ask the user to obtain "
                                + "more information to determine the exact intent"))
                ),
                List.of("confidence", "question_for_user")
        ));

        choices.put("create_dependent_task", buildFunctionTool("create_dependent_task",
                "Create a new task that depends on one or more existing tasks. Use when the user wants to start a "
                        + "task that requires completion of other tasks first.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("task_description", propString("Detailed description of the dependent task")),
                        property("dependent_task_ids",
                                propStringArray("List of task IDs that this task depends on"))
                ),
                List.of("confidence", "task_description", "dependent_task_ids")
        ));

        choices.put("modify_task", buildFunctionTool("modify_task",
                "Modify an existing task by creating a new version with updated description. Use when the user wants "
                        + "to change the details or requirements of an existing task.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("task_id", propString("Unique identifier of the task to be modified")),
                        property("new_task_description", propString("Updated description for the task"))
                ),
                List.of("confidence", "task_id", "new_task_description")
        ));

        choices.put("supplement_task", buildFunctionTool("supplement_task",
                "Add supplementary information to an existing task. Use when the user wants to provide additional "
                        + "details or context for an ongoing task without changing its core description.",
                properties(
                        property("confidence", propNumber(CONFIDENCE_DESCRIPTION)),
                        property("task_id", propString("Unique identifier of the task to be supplemented")),
                        property("supplement_info",
                                propString("Additional information or context to add to the task"))
                ),
                List.of("confidence", "task_id", "supplement_info")
        ));

        return choices;
    }

    private static Map<String, Object> buildFunctionTool(String name, String description,
                                                          Map<String, Object> properties,
                                                          List<String> required) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", required);
        params.put("additionalProperties", false);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", params);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    @SafeVarargs
    private static Map<String, Object> properties(Map.Entry<String, Object>... entries) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : entries) {
            properties.put(entry.getKey(), entry.getValue());
        }
        return properties;
    }

    private static Map.Entry<String, Object> property(String name, Map<String, Object> schema) {
        return Map.entry(name, schema);
    }

    private static Map<String, Object> propString(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> propNumber(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "number");
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> propStringArray(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "array");
        property.put("items", Map.of("type", "string"));
        property.put("description", description);
        return property;
    }
}
