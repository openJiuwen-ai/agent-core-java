/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.utils.SessionUtils;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Mirrors Python's {@code Branch} in
 * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
 */
public final class Branch {

    private final String branchId;
    private final BranchCondition condition;
    private final List<String> target;

    public Branch(String condition, List<String> target, String branchId) {
        this(new ExpressionBranchCondition(condition), target, branchId);
    }

    public Branch(BooleanSupplier condition, List<String> target, String branchId) {
        this(new CallableBranchCondition(condition), target, branchId);
    }

    public Branch(BranchCondition condition, List<String> target, String branchId) {
        if (condition == null) {
            throw new IllegalArgumentException("branch condition type does not meet the requirements");
        }
        this.branchId = branchId;
        this.condition = condition;
        this.target = target == null ? null : new ArrayList<>(target);
    }

    public String getBranchId() {
        return branchId;
    }

    public List<String> getTarget() {
        return target;
    }

    public boolean evaluate(BaseSession session) {
        return condition.evaluate(session);
    }

    public Object traceInfo(BaseSession session) {
        return condition.traceInfo(session);
    }

    /**
     * Mirrors Python's accepted {@code Condition} call contract in
     * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
     */
    public interface BranchCondition {
        boolean evaluate(BaseSession session);

        Object traceInfo(BaseSession session);
    }

    /**
     * Mirrors Python's {@code FuncCondition} adaptation in
     * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
     */
    public static final class CallableBranchCondition implements BranchCondition {
        private final BooleanSupplier condition;

        public CallableBranchCondition(BooleanSupplier condition) {
            this.condition = Objects.requireNonNull(condition, "condition");
        }

        @Override
        public boolean evaluate(BaseSession session) {
            return condition.getAsBoolean();
        }

        @Override
        public Object traceInfo(BaseSession session) {
            return condition.getClass().getSimpleName();
        }
    }

    /**
     * Mirrors Python's string-to-{@code ExpressionCondition} adaptation in
     * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
     */
    public static final class ExpressionBranchCondition implements BranchCondition {
        private final String expression;

        public ExpressionBranchCondition(String expression) {
            this.expression = Objects.requireNonNull(expression, "expression");
        }

        @Override
        public boolean evaluate(BaseSession session) {
            return BranchExpressionEvaluator.evaluate(expression, session);
        }

        @Override
        public Object traceInfo(BaseSession session) {
            return Map.of(
                    "bool_expression", expression,
                    "inputs", BranchExpressionEvaluator.inputs(expression, session)
            );
        }
    }
}

/**
 * Mirrors Python's branch expression use in
 * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
 *
 * <p>This evaluator intentionally covers the branch expressions used by the
 * workflow layer while the full {@code ExpressionCondition} task owns the
 * complete Python AST implementation.</p>
 */
final class BranchExpressionEvaluator {

    private BranchExpressionEvaluator() {
    }

    static boolean evaluate(String expression, BaseSession session) {
        String value = expression == null ? "" : expression.trim();
        if (value.isEmpty()) {
            return true;
        }
        return evaluateOr(value, session);
    }

