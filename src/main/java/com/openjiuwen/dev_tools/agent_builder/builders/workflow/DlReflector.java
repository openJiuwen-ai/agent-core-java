/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            "longer_than", "longer_than_or_eq",
            "short_than", "short_than_or_eq",
            "is_empty", "is_not_empty"
    );

    /** Validation errors. */
    private final List<String> errors = new ArrayList<>();

    /** Seen node IDs. */
    private final List<String> nodeIds = new ArrayList<>();
    private final Set<String> nodeIdsOfNext = new LinkedHashSet<>();
    private final Set<String> availableNodeOutputs = new LinkedHashSet<>();

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
    public List<String> getNodeIds() {
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
        nodeIdsOfNext.clear();
        availableNodeOutputs.clear();
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
        try {
            if (dlContent == null || dlContent.isBlank()) {
                errors.add("JSON格式错误: DL content is empty");
                return;
            }

            List<Map<String, Object>> nodes = MAPPER.readValue(extractJson(dlContent), new TypeReference<>() {
            });

            for (int i = 0; i < nodes.size(); i++) {
                Map<String, Object> node = nodes.get(i);
                if (!basicCheck(node, i)) {
                    continue;
                }
                checkNode(node);
            }

            for (String nodeId : nodeIdsOfNext) {
                if (!nodeIds.contains(nodeId)) {
                    errors.add("节点ID错误: " + nodeId + " 不存在");
                }
            }
        } catch (Exception e) {
            errors.add("JSON格式错误: " + e.getMessage());
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

    private static String extractJson(String input) {
        String trimmed = input.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private boolean basicCheck(Map<String, Object> node, int index) {
        for (String key : List.of("id", "type", "description", "parameters")) {
            if (!node.containsKey(key)) {
                errors.add("第" + (index + 1) + " 个节点中缺失 '" + key + "' 属性");
                return false;
            }
        }

        String id = String.valueOf(node.get("id"));
        if (nodeIds.contains(id)) {
            errors.add("第" + (index + 1) + "个节点ID错误: " + id + " 已存在");
            return false;
        }
        nodeIds.add(id);

        String type = String.valueOf(node.get("type"));
        if (!AVAILABLE_NODE_TYPES.contains(type)) {
            errors.add("第" + (index + 1) + "个节点类型错误: " + type + " 不在可用节点类型中");
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void checkNode(Map<String, Object> node) {
        String type = String.valueOf(node.get("type"));
        switch (type) {
            case "Start" -> {
                checkOutputsList(node);
                checkStartQueryOutput(node);
                checkNext(node);
            }
            case "End" -> {
                checkInputsList(node);
                checkConfigs(node, List.of("template"));
            }
            case "Output" -> {
                checkInputsList(node);
                checkConfigs(node, List.of("template"));
                checkNext(node);
            }
            case "LLM" -> {
                checkInputsList(node);
                checkOutputsList(node);
                checkConfigs(node, List.of("system_prompt", "user_prompt"));
                checkNext(node);
            }
            case "Questioner" -> {
                checkInputsList(node);
                checkOutputsList(node);
                checkConfigs(node, List.of("prompt"));
                checkNext(node);
            }
            case "Plugin" -> {
                checkInputsList(node);
                checkOutputsList(node);
                checkConfigs(node, List.of("tool_id"));
                checkNext(node);
            }
            case "Code" -> {
                checkInputsList(node);
                checkOutputsList(node);
                checkConfigs(node, List.of("code"));
                checkNext(node);
            }
            case "Branch" -> checkBranchNode(node);
            case "IntentDetection" -> {
                checkInputsList(node);
                checkConfigs(node, List.of("prompt"));
                checkIntentConditionsList(node);
            }
            default -> {
                // basicCheck filters unknown types.
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parameters(Map<String, Object> node) {
        Object value = node.get("parameters");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'属性必须为字典类型");
        return new LinkedHashMap<>();
    }

    private void checkStartQueryOutput(Map<String, Object> node) {
        Object outputs = parameters(node).get("outputs");
        if (!(outputs instanceof List<?> outputList)) {
            return;
        }
        boolean hasQuery = outputList.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(item -> "query".equals(item.get("name"))
                        && "用户输入".equals(item.get("description")));
        if (!hasQuery) {
            errors.add("Start节点的'parameters'中的'outputs'列表中必须包含name为'query'且description为'用户输入'的输出参数");
        }
    }

    private void checkInputsList(Map<String, Object> node) {
        Object inputs = parameters(node).get("inputs");
        if (!(inputs instanceof List<?> inputList)) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'中缺失'inputs'属性");
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object item : inputList) {
            if (!(item instanceof Map<?, ?> input)) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'inputs'列表中的元素必须为字典类型");
                return;
            }
            Object name = input.get("name");
            if (name == null) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'inputs'列表中的元素缺失'name'属性");
            } else if (!names.add(String.valueOf(name))) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'inputs'列表中的元素'name'属性值必须唯一");
            }
            Object value = input.get("value");
            if (value instanceof String text) {
                List<String> refs = extractPlaceholderNames(text);
                if (refs.size() > 1) {
                    errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                            + ", 'parameters'中的'inputs'列表中的元素'value'属性值中有多个引用变量");
                } else if (refs.size() == 1 && !availableNodeOutputs.contains(refs.get(0))) {
                    errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                            + ", 'parameters'中的'inputs'列表中的元素'value'属性引用了不存在的变量");
                }
            }
        }
    }

    private void checkOutputsList(Map<String, Object> node) {
        Object outputs = parameters(node).get("outputs");
        if (!(outputs instanceof List<?> outputList)) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'中缺失'outputs'属性");
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object item : outputList) {
            if (!(item instanceof Map<?, ?> output)) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'outputs'列表中的元素必须为字典类型");
                return;
            }
            Object name = output.get("name");
            if (name == null) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'outputs'列表中的元素缺失'name'属性");
            } else {
                String outputName = String.valueOf(name);
                if (!names.add(outputName)) {
                    errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                            + ", 'parameters'中的'outputs'列表中的元素'name'属性值必须唯一");
                }
                availableNodeOutputs.add(node.get("id") + "." + outputName);
            }
            if (!output.containsKey("description")) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'outputs'列表中的元素缺失'description'属性");
            }
        }
    }

    private void checkConfigs(Map<String, Object> node, List<String> keys) {
        Object configs = parameters(node).get("configs");
        if (!(configs instanceof Map<?, ?> configMap)) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'中缺失'configs'属性");
            return;
        }
        for (String key : keys) {
            if (!configMap.containsKey(key)) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'configs'字典中缺失'" + key + "'属性");
            }
        }
    }

    private void checkNext(Map<String, Object> node) {
        if (!node.containsKey("next")) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 缺失'next'属性");
            return;
        }
        Object next = node.get("next");
        if (next != null && !String.valueOf(next).isBlank()) {
            nodeIdsOfNext.add(String.valueOf(next));
        }
    }

    private void checkBranchNode(Map<String, Object> node) {
        Object conditions = parameters(node).get("conditions");
        if (!(conditions instanceof List<?> conditionList)) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'中缺失'conditions'属性");
            return;
        }
        boolean hasDefault = false;
        for (Object item : conditionList) {
            if (!(item instanceof Map<?, ?> condition)) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'conditions'列表中的元素必须为字典类型");
                return;
            }
            if ("default".equals(condition.get("expression"))) {
                hasDefault = true;
            } else {
                checkBranchExpression(node, condition);
            }
            addConditionNext(node, condition);
        }
        if (!hasDefault) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'中的'conditions'列表中缺少default分支");
        }
    }

    private void checkBranchExpression(Map<String, Object> node, Map<?, ?> condition) {
        Object expression = condition.get("expression");
        if (!(expression instanceof String text)) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                    + ", 'parameters'中的'conditions'列表中的元素缺失'expression'或'expressions'属性");
            return;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length > 1 && !AVAILABLE_CONDITION_OPERATORS.contains(parts[1])) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                    + ", 'parameters'中的'conditions'列表中的元素的'expression'的表达式使用了不支持的关系运算符");
        }
        for (String ref : extractPlaceholderNames(text)) {
            if (!availableNodeOutputs.contains(ref)) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'conditions'列表中的元素的'expression'的表达式中引用了不存在的变量");
            }
        }
    }

    private void checkIntentConditionsList(Map<String, Object> node) {
        Object conditions = parameters(node).get("conditions");
        if (!(conditions instanceof List<?> conditionList)) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'中缺失'conditions'属性");
            return;
        }
        boolean hasDefault = false;
        String expectedRef = "${" + node.get("id") + ".rawOutput}";
        for (Object item : conditionList) {
            if (!(item instanceof Map<?, ?> condition)) {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'conditions'列表中的元素必须为字典类型");
                return;
            }
            Object expression = condition.get("expression");
            if ("default".equals(expression)) {
                hasDefault = true;
            } else if (expression instanceof String text) {
                if (!text.contains(expectedRef)) {
                    errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                            + ", 'parameters'中的'conditions'列表中的元素的'expression'的表达式变量错误");
                }
                if (!text.contains("contain")) {
                    errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                            + ", 'parameters'中的'conditions'列表中的元素的'expression'的表达式必须使用contain");
                }
            } else {
                errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                        + ", 'parameters'中的'conditions'列表中的元素缺失'expression'属性");
            }
            addConditionNext(node, condition);
        }
        if (!hasDefault) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type") + ", 'parameters'中的'conditions'列表中缺少default分支");
        }
    }

    private void addConditionNext(Map<String, Object> node, Map<?, ?> condition) {
        Object next = condition.get("next");
        if (next == null || String.valueOf(next).isBlank()) {
            errors.add(node.get("id") + "节点, 类型为" + node.get("type")
                    + ", 'parameters'中的'conditions'列表中的元素缺失'next'属性");
            return;
        }
        nodeIdsOfNext.add(String.valueOf(next));
    }
}
