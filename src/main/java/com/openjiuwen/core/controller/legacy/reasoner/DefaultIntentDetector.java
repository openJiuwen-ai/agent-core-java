/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.reasoner;

import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.controller.legacy.IntentDetectionController;
import com.openjiuwen.core.controller.legacy.config.IntentDetectionConfig;
import com.openjiuwen.core.controller.legacy.config.ReasonerConfig;
import com.openjiuwen.core.controller.legacy.constants.IntentDetectionConstants;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default IntentDetector implementation - Intent detection module for message
 * intent recognition and task generation.
 * Mirrors Python's concrete {@code IntentDetector} class.
 */
public class DefaultIntentDetector implements IntentDetector {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultIntentDetector.class);
    private static final Pattern JSON_FENCE_PATTERN =
            Pattern.compile("^\\s*```json\\s*|\\s*```\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGLE_QUOTE_FENCE_PATTERN =
            Pattern.compile("^\\s*'''json\\s*|\\s*'''\\s*$", Pattern.CASE_INSENSITIVE);

    private final IntentDetectionConfig intentConfig;
    private final Object agentConfig;
    private final ContextEngine contextEngine;
    private final Session session;

    public DefaultIntentDetector(IntentDetectionConfig intentConfig, Object agentConfig,
                                 ContextEngine contextEngine, Session session) {
        this.intentConfig = intentConfig;
        this.agentConfig = agentConfig;
        this.contextEngine = contextEngine;
        this.session = session;
    }

    @Override
    public IntentDetectionController.Intent detect(Event event, Session session, ReasonerConfig config) {
        List<Task> tasks = processMessage(event);
        if (tasks.isEmpty()) {
            return IntentDetectionController.Intent.builder()
                    .intentType(IntentDetectionController.IntentType.DEFAULT_RESPONSE)
                    .metadata(new LinkedHashMap<>(Map.of("default_response_text", "")))
                    .build();
        }
        return IntentDetectionController.Intent.builder()
                .intentType(IntentDetectionController.IntentType.EXEC_NEW_TASK)
                .task(tasks.get(0))
                .build();
    }

    /**
     * Process event, detect intent and generate tasks.
     * Mirrors Python's {@code IntentDetector.process_message()}.
     */
    public List<Task> processMessage(Event event) {
        // 1. Prepare detection input
        Map<String, Object> inputMap = prepareDetectionInput(event);
        String sessionId = session != null ? session.getSessionId() : "unknown";
        LOG.info("[{}] <LLM Input>", sessionId);

        // 2. Intent detection would call LLM here
        // Default: create task from category list mapping
        String query = event.getContent() != null ? event.getContent().getQueryText() : "";
        if (query.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Generate default task for the input
        return generateDefaultTask(event, sessionId);
    }

    /**
     * Prepare intent detection input.
     * Mirrors Python's {@code IntentDetector._prepare_detection_input()}.
     */
    public Map<String, Object> prepareDetectionInput(Event event) {
        List<String> categories = intentConfig.getCategoryList();
        StringBuilder categoryList = new StringBuilder("分类0：意图不明\n");
        for (int i = 0; i < categories.size(); i++) {
            categoryList.append("分类").append(i + 1).append("：").append(categories.get(i)).append("\n");
        }

        Map<String, Object> currentInputs = new LinkedHashMap<>();
        currentInputs.put(IntentDetectionConstants.USER_PROMPT, intentConfig.getUserPrompt());
        currentInputs.put(IntentDetectionConstants.CATEGORY_LIST, categoryList.toString());
        currentInputs.put(IntentDetectionConstants.DEFAULT_CLASS, intentConfig.getDefaultClass());
        currentInputs.put(IntentDetectionConstants.ENABLE_HISTORY, intentConfig.isEnableHistory());
        currentInputs.put(IntentDetectionConstants.ENABLE_INPUT, intentConfig.isEnableInput());
        currentInputs.put(IntentDetectionConstants.EXAMPLE_CONTENT,
                String.join("\n\n", intentConfig.getExampleContent()));
        currentInputs.put(IntentDetectionConstants.CHAT_HISTORY_MAX_TURN,
                intentConfig.getChatHistoryMaxTurn());
        currentInputs.put(IntentDetectionConstants.CHAT_HISTORY, "");

        // Update chat history
        if (intentConfig.isEnableHistory() && contextEngine != null && session != null) {
            List<BaseMessage> chatHistory = getChatHistory(intentConfig.getChatHistoryMaxTurn());
            StringBuilder chatHistoryStr = new StringBuilder();
            for (BaseMessage msg : chatHistory) {
                String roleName = IntentDetectionConstants.ROLE_MAP.getOrDefault(msg.getRole(), "用户");
                chatHistoryStr.append(roleName).append(": ")
                        .append(msg.getContentAsString()).append("\n");
            }
            currentInputs.put(IntentDetectionConstants.CHAT_HISTORY, chatHistoryStr.toString());
        }

        // Process current input
        if (intentConfig.isEnableInput()) {
            String queryText = event.getContent() != null ? event.getContent().getQueryText() : "";
            currentInputs.put(IntentDetectionConstants.INPUT, queryText);
        }

        return currentInputs;
    }

    /**
     * Parse intent ID from LLM output.
     * Mirrors Python's {@code IntentDetector._parse_intent_from_output()}.
     */
    public String parseIntentFromOutput(String llmOutput) {
        try {
            String cleaned = JSON_FENCE_PATTERN.matcher(llmOutput.strip()).replaceAll("");
            cleaned = SINGLE_QUOTE_FENCE_PATTERN.matcher(cleaned).replaceAll("");

            // Simple JSON parsing for {"result": int}
            Pattern resultPattern = Pattern.compile("\"result\"\\s*:\\s*(\\d+)");
            Matcher matcher = resultPattern.matcher(cleaned);
            if (matcher.find()) {
                int number = Integer.parseInt(matcher.group(1));
                List<String> categories = intentConfig.getCategoryList();
                if (number <= 0 || number > categories.size()) {
                    LOG.warn("get unknown class");
                    return IntentDetectionConstants.DEFAULT_CLASS;
                }
                return categories.get(number - 1);
            }
        } catch (Exception e) {
            LOG.error("failed to parse JSON from LLM output");
        }
        return IntentDetectionConstants.DEFAULT_CLASS;
    }

    private List<Task> generateDefaultTask(Event event, String sessionId) {
        String taskId = sessionId + "_intent_" + UUID.randomUUID().toString().substring(0, 8);
        Task.TaskInput taskInput = new Task.TaskInput();
        taskInput.setTargetId("");
        taskInput.setTargetName("");
        taskInput.setArguments(event.getContent());

        Task task = Task.builder()
                .taskId(taskId)
                .taskType(TaskType.WORKFLOW)
                .input(taskInput)
                .build();

        List<Task> tasks = new ArrayList<>();
        tasks.add(task);
        return tasks;
    }

    private List<BaseMessage> getChatHistory(int maxTurn) {
        if (contextEngine == null || session == null) {
            return Collections.emptyList();
        }
        ModelContext ctx = contextEngine.getContext(null, session.getSessionId());
        if (ctx == null) {
            return Collections.emptyList();
        }
        List<BaseMessage> messages = ctx.getMessages();
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = 2 * maxTurn;
        if (messages.size() <= limit) {
            return messages;
        }
        return messages.subList(messages.size() - limit, messages.size());
    }
}
