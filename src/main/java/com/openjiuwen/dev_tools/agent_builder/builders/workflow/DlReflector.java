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
 * DL reflector — validates DL format and detects errors.
 * <p>
 * Mirrors Python's {@code Reflector} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_reflector}.
 */
public class DlReflector {

    private static final Logger LOG = LoggerFactory.getLogger(DlReflector.class);

    /** Pattern for placeholder extraction: ${node_id.variable} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /** Available node types. */
    public static final Set<String> AVAILABLE_NODE_TYPES = Set.of(
            "Start", "End", "Output", "LLM", "Questioner",
            "Plugin", "Code", "Branch", "IntentDetection"
    );

    /** Available variable types. */
    public static final Set<String> AVAILABLE_VARIABLE_TYPES = Set.of(
            "String", "Integer", "Number", "Boolean", "Object",
            "Array<String>", "Array<Integer>", "Array<Number>",
            "Array<Boolean>", "Array<Object>"
    );

    /** Available condition operators. */
    public static final Set<String> AVAILABLE_CONDITION_OPERATORS = Set.of(
            "eq", "not_eq", "contain", "not_contain",
            "longer_than", "longer_equal_than", "shorter_than", "shorter_equal_than"
    );

    /** Validation errors. */
    private final List<String> errors = new ArrayList<>();

    /** Seen node IDs. */
    private final Set<String> nodeIds = new HashSet<>();

    /**
     * Get validation errors.
     *
     * @return List of error messages
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Get seen node IDs.
     *
     * @return Set of node IDs
     */
    public Set<String> getNodeIds() {
        return nodeIds;
    }

    /**
     * Reset reflector state.
     * <p>
     * Mirrors Python's {@code reset} method.
     */
    public void reset() {
        errors.clear();
        nodeIds.clear();
        LOG.debug("DlReflector state reset");
    }

    /**
     * Check if text contains placeholder.
     * <p>
     * Mirrors Python's {@code extract_placeholder_content} function.
     *
     * @param text Input text
     * @return true if contains placeholder
     */
    public static boolean hasPlaceholder(String text) {
        return text != null && text.contains("${");
    }

    /**
     * Extract placeholder names from text.
     * <p>
     * Mirrors Python's {@code extract_placeholder_content} function.
     *
     * @param text Input text
     * @return List of placeholder names (e.g., "node_start.query")
     */
    public static List<String> extractPlaceholderNames(String text) {
        List<String> matches = new ArrayList<>();
        if (text == null) return matches;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }

        return matches;
    }

    /**
     * Extract placeholder content tuple.
     * <p>
     * Mirrors Python's {@code extract_placeholder_content} function return signature.
     *
     * @param text Input text
     * @return Tuple of (hasPlaceholder, placeholderNames)
     */
    public static PlaceholderResult extractPlaceholderContent(String text) {
        List<String> names = extractPlaceholderNames(text);
        return new PlaceholderResult(names.size() > 0, names);
    }

    /**
     * Placeholder extraction result.
     */
    public static class PlaceholderResult {
        private final boolean hasPlaceholder;
        private final List<String> matches;

        public PlaceholderResult(boolean hasPlaceholder, List<String> matches) {
            this.hasPlaceholder = hasPlaceholder;
            this.matches = matches;
        }

        public boolean hasPlaceholder() {
            return hasPlaceholder;
        }

        public List<String> getMatches() {
            return matches;
        }
    }

    /**
     * Check DL format validity.
     * <p>
     * Mirrors Python's {@code check_format} method.
     *
     * @param dlContent DL content string (JSON format)
     */
    public void checkFormat(String dlContent) {
        if (dlContent == null || dlContent.isBlank()) {
            errors.add("DL content is empty");
            return;
        }

        // Try to parse as JSON
        try {
            // Simplified parsing - check for basic structure
            if (!dlContent.trim().startsWith("[") && !dlContent.trim().startsWith("{")) {
                errors.add("DL content should be JSON array or object");
                return;
            }

            // Check for node structure
            // This is a simplified check - full implementation would use JSON parser
            if (dlContent.contains("\"id\"") && dlContent.contains("\"type\"")) {
                LOG.debug("DL format check passed basic validation");
            } else {
                errors.add("DL content missing required fields: id or type");
            }

        } catch (Exception e) {
            errors.add("Failed to parse DL content: " + e.getMessage());
            LOG.warn("DL format check failed", e);
        }
    }

    /**
     * Check node types are valid.
     *
     * @param nodeType Node type string
     * @return true if valid
     */
    public boolean isValidNodeType(String nodeType) {
        return AVAILABLE_NODE_TYPES.contains(nodeType);
    }

    /**
     * Check variable type is valid.
     *
     * @param varType Variable type string
     * @return true if valid
     */
    public boolean isValidVariableType(String varType) {
        return AVAILABLE_VARIABLE_TYPES.contains(varType);
    }

    /**
     * Check condition operator is valid.
     *
     * @param operator Condition operator string
     * @return true if valid
     */
    public boolean isValidConditionOperator(String operator) {
        return AVAILABLE_CONDITION_OPERATORS.contains(operator);
    }

    /**
     * Add validation error.
     *
     * @param error Error message
     */
    public void addError(String error) {
        errors.add(error);
        LOG.warn("Added validation error: {}", error);
    }

    /**
     * Check if validation passed.
     *
     * @return true if no errors
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
}