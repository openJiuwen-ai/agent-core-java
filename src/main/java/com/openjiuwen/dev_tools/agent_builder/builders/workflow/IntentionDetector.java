/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intention detector for workflow builder.
 * <p>
 * Detects user intent, determines whether process description is provided
 * or workflow needs refinement.
 * <p>
 * Mirrors Python's {@code IntentionDetector} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.intention_detector}.
 */
public class IntentionDetector {

    private static final Logger LOG = LoggerFactory.getLogger(IntentionDetector.class);

    /** Role field name in dialog history. */
    public static final String ROLE = "role";
    /** Content field name in dialog history. */
    public static final String CONTENT = "content";

    /** Role display name mapping. */
    private static final Map<String, String> ROLE_MAP = new LinkedHashMap<>();
    static {
        ROLE_MAP.put("user", "User");
        ROLE_MAP.put("assistant", "Assistant");
        ROLE_MAP.put("system", "System");
    }

    /** Pattern for extracting JSON from LLM response. */
    private static final Pattern JSON_PATTERN = Pattern.compile("```json\\s*\\n?\\s*([^`]*?)\\s*\\n?```", Pattern.DOTALL);

    /** Intent types. */
    public enum Intention {
        CREATE_WORKFLOW, MODIFY_WORKFLOW, REFINE_WORKFLOW, UNKNOWN
    }

    /** LLM service instance. */
    private final Object llm;

    /**
     * Initialize intention detector.
     * <p>
     * Mirrors Python's {@code __init__} method.
     *
     * @param llm LLM service instance
     */
    public IntentionDetector(Object llm) {
        this.llm = llm;
    }

    /**
     * Initialize intention detector without LLM.
     * Uses simplified detection logic.
     */
    public IntentionDetector() {
        this.llm = null;
    }

    public Object getLlm() {
        return llm;
    }

    /**
     * Detect intention from user input.
     * <p>
     * Simplified detection without LLM call.
     *
     * @param userInput User input string
     * @return Detected intention type
     */
    public Intention detect(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Intention.UNKNOWN;
        }

        String lowerInput = userInput.toLowerCase();

        // Detect creation intent
        if (lowerInput.contains("创建") || lowerInput.contains("create") ||
            lowerInput.contains("新建") || lowerInput.contains("生成")) {
            return Intention.CREATE_WORKFLOW;
        }

        // Detect modification intent
        if (lowerInput.contains("修改") || lowerInput.contains("modify") ||
            lowerInput.contains("调整") || lowerInput.contains("变更") ||
            lowerInput.contains("优化") || lowerInput.contains("完善")) {
            return Intention.MODIFY_WORKFLOW;
        }

        // Detect refinement intent
        if (lowerInput.contains("细化") || lowerInput.contains("refine") ||
            lowerInput.contains("补充") || lowerInput.contains("补充细节")) {
            return Intention.REFINE_WORKFLOW;
        }

