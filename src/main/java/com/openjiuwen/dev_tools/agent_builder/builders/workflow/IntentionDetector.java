/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // Parse JSON (simplified implementation)
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // Check for common intent fields
            if (jsonStr.contains("provide_process")) {
                boolean value = jsonStr.contains("true") || jsonStr.contains("True");
                result.put("provide_process", value);
            }
            if (jsonStr.contains("need_refined")) {
                boolean value = jsonStr.contains("true") || jsonStr.contains("True");
                result.put("need_refined", value);
            }
            if (jsonStr.contains("has_instruction")) {
                boolean value = jsonStr.contains("true") || jsonStr.contains("True");
                result.put("has_instruction", value);
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract intent from: {}", inputs, e);
        }

        return result;
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

        // Simplified detection: check if any message has substantial content
        String formattedHistory = formatDialogHistory(messages);
        if (formattedHistory.length() > 50) {
            return true;
        }

        // Use LLM for detection if available (placeholder)
        if (llm != null) {
            // TODO: Implement full LLM-based detection
            LOG.debug("LLM-based intent detection not yet implemented");
        }

        return false;
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

        // Simplified detection: check for refinement keywords in last message
        Map<String, Object> lastMessage = messages.get(messages.size() - 1);
        Object content = lastMessage.get(CONTENT);
        if (content != null) {
            String input = content.toString().toLowerCase();
            return input.contains("修改") || input.contains("modify") ||
                   input.contains("调整") || input.contains("优化") ||
                   input.contains("refine");
        }

        return false;
    }
}