    static Map<String, Object> inputs(String expression, BaseSession session) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (String path : placeholderPaths(expression)) {
            result.put("${" + path + "}", SessionValueResolver.resolve(session, path).orElse(null));
        }
        return result;
    }

    private static boolean evaluateOr(String expression, BaseSession session) {
        for (String part : splitTopLevel(expression, "or")) {
            if (evaluateAnd(part, session)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateAnd(String expression, BaseSession session) {
        for (String part : splitTopLevel(expression, "and")) {
            if (!evaluateAtom(part, session)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateAtom(String expression, BaseSession session) {
        String value = stripOuterParens(normalizeOperators(expression.trim()));
        if (value.startsWith("not ")) {
            return !evaluateAtom(value.substring(4), session);
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        if (startsWithFunction(value, "is_empty")) {
            return isEmpty(resolveValue(functionArgument(value), session));
        }
        if (startsWithFunction(value, "is_not_empty")) {
            return !isEmpty(resolveValue(functionArgument(value), session));
        }
        String[] comparison = findComparison(value);
        if (comparison != null) {
            Object left = resolveValue(comparison[0], session);
            Object right = resolveValue(comparison[2], session);
            return compare(left, right, comparison[1]);
        }
        return truthy(resolveValue(value, session));
    }

    private static String normalizeOperators(String expression) {
        return expression.replace("&&", " and ")
                .replace("||", " or ")
                .replace("not_in", "not in")
                .replaceAll("(?i)\\btrue\\b", "True")
                .replaceAll("(?i)\\bfalse\\b", "False");
    }

    private static Object resolveValue(String rawToken, BaseSession session) {
        String token = stripOuterParens(rawToken.trim());
        String[] modulo = splitArithmetic(token, "%");
        if (modulo != null) {
            Object left = resolveValue(modulo[0], session);
            Object right = resolveValue(modulo[1], session);
            if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                return leftNumber.doubleValue() % rightNumber.doubleValue();
            }
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "unsupported operand type for %");
        }
        if (startsWithFunction(token, "length") || startsWithFunction(token, "len")) {
            return collectionLength(resolveValue(functionArgument(token), session));
        }
        if (token.startsWith("${")) {
            int close = token.indexOf('}');
            if (close > 1) {
                Object value = SessionValueResolver.resolve(session, token.substring(2, close)).orElse(null);
                return applySubscripts(value, token.substring(close + 1));
            }
        }
        if ((token.startsWith("'") && token.endsWith("'")) || (token.startsWith("\"") && token.endsWith("\""))) {
            return token.substring(1, token.length() - 1);
        }
        if (token.equals("None") || token.equals("null")) {
            return null;
        }
        if (token.equalsIgnoreCase("True")) {
            return Boolean.TRUE;
        }
        if (token.equalsIgnoreCase("False")) {
            return Boolean.FALSE;
        }
        try {
            if (token.contains(".")) {
                return Double.parseDouble(token);
            }
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "name '" + token + "' is not defined");
        }
    }

    static Object applySubscripts(Object value, String suffix) {
        String rest = suffix == null ? "" : suffix.trim();
        Object current = value;
        while (!rest.isEmpty()) {
            if (!rest.startsWith("[")) {
                throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                        "error_msg", "unsupported expression suffix: " + rest);
            }
            int close = rest.indexOf(']');
            if (close < 0) {
                throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                        "error_msg", "unclosed subscript");
            }
            String keyToken = rest.substring(1, close).trim();
            Object key = parseSubscriptKey(keyToken);
            current = subscript(current, key);
            rest = rest.substring(close + 1).trim();
        }
        return current;
    }

    private static Object parseSubscriptKey(String token) {
        if ((token.startsWith("'") && token.endsWith("'")) || (token.startsWith("\"") && token.endsWith("\""))) {
            return token.substring(1, token.length() - 1);
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            return token;
        }
    }

    private static Object subscript(Object value, Object key) {
        if (value == null) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "cannot subscript None");
        }
        if (value instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (value instanceof List<?> list && key instanceof Number number) {
            return list.get(number.intValue());
        }
        if (value.getClass().isArray() && key instanceof Number number) {
            return Array.get(value, number.intValue());
        }
        if (value instanceof CharSequence text && key instanceof Number number) {
            return String.valueOf(text.charAt(number.intValue()));
        }
        throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                "error_msg", "object is not subscriptable");
    }

    private static String[] findComparison(String expression) {
        for (String operator : List.of(" is not ", " is ", " not in ", " in ", "==", "!=", ">=", "<=", ">", "<", "=")) {
            int index = indexOfTopLevel(expression, operator);
            if (index >= 0) {
                String left = expression.substring(0, index).trim();
                String right = expression.substring(index + operator.length()).trim();
                String op = operator.trim();
                return new String[] {left, op, right};
            }
        }
        return null;
    }

    private static boolean compare(Object left, Object right, String operator) {
        return switch (operator) {
            case "==", "=" -> valuesEqual(left, right);
            case "!=" -> !valuesEqual(left, right);
            case ">" -> compareNumbers(left, right) > 0;
            case ">=" -> compareNumbers(left, right) >= 0;
            case "<" -> compareNumbers(left, right) < 0;
            case "<=" -> compareNumbers(left, right) <= 0;
            case "is" -> left == right;
            case "is not" -> left != right;
            case "in" -> contains(right, left);
            case "not in" -> !contains(right, left);
            default -> false;
        };
    }

    private static boolean valuesEqual(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private static int compareNumbers(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (left instanceof Comparable<?> comparable && left.getClass().isInstance(right)) {
            @SuppressWarnings("unchecked")
            Comparable<Object> typedComparable = (Comparable<Object>) comparable;
            return typedComparable.compareTo(right);
        }
        throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                "error_msg", "values are not comparable");
    }

    private static boolean contains(Object container, Object item) {
        if (container instanceof Collection<?> collection) {
            return collection.contains(item);
        }
        if (container instanceof Map<?, ?> map) {
            return map.containsKey(item);
        }
        if (container instanceof CharSequence text) {
            return item != null && text.toString().contains(String.valueOf(item));
        }
        return false;
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number || value instanceof Boolean) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "cannot check emptiness of " + value.getClass().getSimpleName() + " type");
        }
        if (value instanceof CharSequence text) {
            return text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0;
        }
        return false;
    }

    private static int collectionLength(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "object has no len()");
        }
        if (value instanceof CharSequence text) {
            return text.length();
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                "error_msg", "object has no len()");
    }

    private static boolean startsWithFunction(String expression, String functionName) {
        return expression.startsWith(functionName + "(") && expression.endsWith(")");
    }

    private static String functionArgument(String expression) {
        int open = expression.indexOf('(');
        return expression.substring(open + 1, expression.length() - 1);
    }

    private static String stripOuterParens(String expression) {
        String value = expression.trim();
        while (value.startsWith("(") && value.endsWith(")") && enclosesWholeExpression(value)) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static boolean enclosesWholeExpression(String value) {
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0 && i < value.length() - 1) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private static List<String> splitTopLevel(String expression, String operator) {
        String normalized = normalizeOperators(expression);
        List<String> result = new ArrayList<>();
        int start = 0;
        int depth = 0;
        char quote = 0;
        String token = " " + operator + " ";
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            }
            if (depth == 0 && normalized.startsWith(token, i)) {
                result.add(normalized.substring(start, i).trim());
                start = i + token.length();
                i = start - 1;
            }
        }
        if (start == 0) {
            result.add(normalized.trim());
        } else {
            result.add(normalized.substring(start).trim());
        }
        return result;
    }

    private static int indexOfTopLevel(String expression, String needle) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i <= expression.length() - needle.length(); i++) {
            char ch = expression.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            }
            if (depth == 0 && expression.startsWith(needle, i)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] splitArithmetic(String expression, String operator) {
        int index = indexOfTopLevel(expression, operator);
        if (index < 0) {
            return null;
        }
        return new String[] {
                expression.substring(0, index).trim(),
                expression.substring(index + operator.length()).trim()
        };
    }

    private static List<String> placeholderPaths(String expression) {
        List<String> result = new ArrayList<>();
        if (expression == null) {
            return result;
        }
        int index = 0;
        while (index < expression.length()) {
            int start = expression.indexOf("${", index);
            if (start < 0) {
                break;
            }
            int end = expression.indexOf('}', start + 2);
            if (end < 0) {
                break;
            }
            result.add(expression.substring(start + 2, end));
            index = end + 1;
        }
        return result;
    }
}

