/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DL reflector for validating DL format correctness.
 * <p>
 * Mirrors Python's {@code Reflector} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_reflector.py}.
 * </p>
 */
public class Reflector {

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final Set<String> availableNodeTypes = Set.of(
            "Start", "End", "Output", "LLM", "Questioner", "Plugin", "Code", "Branch", "IntentDetection");
    private final Set<String> availableVariableTypes = Set.of(
            "String", "Integer", "Number", "Boolean", "Object",
            "Array<String>", "Array<Integer>", "Array<Number>",
            "Array<Boolean>", "Array<Object>");
    private final Set<String> availableConditionOperators = Set.of(
            "eq", "not_eq", "contain", "not_contain",
            "longer_than", "longer_than_or_eq",
            "short_than", "short_than_or_eq",
            "is_empty", "is_not_empty");

    private final Map<String, Consumer<Map<String, Object>>> checkFunctions = new LinkedHashMap<>();
    private final Set<String> availableNodeOutputs = new LinkedHashSet<>();
    private final List<String> nodeIds = new ArrayList<>();
    private final Set<String> nodeIdsOfNext = new LinkedHashSet<>();
    private final List<String> errors = new ArrayList<>();

    public Reflector() {
        checkFunctions.put("Start", this::checkStartNode);
        checkFunctions.put("End", this::checkEndNode);
        checkFunctions.put("Output", this::checkOutputNode);
        checkFunctions.put("LLM", this::checkLlmNode);
        checkFunctions.put("Questioner", this::checkQuestionerNode);
        checkFunctions.put("Plugin", this::checkPluginNode);
        checkFunctions.put("Code", this::checkCodeNode);
        checkFunctions.put("Branch", this::checkBranchNode);
        checkFunctions.put("IntentDetection", this::checkIntentDetectionNode);
    }