        return Intention.UNKNOWN;
    }

    /**
     * Format dialog history.
     * <p>
     * Mirrors Python's {@code format_dialog_history} method.
     *
     * @param dialogHistory Dialog history list
     * @return Formatted dialog history string
     */
    public static String formatDialogHistory(List<Map<String, Object>> dialogHistory) {
        if (dialogHistory == null || dialogHistory.isEmpty()) {
            return "";
        }

        List<String> formattedLines = new ArrayList<>();
        for (Map<String, Object> msg : dialogHistory) {
            String role = (String) msg.get(ROLE);
            Object content = msg.get(CONTENT);
            String roleDisplay = ROLE_MAP.getOrDefault(role, "User");
            formattedLines.add(roleDisplay + ": " + (content != null ? content.toString() : ""));
        }

        return String.join("\n", formattedLines);
    }

    /**
     * Extract intent from LLM response.
     * <p>
     * Mirrors Python's {@code extract_intent} method.
     *
     * @param inputs Text returned by LLM
     * @return Intent judgment result dictionary
     */
    public static Map<String, Object> extractIntent(String inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new LinkedHashMap<>();
        }

        // Extract JSON from response
        String jsonStr = extractJsonFromText(inputs);
        if (jsonStr == null || jsonStr.isEmpty()) {
            // Try to parse directly if no code block
            jsonStr = inputs;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = JsonUtils.safeJsonLoads(jsonStr.trim(), Map.class, new LinkedHashMap<>());
        return parsed == null ? new LinkedHashMap<>() : parsed;
    }

    /**
     * Extract JSON string from text (removes code block markers).
     *
     * @param text Input text containing potential JSON
     * @return Extracted JSON string
     */
    private static String extractJsonFromText(String text) {
        if (text == null) return null;

        Matcher matcher = JSON_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // If no code block, try to find JSON object directly
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return text;
    }

    /**
     * Detect whether initial process description is provided.
     * <p>
     * Mirrors Python's {@code detect_initial_instruction} method.
     *
     * @param messages Dialog history list
     * @return True if process description is provided
     */
    public boolean detectInitialInstruction(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        String formattedHistory = formatDialogHistory(messages);
        String modelResponse = invokeForContent(
                Prompts.INITIAL_INTENTION_SYSTEM_PROMPT,
                Prompts.formatInitialIntentionUserTemplate(formattedHistory)
        );
        return Boolean.TRUE.equals(extractIntent(modelResponse).getOrDefault("provide_process", false));
    }

    /**
     * Detect whether workflow needs refinement.
     * <p>
     * Mirrors Python's {@code detect_refine_intent} method.
     *
     * @param messages       Dialog history list
     * @param flowchartCode  Current Mermaid flowchart code
     * @return True if refinement is needed
     */
    public boolean detectRefineIntent(List<Map<String, Object>> messages, String flowchartCode) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        String formattedHistory = formatDialogHistory(messages);
        String modelResponse = invokeForContent(
                Prompts.REFINE_INTENTION_SYSTEM_PROMPT,
                Prompts.formatRefineIntentionUserTemplate(flowchartCode, formattedHistory)
        );
        return Boolean.TRUE.equals(extractIntent(modelResponse).getOrDefault("need_refined", false));
    }

    private String invokeForContent(String systemPrompt, String userPrompt) {
        if (llm == null) {
            throw new IllegalStateException("LLM service is required for workflow intention detection");
        }

        List<Map<String, Object>> messages = List.of(
                Map.of(ROLE, "system", CONTENT, systemPrompt),
                Map.of(ROLE, "user", CONTENT, userPrompt)
        );

        try {
            return invokeLlmContent(llm, messages);
        } catch (Exception e) {
            throw new IllegalStateException("Workflow intention detection failed: " + e.getMessage(), e);
        }
    }

    public static String invokeLlmContent(Object llm, Object messages) throws Exception {
        Object response = llm instanceof Model model
                ? model.invoke(messages, null, null, null, null, null, null, null, null, null)
                : invokeViaReflection(llm, messages);
        return extractContent(response);
    }

    private static Object invokeViaReflection(Object llm, Object messages) throws ReflectiveOperationException {
        for (Method method : llm.getClass().getMethods()) {
            if ("invoke".equals(method.getName()) && method.getParameterCount() == 1) {
                return method.invoke(llm, messages);
            }
        }
        throw new NoSuchMethodException("invoke(Object)");
    }

    private static String extractContent(Object response) throws ReflectiveOperationException {
        if (response == null) {
            return "";
        }
        if (response instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getContentAsString();
        }
        if (response instanceof CharSequence text) {
            return text.toString();
        }
        if (response instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (CONTENT.equals(entry.getKey())) {
                    return Objects.toString(entry.getValue(), "");
                }
            }
            return "";
        }

        try {
            Method method = response.getClass().getMethod("getContentAsString");
            return Objects.toString(method.invoke(response), "");
        } catch (NoSuchMethodException ignored) {
            Method method = response.getClass().getMethod("getContent");
            return Objects.toString(method.invoke(response), "");
        }
    }
}