/**
 * Mirrors Python's session-global lookup used by branch expressions in
 * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
 */
final class SessionValueResolver {

    private SessionValueResolver() {
    }

    static Optional<Object> resolve(BaseSession session, String path) {
        if (session == null || path == null) {
            return Optional.empty();
        }
        Optional<Object> direct = invokeAccessor(session, path);
        if (direct.isPresent()) {
            return direct;
        }
        Optional<Object> state = invokeNoArg(session, "state");
        if (state.isPresent()) {
            Optional<Object> stateValue = invokeAccessor(state.get(), path);
            if (stateValue.isPresent()) {
                return stateValue;
            }
            Optional<Object> dumpedValue = resolveFromStateDump(state.get(), path);
            if (dumpedValue.isPresent()) {
                return dumpedValue;
            }
        }
        return resolveWithSubscripts(session, path);
    }

    private static Optional<Object> invokeAccessor(Object target, String path) {
        for (String methodName : List.of("get_global", "getGlobal", "get")) {
            try {
                Method method = target.getClass().getMethod(methodName, String.class);
                return Optional.ofNullable(method.invoke(target, path));
            } catch (NoSuchMethodException ignored) {
                // Try the next Python/Java naming convention.
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> resolveFromStateDump(Object state, String path) {
        Optional<Object> stateMap = invokeNoArg(state, "getState");
        if (stateMap.isEmpty()) {
            stateMap = invokeNoArg(state, "dump");
        }
        if (stateMap.isEmpty() || !(stateMap.get() instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        for (String partition : List.of("io_state", "global_state", "comp_state", "workflow_state")) {
            Object value = map.get(partition);
            if (value instanceof Map<?, ?> partitionMap) {
                Object resolved = SessionUtils.getValueByNestedPath(path, (Map<String, Object>) partitionMap);
                if (resolved != null) {
                    return Optional.of(resolved);
                }
            }
        }
        Object resolved = SessionUtils.getValueByNestedPath(path, (Map<String, Object>) map);
        return Optional.ofNullable(resolved);
    }

    private static Optional<Object> resolveWithSubscripts(BaseSession session, String path) {
        int bracket = path.indexOf('[');
        if (bracket < 0) {
            return Optional.empty();
        }
        String prefix = path.substring(0, bracket);
        Optional<Object> value = invokeAccessor(session, prefix);
        if (value.isEmpty()) {
            Optional<Object> state = invokeNoArg(session, "state");
            if (state.isPresent()) {
                value = invokeAccessor(state.get(), prefix);
            }
        }
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BranchExpressionEvaluator.applySubscripts(value.get(), path.substring(bracket)));
    }
}
