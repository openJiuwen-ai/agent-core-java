/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detects whether a dialog contains workflow instructions or refinement intent.
 *
 * <p>Mirrors Python's {@code IntentionDetector} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/intention_detector.py}.</p>
 */
public class IntentionDetector {

    public static final String ROLE = "role";
    public static final String CONTENT = "content";
    public static final Map<String, String> ROLE_MAP = Map.of(
            "user", "User",
            "assistant", "Assistant",
            "system", "System"
    );

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    private final Model llm;

    public IntentionDetector(Model llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public static String formatDialogHistory(List<Map<String, Object>> dialogHistory) {
        List<String> formattedLines = new ArrayList<>();
        for (Map<String, Object> message : dialogHistory) {
            Object role = message.get(ROLE);
            Object content = message.get(CONTENT);
            String roleDisplay = ROLE_MAP.getOrDefault(String.valueOf(role), "User");
            formattedLines.add(roleDisplay + ": " + pythonString(content));
        }
        return String.join("\n", formattedLines);
    }

    public static Map<String, Object> extractIntent(String inputs) {
        String json = AgentBuilderUtils.extractJsonFromText(inputs);
        Object parsed = JsonUtils.safeJsonLoads(json);
        if (!(parsed instanceof Map<?, ?> resultMap)) {
            throw new IllegalArgumentException("intent JSON must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        resultMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    public boolean detectInitialInstruction(List<Map<String, Object>> messages) {
        try {
            if (messages == null || messages.isEmpty()) {
                return false;
            }

            String formattedHistory = formatDialogHistory(messages);
            List<BaseMessage> userMessages = WorkflowPrompts.INITIAL_INTENTION_USER_TEMPLATE
                    .format(Map.of("dialog_history", formattedHistory))
                    .toMessages();
            List<BaseMessage> llmMessages = new ArrayList<>();
            llmMessages.add(new SystemMessage(WorkflowPrompts.INITIAL_INTENTION_SYSTEM_PROMPT));
            llmMessages.addAll(userMessages);

            AssistantMessage response = llm.invoke(llmMessages).toCompletableFuture().join();
            Map<String, Object> operationResult = extractIntent(response.getContentAsString());
            return pythonTruth(operationResult.getOrDefault("provide_process", Boolean.FALSE));
        } catch (Exception exception) {
            LOGGER.error("Intent detection failed: {}", exception.getMessage());
            throw applicationError("Process intent judgment exception: " + exception.getMessage(), exception);
        }
    }

    public boolean detectRefineIntent(List<Map<String, Object>> messages, String flowchartCode) {
        try {
            if (messages == null || messages.isEmpty()) {
                return false;
            }

            String formattedHistory = formatDialogHistory(messages);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("mermaid_code", flowchartCode);
            values.put("dialog_history", formattedHistory);
            List<BaseMessage> userMessages = WorkflowPrompts.REFINE_INTENTION_USER_TEMPLATE
                    .format(values)
                    .toMessages();
            List<BaseMessage> llmMessages = new ArrayList<>();
            llmMessages.add(new SystemMessage(WorkflowPrompts.REFINE_INTENTION_SYSTEM_PROMPT));
            llmMessages.addAll(userMessages);

            AssistantMessage response = llm.invoke(llmMessages).toCompletableFuture().join();
            Map<String, Object> operationResult = extractIntent(response.getContentAsString());
            return pythonTruth(operationResult.getOrDefault("need_refined", Boolean.FALSE));
        } catch (Exception exception) {
            LOGGER.error("Refinement intent detection failed: {}", exception.getMessage());
            throw applicationError("Process intent judgment exception: " + exception.getMessage(), exception);
        }
    }

    private static ApplicationError applicationError(String message, Exception cause) {
        return new ApplicationError(
                StatusCode.AGENT_CONTROLLER_INTENT_PARAM_ERROR,
                message,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }

    private static boolean pythonTruth(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static String pythonString(Object value) {
        return value == null ? "None" : String.valueOf(value);
    }
}
