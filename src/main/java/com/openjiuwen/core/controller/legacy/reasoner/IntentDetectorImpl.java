/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.controller.legacy.IntentDetectionController;
import com.openjiuwen.core.controller.legacy.config.IntentDetectionConfig;
import com.openjiuwen.core.controller.legacy.config.ReasonerConfig;
import com.openjiuwen.core.controller.legacy.constants.IntentDetectionConstants;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IntentDetector - Intent detection module for message intent recognition and task generation.
 * <p>
 * Mirrors Python's {@code IntentDetector} in
 * {@code openjiuwen.core.controller.legacy.reasoner.intent_detector}.
 * </p>
 */
public class IntentDetectorImpl implements IntentDetector {

    private static final LoggerProtocol LOG = Loggers.CONTROLLER;
    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("^\\s*```json\\s*|\\s*```\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_QUOTE_FENCE_PATTERN =
            Pattern.compile("^\\s*'''json\\s*|\\s*'''\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESULT_PATTERN = Pattern.compile("\"result\"\\s*:\\s*\"?(\\d+)\"?");

    private final IntentDetectionConfig intentConfig;
    private final Object agentConfig;
    private final Object contextEngine;
    private final Session session;

    /**
     * Initialize IntentDetector.
     *
     * @param intentConfig  IntentDetection config
     * @param agentConfig   Agent config
     * @param contextEngine Context engine
     * @param session       Session environment
     */
    public IntentDetectorImpl(
            IntentDetectionConfig intentConfig,
            Object agentConfig,
            Object contextEngine,
            Session session) {
        this.intentConfig = intentConfig;
        this.agentConfig = agentConfig;
        this.contextEngine = contextEngine;
        this.session = session;
    }

    /**
     * Process event, detect intent and generate tasks.
     *
     * @param event Input event
     * @return Generated task list
     */
    public CompletableFuture<List<Task>> processMessage(Event event) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Prepare detection input
            List<Object> llmInputs = prepareDetectionInput(event);
            String sessionId = session != null ? session.getSessionId() : "unknown";
            LOG.info("[{}] <LLM Input>: {}", sessionId, llmInputs);

            // 2. Call LLM for intent detection
            String llmOutput = invokeLlmGetOutput(llmInputs);
            LOG.info("[{}] <LLM Output>: {}", sessionId, llmOutput);

            // 3. Parse intent from output
            String detectedIntentId = parseIntentFromOutput(llmOutput);

            // 4. Create tasks from intent
            return generateTasksFromIntent(detectedIntentId, event);
        });
    }

    @Override
    public IntentDetectionController.Intent detect(Event event, Session session, ReasonerConfig config) {
        // Simplified detection for interface compatibility
        String content = event != null && event.getContent() != null ? event.getContent().getQueryText() : "";
        String intentId = detectIntentFromContent(content);
        return IntentDetectionController.Intent.builder()
                .intentType(IntentDetectionController.IntentType.UNKNOWN)
                .metadata(Map.of("intent_id", intentId, "content", content))
                .build();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Prepare detection input from event.
     */
    private List<Object> prepareDetectionInput(Event event) {
        List<Object> inputs = new ArrayList<>();
        if (event != null && event.getContent() != null) {
            inputs.add(event.getContent());
        }
        return inputs;
    }

    /**
     * Invoke LLM and get output.
     */
    private String invokeLlmGetOutput(List<Object> inputs) {
        // Placeholder: actual LLM call would use contextEngine
        if (intentConfig != null) {
            return intentConfig.getDefaultClass();
        }
        return "default";
    }

    /**
     * Parse intent from LLM output.
     */
    private String parseIntentFromOutput(String output) {
        if (output == null || output.isEmpty()) {
            return defaultIntentClass();
        }
        try {
            String cleaned = JSON_FENCE_PATTERN.matcher(output.strip()).replaceAll("");
            cleaned = SINGLE_QUOTE_FENCE_PATTERN.matcher(cleaned).replaceAll("");
            Matcher matcher = RESULT_PATTERN.matcher(cleaned);
            if (!matcher.find()) {
                return defaultIntentClass();
            }

            int detectedClassNumber = Integer.parseInt(matcher.group(1));
            List<String> categories = intentConfig != null && intentConfig.getCategoryList() != null
                    ? intentConfig.getCategoryList()
                    : Collections.emptyList();
            if (detectedClassNumber <= 0 || detectedClassNumber > categories.size()) {
                LOG.warn("get unknown class");
                return defaultIntentClass();
            }

            String detectedIntentName = categories.get(detectedClassNumber - 1);
            List<?> workflows = getWorkflows();
            if (workflows.isEmpty()) {
                LOG.info("[{}] get intent (direct category): {}",
                        session != null ? session.getSessionId() : "unknown", detectedIntentName);
                return detectedIntentName;
            }

            for (Object workflow : workflows) {
                String workflowLabel = firstNonBlank(
                        getStringProperty(workflow, "getDescription"),
                        getStringProperty(workflow, "getName")
                );
                if (detectedIntentName.equals(workflowLabel)) {
                    String workflowId = getStringProperty(workflow, "getId");
                    LOG.info("[{}] get intent: {}", session != null ? session.getSessionId() : "unknown", workflowId);
                    return workflowId;
                }
            }
            return "";
        } catch (RuntimeException ex) {
            LOG.error("failed to parse JSON from LLM output, error: {}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * Detect intent from content directly (fallback).
     */
    private String detectIntentFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return "default";
        }
        // Placeholder intent detection
        return "general";
    }

    /**
     * Create task objects from detected intent.
     */
    private List<Task> generateTasksFromIntent(String intentId, Event event) {
        List<Task> tasks = new ArrayList<>();
        String sessionId = session != null ? session.getSessionId() : "unknown";
        String taskUniqueId = sessionId + "_intent_" + intentId + "_" + UUID.randomUUID().toString().substring(0, 8);

        if (isDefaultIntent(intentId)) {
            // No match, return empty task list
            return tasks;
        }

        List<?> workflows = getWorkflows();
        if (workflows.isEmpty()) {
            tasks.add(createWorkflowTask(taskUniqueId, intentId, intentId, event));
            LOG.info("[{}] success to create task for intent (direct): {}", sessionId, intentId);
            return tasks;
        }

        for (Object workflow : workflows) {
            String workflowId = getStringProperty(workflow, "getId");
            if (intentId.equals(workflowId)) {
                String workflowName = firstNonBlank(getStringProperty(workflow, "getName"), workflowId);
                tasks.add(createWorkflowTask(taskUniqueId, workflowId, workflowName, event));
                LOG.info("[{}] success to create task for intent: {}", sessionId, intentId);
                break;
            }
        }
        return tasks;
    }

    private Task createWorkflowTask(String taskId, String targetId, String targetName, Event event) {
        Task.TaskInput taskInput = new Task.TaskInput(targetId, targetName, event != null ? event.getContent() : null);
        return Task.builder()
                .agentId(getStringProperty(agentConfig, "getId"))
                .taskId(taskId)
                .taskType(TaskType.WORKFLOW)
                .input(taskInput)
                .build();
    }

    private boolean isDefaultIntent(String intentId) {
        return intentId == null
                || intentId.isEmpty()
                || "default".equals(intentId)
                || IntentDetectionConstants.DEFAULT_CLASS.equals(intentId)
                || defaultIntentClass().equals(intentId);
    }

    private String defaultIntentClass() {
        return intentConfig != null && intentConfig.getDefaultClass() != null
                ? intentConfig.getDefaultClass()
                : "default";
    }

    private List<?> getWorkflows() {
        Object workflows = invokeNoArg(agentConfig, "getWorkflows");
        if (workflows instanceof List<?> list) {
            return list;
        }
        return Collections.emptyList();
    }

    private static String getStringProperty(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return value != null ? String.valueOf(value) : "";
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
