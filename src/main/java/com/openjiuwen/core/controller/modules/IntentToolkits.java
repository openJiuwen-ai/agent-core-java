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
 * Mirrors Python's {@code IntentToolkits}.
 * 
 * @since 0.1.7
 */
public class IntentToolkits {
    private final Event event;
    private final double confidenceThreshold;
    private final Map<String, Map<String, Object>> toolSchemaChoices;

    /**
     * IntentToolkits.
     * 
     * @param event event
     * @param confidenceThreshold confidenceThreshold
     * @since 0.1.7
     */
    public IntentToolkits(Event event, double confidenceThreshold) {
        this.event = event;
        this.confidenceThreshold = confidenceThreshold;
        this.toolSchemaChoices = buildToolSchemaChoices();
    }

    /**
     * Result of intent creation: an intent + a descriptive message.
     * 
     * @since 0.1.7
     */
    public record IntentResult(Intent intent, String message) {
    }

    // ==================== Intent creation methods ====================

    /**
     * createTask.
     * 
     * @param confidence confidence
     * @param taskDescription taskDescription
     * @return the result
     * @since 0.1.7
     */
    public IntentResult createTask(double confidence, String taskDescription) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        String targetTaskId = UUID.randomUUID().toString();
        return new IntentResult(
                new Intent(IntentType.CREATE_TASK, event, targetTaskId, taskDescription, List.of(), null, null,
                        confidence, null),
                "Task ID: " + targetTaskId + ", Task Description: " + taskDescription
                        + ", Current Status: Created and submitted for execution");
    }

    /**
     * pauseTask.
     * 
     * @param confidence confidence
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public IntentResult pauseTask(double confidence, String taskId) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.PAUSE_TASK, event, taskId, null, List.of(), null, null, confidence, null),
                "Task ID: " + taskId + ", Current Status: Paused");
    }

    /**
     * cancelTask.
     * 
     * @param confidence confidence
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public IntentResult cancelTask(double confidence, String taskId) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.CANCEL_TASK, event, taskId, null, List.of(), null, null, confidence, null),
                "Task ID: " + taskId + ", Current Status: Canceled");
    }

    /**
     * resumeTask.
     * 
     * @param confidence confidence
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public IntentResult resumeTask(double confidence, String taskId) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(
                new Intent(IntentType.RESUME_TASK, event, taskId, null, List.of(), null, null, confidence, null),
                "Task ID: " + taskId + ", Current Status: Resumed");
    }

    /**
     * unknownTask.
     * 
     * @param confidence confidence
     * @param questionForUser questionForUser
     * @return the result
     * @since 0.1.7
     */
    public IntentResult unknownTask(double confidence, String questionForUser) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(new Intent(IntentType.UNKNOWN_TASK, event, "", null, List.of(), null, null, confidence,
                questionForUser), "Request sent, waiting for user response.");
    }

    /**
     * createDependentTask.
     * 
     * @param confidence confidence
     * @param taskDescription taskDescription
     * @param dependentTaskIds dependentTaskIds
     * @return the result
     * @since 0.1.7
     */
    public IntentResult createDependentTask(double confidence, String taskDescription, List<String> dependentTaskIds) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        String targetTaskId = UUID.randomUUID().toString();
        return new IntentResult(
                new Intent(IntentType.CONTINUE_TASK, event, targetTaskId, taskDescription, dependentTaskIds, null, null,
                        confidence, null),
                "Task ID: " + targetTaskId + ", Task Description: " + taskDescription
                        + ", Current Status: Created and submitted for execution");
    }

    /**
     * modifyTask.
     * 
     * @param confidence confidence
     * @param taskId taskId
     * @param newTaskDescription newTaskDescription
     * @return the result
     * @since 0.1.7
     */
    public IntentResult modifyTask(double confidence, String taskId, String newTaskDescription) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        String targetTaskId = UUID.randomUUID().toString();
        return new IntentResult(
                new Intent(IntentType.MODIFY_TASK, event, targetTaskId, newTaskDescription, List.of(taskId), null,
                        newTaskDescription, confidence, null),
                "Task ID: " + targetTaskId + ", Task Description: " + newTaskDescription
                        + ", Current Status: Created and submitted for execution");
    }

    /**
     * supplementTask.
     * 
     * @param confidence confidence
     * @param taskId taskId
     * @param supplementInfo supplementInfo
     * @return the result
     * @since 0.1.7
     */
    public IntentResult supplementTask(double confidence, String taskId, String supplementInfo) {
        if (confidence < confidenceThreshold) {
            return lowConfidenceIntent(confidence);
        }
        return new IntentResult(new Intent(IntentType.SUPPLEMENT_TASK, event, taskId, null, List.of(), supplementInfo,
                null, confidence, null), "Task supplementary information submitted.");
    }

    /**
     * Get OpenAI-compatible tool schemas.
     * 
     * @param choices optional list of tool names to include; null returns all
     * @return list of tool schema maps
     * @since 0.1.7
     */
    public List<Map<String, Object>> getOpenaiToolSchemas(List<String> choices) {
        if (choices == null || choices.isEmpty()) {
            return new ArrayList<>(toolSchemaChoices.values());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : choices) {
            Map<String, Object> schema = toolSchemaChoices.get(key);
            if (schema != null) {
                result.add(schema);
            }
        }
        return result;
    }

    /**
     * Dispatch a tool call by name.
     * 
     * @param toolName the tool function name
     * @param arguments parsed arguments map
     * @return intent result
     * @since 0.1.7
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

    /**
     * lowConfidenceIntent.
     * 
     * @param confidence confidence
     * @return the result
     * @since 0.1.7
     */
    private IntentResult lowConfidenceIntent(double confidence) {
        return new IntentResult(
                new Intent(IntentType.UNKNOWN_TASK, event, "", null, List.of(), null, null, confidence,
                        "Sorry, I couldn't understand your meaning. Please clarify whether "
                                + "you want to create a new task or modify an existing one."),
                "Automatically converted to unknown_task due to low confidence");
    }

    /**
     * buildToolSchemaChoices.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Map<String, Object>> buildToolSchemaChoices() {
        Map<String, Map<String, Object>> choices = new LinkedHashMap<>();

        choices.put("create_task", buildFunctionTool("create_task",
                "Create a new task. Use this method when the user wants to start a new task or activity.",
                Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"), "task_description",
                        propString("Detailed description of the task"), "dependent_task_id",
                        propString("Optional ID of predecessor task")),
                List.of("confidence", "task_description")));

        choices.put("pause_task",
                buildFunctionTool("pause_task", "Pause a specific task.",
                        Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"), "task_id",
                                propString("Unique identifier of the task to be paused")),
                        List.of("confidence", "task_id")));

        choices.put("cancel_task",
                buildFunctionTool("cancel_task", "Cancel a specific task.",
                        Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"), "task_id",
                                propString("Unique identifier of the task to be canceled")),
                        List.of("confidence", "task_id")));

        choices.put("resume_task",
                buildFunctionTool("resume_task", "Resume a previously paused task.",
                        Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"), "task_id",
                                propString("Unique identifier of the task to be resumed")),
                        List.of("confidence", "task_id")));

        choices.put("unknown_task",
                buildFunctionTool("unknown_task", "Handle unknown or ambiguous user intents.",
                        Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"),
                                "question_for_user", propString("Clarification question to ask the user")),
                        List.of("confidence", "question_for_user")));

        choices.put("create_dependent_task", buildFunctionTool("create_dependent_task",
                "Create a new task that depends on existing tasks.",
                Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"), "task_description",
                        propString("Detailed description of the dependent task"), "dependent_task_ids",
                        Map.of("type", "array", "items", Map.of("type", "string"), "description",
                                "List of task IDs that this task depends on")),
                List.of("confidence", "task_description", "dependent_task_ids")));

        choices.put("modify_task",
                buildFunctionTool("modify_task",
                        "Modify an existing task by creating a new version with updated description.",
                        Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"), "task_id",
                                propString("Unique identifier of the task to be modified"), "new_task_description",
                                propString("Updated description for the task")),
                        List.of("confidence", "task_id", "new_task_description")));

        choices.put("supplement_task",
                buildFunctionTool("supplement_task", "Add supplementary information to an existing task.",
                        Map.of("confidence", propNumber("Your confidence score for this operation (0-1.0)"), "task_id",
                                propString("Unique identifier of the task to be supplemented"), "supplement_info",
                                propString("Additional information to add to the task")),
                        List.of("confidence", "task_id", "supplement_info")));

        return choices;
    }

    /**
     * buildFunctionTool.
     * 
     * @param name name
     * @param description description
     * @param properties properties
     * @param required required
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> buildFunctionTool(String name, String description,
            Map<String, Object> properties, List<String> required) {
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

    /**
     * propString.
     * 
     * @param description description
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> propString(String description) {
        return Map.of("type", "string", "description", description);
    }

    /**
     * propNumber.
     * 
     * @param description description
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> propNumber(String description) {
        return Map.of("type", "number", "description", description);
    }
}