    public static PlaceholderContent extractPlaceholderContent(String input) {
        List<String> matches = new ArrayList<>();
        if (input != null) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
            while (matcher.find()) {
                matches.add(matcher.group(1));
            }
        }
        return new PlaceholderContent(!matches.isEmpty(), matches);
    }

    public void checkFormat(String generatedDl) {
        Object loaded;
        try {
            String jsonText = AgentBuilderUtils.extractJsonFromText(generatedDl);
            loaded = JsonUtils.safeJsonLoads(jsonText);
            if (!(loaded instanceof List<?>)) {
                throw new IllegalArgumentException("DL 格式错误：期望 JSON 数组（list），得到 "
                        + (loaded == null ? "null" : loaded.getClass()));
            }
        } catch (Exception exception) {
            errors.add("JSON格式错误: " + exception.getMessage());
            LOGGER.warning("DL JSON格式错误: {}", exception.getMessage());
            return;
        }

        List<?> generatedDlList = (List<?>) loaded;
        for (int index = 0; index < generatedDlList.size(); index++) {
            Object rawNode = generatedDlList.get(index);
            if (!(rawNode instanceof Map<?, ?> rawNodeMap)) {
                errors.add("Node " + (index + 1) + " type error: must be dict type!");
                continue;
            }
            Map<String, Object> nodeContent = toStringObjectMap(rawNodeMap);
            boolean basicHasError = basicCheck(nodeContent, index);
            if (basicHasError) {
                continue;
            }
            checkFunctions.get(String.valueOf(nodeContent.get("type"))).accept(nodeContent);
        }

        for (String nodeId : nodeIdsOfNext) {
            if (!nodeIds.contains(nodeId)) {
                errors.add("节点ID错误: " + nodeId + " 不存在");
            }
        }
    }

    public void reset() {
        availableNodeOutputs.clear();
        nodeIds.clear();
        nodeIdsOfNext.clear();
        errors.clear();
    }

    public List<String> getErrors() {
        return errors;
    }

    public Set<String> getAvailableNodeOutputs() {
        return availableNodeOutputs;
    }

    public Set<String> getAvailableNodeTypes() {
        return availableNodeTypes;
    }

    public Set<String> getAvailableVariableTypes() {
        return availableVariableTypes;
    }

    public Set<String> getAvailableConditionOperators() {
        return availableConditionOperators;
    }

    public List<String> getNodeIds() {
        return nodeIds;
    }

    public Set<String> getNodeIdsOfNext() {
        return nodeIdsOfNext;
    }

    private boolean basicCheck(Map<String, Object> nodeContent, int nodeIndex) {
        for (String key : List.of("id", "type", "description", "parameters")) {
            if (!nodeContent.containsKey(key)) {
                errors.add("第" + (nodeIndex + 1) + " 个节点中缺失 '" + key + "' 属性");
                return true;
            }
        }

        String nodeId = String.valueOf(nodeContent.get("id"));
        if (nodeIds.contains(nodeId)) {
            errors.add("第" + (nodeIndex + 1) + "个节点ID错误: " + nodeId + " 已存在");
            return true;
        }
        nodeIds.add(nodeId);

        String nodeType = String.valueOf(nodeContent.get("type"));
        if (!availableNodeTypes.contains(nodeType)) {
            errors.add("第" + (nodeIndex + 1) + "个节点类型错误: " + nodeType + " 不在可用节点类型中");
            return true;
        }
        return false;
    }

    private void checkStartNode(Map<String, Object> nodeContent) {
        checkOutputsList(nodeContent, false);
        if (errors.isEmpty()) {
            boolean hasQuery = false;
            for (Object outputItem : listValue(parameters(nodeContent).get("outputs"))) {
                if (outputItem instanceof Map<?, ?> outputMap
                        && "query".equals(String.valueOf(outputMap.get("name")))
                        && "用户输入".equals(String.valueOf(outputMap.get("description")))) {
                    hasQuery = true;
                    break;
                }
            }
            if (!hasQuery) {
                errors.add("Start节点的'parameters'中的'outputs'列表中必须包含name为'query'且description为'用户输入'的输出参数");
            }
        }
        checkNextMissing(nodeContent);
    }

    private void checkEndNode(Map<String, Object> nodeContent) {
        checkInputsList(nodeContent, false);
        checkConfigs(nodeContent, List.of("template"));
    }

    private void checkOutputNode(Map<String, Object> nodeContent) {
        checkInputsList(nodeContent, false);
        checkConfigs(nodeContent, List.of("template"));
        checkNextMissing(nodeContent);
    }

    private void checkLlmNode(Map<String, Object> nodeContent) {
        checkInputsList(nodeContent, false);
        checkOutputsList(nodeContent, false);
        checkConfigs(nodeContent, List.of("system_prompt", "user_prompt"));
        checkNextMissing(nodeContent);
    }

    private void checkQuestionerNode(Map<String, Object> nodeContent) {
        checkInputsList(nodeContent, false);
        checkOutputsList(nodeContent, false);
        checkConfigs(nodeContent, List.of("prompt"));
        checkNextMissing(nodeContent);
    }

    private void checkPluginNode(Map<String, Object> nodeContent) {
        checkInputsList(nodeContent, false);
        checkOutputsList(nodeContent, false);
        checkConfigs(nodeContent, List.of("tool_id"));
        checkNextMissing(nodeContent);
    }

    private void checkCodeNode(Map<String, Object> nodeContent) {
        checkInputsList(nodeContent, false);
        checkOutputsList(nodeContent, false);
        checkConfigs(nodeContent, List.of("code"));
        checkNextMissing(nodeContent);
    }

    private void checkIntentDetectionNode(Map<String, Object> nodeContent) {
        checkInputsList(nodeContent, false);
        checkConfigs(nodeContent, List.of("prompt"));
        checkIntentConditionsList(nodeContent);
    }

    private void checkIntentConditionsList(Map<String, Object> nodeContent) {
        Map<String, Object> parameters = parameters(nodeContent);
        if (!parameters.containsKey("conditions")) {
            errors.add(prefix(nodeContent) + "'parameters'中缺失'conditions'属性");
            return;
        }
        Object rawConditions = parameters.get("conditions");
        if (!(rawConditions instanceof List<?> conditions)) {
            errors.add(prefix(nodeContent) + "'parameters'中的'conditions'属性必须为列表类型");
            return;
        }

        String nodeId = String.valueOf(nodeContent.get("id"));
        boolean hasDefaultBranch = false;
        for (Object rawCondition : conditions) {
            if (!(rawCondition instanceof Map<?, ?> rawConditionMap)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素必须为字典类型");
                return;
            }
            Map<String, Object> condition = toStringObjectMap(rawConditionMap);
            for (String key : List.of("branch", "description")) {
                if (!condition.containsKey(key)) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素缺失'" + key + "'属性");
                }
            }
            if (!condition.containsKey("next")) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素缺失'next'属性");
            } else {
                nodeIdsOfNext.add(String.valueOf(condition.get("next")));
            }

            if (!condition.containsKey("expression")) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素缺失'expression'属性");
                continue;
            }
            Object expressionRaw = condition.get("expression");
            if (!(expressionRaw instanceof String expression)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素的'expression'属性必须为字符串类型");
                return;
            }
            if ("default".equals(expression)) {
                hasDefaultBranch = true;
            } else {
                String leftVal = "${" + nodeId + ".rawOutput}";
                if (!expression.contains(leftVal)) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素的'expression'的表达式变量错误");
                }
                if (!expression.contains("contain")) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素的'expression'的表达式必须使用contain");
                }
            }
        }
        if (!hasDefaultBranch) {
            errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中缺少default分支");
        }
    }

    private void checkBranchNode(Map<String, Object> nodeContent) {
        Map<String, Object> parameters = parameters(nodeContent);
        if (!parameters.containsKey("conditions")) {
            errors.add(prefix(nodeContent) + "'parameters'中缺失'conditions'属性");
            return;
        }
        Object rawConditions = parameters.get("conditions");
        if (!(rawConditions instanceof List<?> conditions)) {
            errors.add(prefix(nodeContent) + "'parameters'中的'conditions'属性必须为列表类型");
            return;
        }

        boolean hasDefaultBranch = false;
        for (Object rawCondition : conditions) {
            if (!(rawCondition instanceof Map<?, ?> rawConditionMap)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素必须为字典类型");
                return;
            }
            Map<String, Object> condition = toStringObjectMap(rawConditionMap);
            for (String key : List.of("branch", "description")) {
                if (!condition.containsKey(key)) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素缺失'" + key + "'属性");
                }
            }
            if ("default".equals(condition.get("expression"))) {
                hasDefaultBranch = true;
            } else {
                checkBranchExpression(condition, nodeContent);
            }
            if (!condition.containsKey("next")) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素缺失'next'属性");
            } else {
                nodeIdsOfNext.add(String.valueOf(condition.get("next")));
            }
        }
        if (!hasDefaultBranch) {
            errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中缺少default分支");
        }
    }

    private void checkBranchExpression(Map<String, Object> conditionBranch, Map<String, Object> nodeContent) {
        if (conditionBranch.containsKey("expression")) {
            Object expressionRaw = conditionBranch.get("expression");
            if (!(expressionRaw instanceof String expression)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素的'expression'属性必须为字符串类型");
            } else {
                checkBranchOperator(expression, nodeContent);
                checkPlaceholdersExist(expression, nodeContent);
            }
        } else if (conditionBranch.containsKey("expressions")) {
            Object expressionsRaw = conditionBranch.get("expressions");
            if (!(expressionsRaw instanceof List<?> expressions)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素的'expression'属性必须为列表类型");
            } else {
                for (Object expression : expressions) {
                    String expressionText = String.valueOf(expression);
                    checkBranchOperator(expressionText, nodeContent);
                    checkPlaceholdersExist(expressionText, nodeContent);
                }
            }
        } else {
            errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素缺失'expression'或'expressions'属性");
        }
    }

    private void checkBranchOperator(String expression, Map<String, Object> nodeContent) {
        String[] expressionList = expression.strip().split("\\s+");
        if (expressionList.length < 2 || !availableConditionOperators.contains(expressionList[1])) {
            errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素的'expression'的表达式使用了不支持的关系运算符");
        }
    }

    private void checkPlaceholdersExist(String expression, Map<String, Object> nodeContent) {
        PlaceholderContent placeholderContent = extractPlaceholderContent(expression);
        for (String content : placeholderContent.matches()) {
            if (!availableNodeOutputs.contains(content)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'conditions'列表中的元素的'expression'的表达式中引用了不存在的变量");
            }
        }
    }

    private void checkInputsList(Map<String, Object> nodeContent, boolean checkType) {
        Map<String, Object> parameters = parameters(nodeContent);
        if (!parameters.containsKey("inputs")) {
            errors.add(prefix(nodeContent) + "'parameters'中缺失'inputs'属性");
            return;
        }
        Object rawInputs = parameters.get("inputs");
        if (!(rawInputs instanceof List<?> inputs)) {
            errors.add(prefix(nodeContent) + "'parameters'中的'inputs'属性必须为列表类型");
            return;
        }

        Set<String> inputNames = new LinkedHashSet<>();
        for (Object rawInput : inputs) {
            if (!(rawInput instanceof Map<?, ?> rawInputMap)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素必须为字典类型");
                return;
            }
            Map<String, Object> inputItem = toStringObjectMap(rawInputMap);
            if (!inputItem.containsKey("name")) {
                errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素缺失'name'属性");
            } else {
                String name = String.valueOf(inputItem.get("name"));
                if (inputNames.contains(name)) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素'name'属性值必须唯一");
                }
                inputNames.add(name);
            }

            if (!inputItem.containsKey("value")) {
                errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素缺失'value'属性");
            } else {
                PlaceholderContent placeholderContent = extractPlaceholderContent(String.valueOf(inputItem.get("value")));
                if (placeholderContent.hasPlaceholder()) {
                    if (placeholderContent.matches().size() > 1) {
                        errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素'value'属性值中有多个引用变量");
                    } else if (!availableNodeOutputs.contains(placeholderContent.matches().getFirst())) {
                        errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素'value'属性引用了不存在的变量");
                    }
                }
            }

            if (checkType) {
                if (!inputItem.containsKey("type")) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素缺失'type'属性");
                } else if (!availableVariableTypes.contains(String.valueOf(inputItem.get("type")))) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'inputs'列表中的元素'type'属性值必须为"
                            + availableVariableTypes + "中的一个");
                }
            }
        }
    }

    private void checkOutputsList(Map<String, Object> nodeContent, boolean checkType) {
        Map<String, Object> parameters = parameters(nodeContent);
        if (!parameters.containsKey("outputs")) {
            errors.add(prefix(nodeContent) + "'parameters'中缺失'outputs'属性");
            return;
        }
        Object rawOutputs = parameters.get("outputs");
        if (!(rawOutputs instanceof List<?> outputs)) {
            errors.add(prefix(nodeContent) + "'parameters'中的'outputs'属性必须为列表类型");
            return;
        }

        Set<String> outputNames = new LinkedHashSet<>();
        for (Object rawOutput : outputs) {
            if (!(rawOutput instanceof Map<?, ?> rawOutputMap)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'outputs'列表中的元素必须为字典类型");
                return;
            }
            Map<String, Object> outputItem = toStringObjectMap(rawOutputMap);
            if (!outputItem.containsKey("name")) {
                errors.add(prefix(nodeContent) + "'parameters'中的'outputs'列表中的元素缺失'name'属性");
            } else {
                String name = String.valueOf(outputItem.get("name"));
                if (outputNames.contains(name)) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'outputs'列表中的元素'name'属性值必须唯一");
                }
                outputNames.add(name);
                availableNodeOutputs.add(String.valueOf(nodeContent.get("id")) + "." + name);
            }
            if (!outputItem.containsKey("description")) {
                errors.add(prefix(nodeContent) + "'parameters'中的'outputs'列表中的元素缺失'description'属性");
            }
            if (checkType) {
                if (!outputItem.containsKey("type")) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'outputs'列表中的元素缺失'type'属性");
                } else if (!availableVariableTypes.contains(String.valueOf(outputItem.get("type")))) {
                    errors.add(prefix(nodeContent) + "'parameters'中的'outputs'列表中的元素'type'属性值必须为"
                            + availableVariableTypes + "中的一个");
                }
            }
        }
    }

    private void checkConfigs(Map<String, Object> nodeContent, List<String> keys) {
        Map<String, Object> parameters = parameters(nodeContent);
        if (!parameters.containsKey("configs")) {
            errors.add(prefix(nodeContent) + "'parameters'中缺失'configs'属性");
            return;
        }
        Object rawConfigs = parameters.get("configs");
        if (!(rawConfigs instanceof Map<?, ?> configs)) {
            errors.add(prefix(nodeContent) + "'parameters'中的'configs'属性必须为字典类型");
            return;
        }
        for (String key : keys) {
            if (!configs.containsKey(key)) {
                errors.add(prefix(nodeContent) + "'parameters'中的'configs'字典中缺失'" + key + "'属性");
            }
        }
    }

    private void checkNextMissing(Map<String, Object> nodeContent) {
        if (!nodeContent.containsKey("next")) {
            errors.add(prefix(nodeContent) + "缺失'next'属性");
        } else {
            nodeIdsOfNext.add(String.valueOf(nodeContent.get("next")));
        }
    }

    private String prefix(Map<String, Object> nodeContent) {
        return nodeContent.get("id") + "节点, 类型为" + nodeContent.get("type") + ", ";
    }

    private Map<String, Object> parameters(Map<String, Object> nodeContent) {
        Object rawParameters = nodeContent.get("parameters");
        if (rawParameters instanceof Map<?, ?> parameters) {
            return toStringObjectMap(parameters);
        }
        return Map.of();
    }

    private List<?> listValue(Object rawValue) {
        if (rawValue instanceof List<?> values) {
            return values;
        }
        return List.of();
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    /**
     * Placeholder extraction result for strings containing {@code ${...}} references.
     */
    public record PlaceholderContent(boolean hasPlaceholder, List<String> matches) {
    }
}
