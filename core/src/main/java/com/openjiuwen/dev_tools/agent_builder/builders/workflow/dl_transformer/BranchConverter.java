/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Branch node converter.
 *
 * <p>Mirrors Python's {@code BranchConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/branch_converter.py}.</p>
 */
public class BranchConverter extends BaseConverter {

    public static final Map<String, String> BRANCH_OPERATOR_MAP = createBranchOperatorMap();
    public static final Map<String, Integer> BRANCH_LOGIC_MAP = Map.of("or", 1, "and", 2);
    private static final Pattern PREFIX_REF_PATTERN = Pattern.compile("^\\$\\{\\s*\\w+\\.\\w+\\s*}");

    public BranchConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
        super(nodeData, nodesDict);
    }

    public BranchConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Map<String, Object> resource) {
        super(nodeData, nodesDict, resource);
    }

    public BranchConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
        super(nodeData, nodesDict, position);
    }

    public BranchConverter(
            Map<String, Object> nodeData,
            Map<String, Object> nodesDict,
            Map<String, Object> resource,
            Position position) {
        super(nodeData, nodesDict, resource, position);
    }

    static List<Map<String, Object>> convertBranches(List<Map<String, Object>> conditions) {
        List<Map<String, Object>> branches = new ArrayList<>();
        for (Map<String, Object> condition : conditions) {
            Map<String, Object> branch = new LinkedHashMap<>();
            if (condition.containsKey("expressions")) {
                branch.put("conditions", convertExpressions(asStringList(required(condition, "expressions"))));
                branch.put("logic", requiredLogic(requiredString(condition, "operator")));
                branch.put("branchId", required(condition, "branch"));
            } else if (!"default".equals(required(condition, "expression"))) {
                branch.put("conditions", List.of(convertExpression(String.valueOf(required(condition, "expression")))));
                branch.put("branchId", required(condition, "branch"));
            } else {
                branch.put("conditions", List.of());
                branch.put("branchId", required(condition, "branch"));
            }
            branches.add(branch);
        }
        return branches;
    }

    static Map<String, Object> convertExpression(String expression) {
        String operator = findOperator(expression);
        if (operator == null) {
            return new LinkedHashMap<>();
        }

        int operatorIndex = expression.indexOf(operator);
        String leftText = expression.substring(0, operatorIndex).trim();
        String rightText = expression.substring(operatorIndex + operator.length()).trim();

        Map<String, Object> left = buildSide(leftText);
        Map<String, Object> right = buildSide(rightText);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("left", left);
        result.put("operator", BRANCH_OPERATOR_MAP.get(operator));
        if (right != null && !right.isEmpty()) {
            result.put("right", right);
        }
        return result;
    }

    static Map<String, Object> buildSide(String valueText) {
        if (valueText == null || valueText.isEmpty()) {
            return null;
        }

        if (valueText.contains("${")) {
            return ConverterUtils.convertRefVariable(extractPrefixReference(valueText));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("extra", Map.of("weak", true));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", SourceType.constant.getValue());
        result.put("content", valueText);
        result.put("schema", schema);
        return result;
    }

    @Override
    protected void convertSpecificConfig() {
        node.getData().setBranches(convertBranches(conditionList()));
    }

    @Override
    public void convertEdges() {
        for (Map<String, Object> condition : conditionList()) {
            edges.add(new Edge(
                    requiredString(nodeData, "id"),
                    nullableString(required(condition, "next")),
                    nullableString(required(condition, "branch"))
            ));
        }
    }

    private static Map<String, String> createBranchOperatorMap() {
        Map<String, String> operators = new LinkedHashMap<>();
        operators.put("eq", "eq");
        operators.put("not_eq", "neq");
        operators.put("contain", "contains");
        operators.put("not_contain", "not_contains");
        operators.put("is_empty", "is_empty");
        operators.put("is_not_empty", "is_not_empty");
        operators.put("longer_than", "gt");
        operators.put("longer_than_or_eq", "gte");
        operators.put("short_than", "lt");
        operators.put("short_than_or_eq", "lte");
        operators.put("len_longer_than", "len_longer_than");
        operators.put("len_longer_than_or_eq", "len_longer_than_or_eq");
        operators.put("len_shorter_than", "len_shorter_than");
        operators.put("len_shorter_than_or_eq", "len_shorter_than_or_eq");
        return operators;
    }

    private static List<Map<String, Object>> convertExpressions(List<String> expressions) {
        List<Map<String, Object>> converted = new ArrayList<>();
        for (String expression : expressions) {
            converted.add(convertExpression(expression));
        }
        return converted;
    }

    private static String findOperator(String expression) {
        for (String operator : BRANCH_OPERATOR_MAP.keySet()) {
            if (expression.contains(operator)) {
                return operator;
            }
        }
        return null;
    }

    private static Integer requiredLogic(String operator) {
        Integer logic = BRANCH_LOGIC_MAP.get(operator);
        if (logic == null) {
            throw new IllegalArgumentException("Unsupported branch logic operator: " + operator);
        }
        return logic;
    }

    private static String extractPrefixReference(String valueText) {
        Matcher matcher = PREFIX_REF_PATTERN.matcher(valueText.strip());
        if (matcher.find()) {
            return matcher.group();
        }
        return valueText;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("Expected expressions to be a list: " + value);
        }

        List<String> result = new ArrayList<>();
        for (Object item : values) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private List<Map<String, Object>> conditionList() {
        Object parametersObject = nodeData.get("parameters");
        if (!(parametersObject instanceof Map<?, ?> parameters)) {
            throw new IllegalArgumentException("Branch parameters must be a map");
        }
        Object conditionsObject = parameters.get("conditions");
        if (!(conditionsObject instanceof List<?> rawConditions)) {
            throw new IllegalArgumentException("Branch conditions must be a list");
        }

        List<Map<String, Object>> conditions = new ArrayList<>();
        for (Object rawCondition : rawConditions) {
            if (!(rawCondition instanceof Map<?, ?> rawConditionMap)) {
                throw new IllegalArgumentException("Branch condition must be a map");
            }
            conditions.add(toStringObjectMap(rawConditionMap));
        }
        return conditions;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    private static Object required(Map<String, Object> source, String key) {
        if (!source.containsKey(key)) {
            throw new IllegalArgumentException("Missing required branch key: " + key);
        }
        return source.get(key);
    }

    private static String requiredString(Map<String, Object> source, String key) {
        Object value = required(source, key);
        return value == null ? null : String.valueOf(value);
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
