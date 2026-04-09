/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.condition;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowStateCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condition that evaluates string expressions with variable substitution.
 * <p>
 * Supports operators: {@code &&} (and), {@code ||} (or), {@code !} (not),
 * comparisons ({@code ==, !=, <, <=, >, >=, in, not_in}),
 * and functions: {@code length()}, {@code is_empty()}, {@code is_not_empty()}.
 * <p>
 * Variables are referenced via {@code ${variable_path}} syntax and resolved from session state.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.condition.expression.ExpressionCondition}.
 */
public class ExpressionCondition extends Condition {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");

    private final String expression;
    private final List<String> matches;

    public ExpressionCondition(String expression) {
        super();
        if (expression == null) {
            expression = "";
        }
        if (expression.length() > Constant.MAX_EXPRESSION_LENGTH) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "expression length exceeds maximum allowed length of " + Constant.MAX_EXPRESSION_LENGTH);
        }
        this.expression = expression;
        this.matches = new ArrayList<>();
        Matcher matcher = VAR_PATTERN.matcher(expression);
        while (matcher.find()) {
            this.matches.add(matcher.group(0));
        }
    }

    @Override
    public Object traceInfo(BaseSession session) {
        Map<String, Object> info = new HashMap<>();
        info.put("bool_expression", expression);
        info.put("inputs", getInputs(session));
        return info;
    }

    private Map<String, Object> getInputs(BaseSession session) {
        if (expression.isEmpty() || session == null) {
            return new HashMap<>();
        }
        Map<String, Object> inputs = new HashMap<>();
        for (String match : matches) {
            String key = match.substring(2, match.length() - 1);
            Object value = null;
            if (session.state() instanceof WorkflowStateCollection) {
                value = ((WorkflowStateCollection) session.state()).getGlobal(key);
            }
            inputs.put(match, value);
        }
        return inputs;
    }

    @Override
    public Object doInvoke(Object inputs, BaseSession session) {
        if (expression.isEmpty()) {
            return true;
        }
        return evaluateExpression(expression, getInputs(session));
    }

    @Override
    public boolean evaluate(BaseSession session) {
        if (expression.isEmpty()) {
            return true;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("session", session);
        Object result = atomicInvoke(kwargs);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return false;
    }

    /**
     * Evaluate the expression with the given variable bindings.
     */
    private boolean evaluateExpression(String expr, Map<String, Object> inputs) {
        // Preprocess: replace operators
        String processed = convertCondition(expr);

        // Substitute variables
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String varRef = entry.getKey();
            Object value = entry.getValue();
            processed = processed.replace(varRef, toLiteral(value));
        }

        try {
            Object result = parseOrExpression(new ExpressionParser(processed.trim()));
            return toBoolean(result);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("[")) {
                throw e;
            }
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "error evaluating expression: " + e.getMessage());
        }
    }

    public static String convertCondition(String expr) {
        if (expr == null || expr.isEmpty()) {
            return "";
        }
        List<String> stringLiterals = new ArrayList<>();
        Pattern stringPattern = Pattern.compile("(\"(?:[^\"\\\\]|\\\\.)*\")|('(?:[^'\\\\]|\\\\.)*')");
        Matcher matcher = stringPattern.matcher(expr);
        StringBuffer protectedExpr = new StringBuffer();
        while (matcher.find()) {
            String placeholder = "__STRING_LITERAL_" + stringLiterals.size() + "__";
            stringLiterals.add(matcher.group(0));
            matcher.appendReplacement(protectedExpr, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(protectedExpr);

        String processed = protectedExpr.toString()
                .replace("&&", " AND ")
                .replace("||", " OR ")
                .replaceAll("\\btrue\\b", "TRUE_VAL")
                .replaceAll("\\bfalse\\b", "FALSE_VAL")
                .replaceAll("\\blength\\(", "len(")
                .replaceAll("\\bnot_in\\b", "NOT_IN");

        for (int i = 0; i < stringLiterals.size(); i++) {
            processed = processed.replace("__STRING_LITERAL_" + i + "__", stringLiterals.get(i));
        }
        return processed;
    }

    // ==================== Recursive Descent Parser ====================

    private static Object parseOrExpression(ExpressionParser parser) {
        Object left = parseAndExpression(parser);
        while (parser.matchKeyword("OR")) {
            Object right = parseAndExpression(parser);
            left = toBoolean(left) || toBoolean(right);
        }
        return left;
    }

    private static Object parseAndExpression(ExpressionParser parser) {
        Object left = parseNotExpression(parser);
        while (parser.matchKeyword("AND")) {
            Object right = parseNotExpression(parser);
            left = toBoolean(left) && toBoolean(right);
        }
        return left;
    }

    private static Object parseNotExpression(ExpressionParser parser) {
        if (parser.matchKeyword("not") || parser.matchChar('!')) {
            Object operand = parseNotExpression(parser);
            return !toBoolean(operand);
        }
        return parseComparison(parser);
    }

    private static Object parseComparison(ExpressionParser parser) {
        Object left = parseAddSub(parser);
        while (true) {
            if (parser.matchOp("==")) {
                Object right = parseAddSub(parser);
                left = objectEquals(left, right);
            } else if (parser.matchOp("!=")) {
                Object right = parseAddSub(parser);
                left = !objectEquals(left, right);
            } else if (parser.matchOp("<=")) {
                Object right = parseAddSub(parser);
                left = objectCompare(left, right) <= 0;
            } else if (parser.matchOp(">=")) {
                Object right = parseAddSub(parser);
                left = objectCompare(left, right) >= 0;
            } else if (parser.matchOp("<")) {
                Object right = parseAddSub(parser);
                left = objectCompare(left, right) < 0;
            } else if (parser.matchOp(">")) {
                Object right = parseAddSub(parser);
                left = objectCompare(left, right) > 0;
            } else if (parser.matchKeyword("NOT_IN")) {
                Object right = parseAddSub(parser);
                left = !objectIn(left, right);
            } else if (parser.matchKeyword("in")) {
                Object right = parseAddSub(parser);
                left = objectIn(left, right);
            } else {
                break;
            }
        }
        return left;
    }

    private static Object parseAddSub(ExpressionParser parser) {
        Object left = parseMulDiv(parser);
        while (true) {
            if (parser.matchChar('+')) {
                Object right = parseMulDiv(parser);
                left = numericOp(left, right, '+');
            } else if (parser.matchChar('-')) {
                Object right = parseMulDiv(parser);
                left = numericOp(left, right, '-');
            } else {
                break;
            }
        }
        return left;
    }

    private static Object parseMulDiv(ExpressionParser parser) {
        Object left = parseUnary(parser);
        while (true) {
            if (parser.matchChar('*')) {
                Object right = parseUnary(parser);
                left = numericOp(left, right, '*');
            } else if (parser.matchChar('/')) {
                Object right = parseUnary(parser);
                left = numericOp(left, right, '/');
            } else if (parser.matchChar('%')) {
                Object right = parseUnary(parser);
                left = numericOp(left, right, '%');
            } else {
                break;
            }
        }
        return left;
    }

    private static Object parseUnary(ExpressionParser parser) {
        if (parser.matchChar('-')) {
            Object operand = parsePrimary(parser);
            if (operand instanceof Number) {
                return -((Number) operand).doubleValue();
            }
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "cannot negate non-numeric value");
        }
        return parsePrimary(parser);
    }

    @SuppressWarnings("unchecked")
    private static Object parsePrimary(ExpressionParser parser) {
        parser.skipWhitespace();
        if (parser.isEof()) {
            return null;
        }

        // Parenthesized expression
        if (parser.matchChar('(')) {
            Object result = parseOrExpression(parser);
            parser.expect(')');
            return result;
        }

        // String literal
        if (parser.peek() == '"' || parser.peek() == '\'') {
            return parser.readString();
        }

        // Number
        if (Character.isDigit(parser.peek()) || (parser.peek() == '.' && parser.hasNext() && Character.isDigit(parser.peekNext()))) {
            return parser.readNumber();
        }

        // List literal
        if (parser.matchChar('[')) {
            List<Object> list = new ArrayList<>();
            if (!parser.checkChar(']')) {
                list.add(parseOrExpression(parser));
                while (parser.matchChar(',')) {
                    list.add(parseOrExpression(parser));
                }
            }
            parser.expect(']');
            return list;
        }

        // Keywords and function calls
        String ident = parser.readIdentifier();
        if (ident == null || ident.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_SYNTAX_ERROR,
                    "error_msg", "unexpected character: " + parser.peek());
        }

        // Boolean literals
        if ("TRUE_VAL".equals(ident) || "True".equals(ident)) {
            return true;
        }
        if ("FALSE_VAL".equals(ident) || "False".equals(ident)) {
            return false;
        }
        if ("None".equals(ident)) {
            return null;
        }

        // Functions
        if (parser.matchChar('(')) {
            Object arg = parseOrExpression(parser);
            parser.expect(')');
            switch (ident) {
                case "length":
                case "len":
                    return safeLength(arg);
                case "is_empty":
                case "_safe_is_empty":
                    return safeIsEmpty(arg);
                case "is_not_empty":
                case "_safe_is_not_empty":
                    return !safeIsEmpty(arg);
                default:
                    throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                            "error_msg", "unknown function: " + ident);
            }
        }

        // Try as number
        try {
            return Integer.parseInt(ident);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(ident);
        } catch (NumberFormatException ignored) {
        }

        throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                "error_msg", "name '" + ident + "' is not defined");
    }

    // ==================== Helper methods ====================

    private static String toLiteral(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String) {
            return "\"" + ((String) value).replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "True" : "False";
        }
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toLiteral(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("[");
            int index = 0;
            for (Object key : map.keySet()) {
                if (index++ > 0) {
                    sb.append(",");
                }
                sb.append(toLiteral(key));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }

    private static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof Collection) return !((Collection<?>) value).isEmpty();
        return true;
    }

    private static boolean objectEquals(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (left instanceof Number && right instanceof Number) {
            return ((Number) left).doubleValue() == ((Number) right).doubleValue();
        }
        return left.equals(right);
    }

    @SuppressWarnings("unchecked")
    private static int objectCompare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        if (left instanceof Comparable && right instanceof Comparable) {
            return ((Comparable<Object>) left).compareTo(right);
        }
        throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                "error_msg", "cannot compare types: " + typeName(left) + " and " + typeName(right));
    }

    private static boolean objectIn(Object left, Object right) {
        if (right instanceof Collection) {
            return ((Collection<?>) right).contains(left);
        }
        if (right instanceof String && left instanceof String) {
            return ((String) right).contains((String) left);
        }
        return false;
    }

    private static Object numericOp(Object left, Object right, char op) {
        if (op == '+' && (left instanceof String || right instanceof String)) {
            return String.valueOf(left) + String.valueOf(right);
        }
        if (!(left instanceof Number) || !(right instanceof Number)) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "cannot perform arithmetic on non-numeric types");
        }
        double l = ((Number) left).doubleValue();
        double r = ((Number) right).doubleValue();
        switch (op) {
            case '+': return l + r;
            case '-': return l - r;
            case '*': return l * r;
            case '/':
                if (r == 0) throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                        "error_msg", "division by zero");
                return l / r;
            case '%': return l % r;
            default: throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "unsupported operator: " + op);
        }
    }

    private static int safeLength(Object value) {
        if (value == null) return 0;
        if (value instanceof String) return ((String) value).length();
        if (value instanceof Collection) {
            int size = ((Collection<?>) value).size();
            if (size > Constant.MAX_COLLECTION_SIZE) {
                throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                        "error_msg", "collection size exceeds maximum allowed size of " + Constant.MAX_COLLECTION_SIZE);
            }
            return size;
        }
        if (value instanceof Map) return ((Map<?, ?>) value).size();
        throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                "error_msg", "object of type '" + typeName(value) + "' has no len()");
    }

    private static boolean safeIsEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof Number || value instanceof Boolean) {
            throw ErrorHelper.buildError(StatusCode.EXPRESSION_EVAL_ERROR,
                    "error_msg", "cannot check emptiness of " + typeName(value) + " type");
        }
        if (value instanceof String) return ((String) value).isEmpty();
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        return false;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    // ==================== Simple Expression Parser ====================

    private static class ExpressionParser {
        private final String input;
        private int pos;

        ExpressionParser(String input) {
            this.input = input;
            this.pos = 0;
        }

        void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        boolean isEof() {
            skipWhitespace();
            return pos >= input.length();
        }

        char peek() {
            skipWhitespace();
            return pos < input.length() ? input.charAt(pos) : '\0';
        }

        char peekNext() {
            return (pos + 1) < input.length() ? input.charAt(pos + 1) : '\0';
        }

        boolean hasNext() {
            return (pos + 1) < input.length();
        }

        boolean matchChar(char c) {
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        boolean checkChar(char c) {
            skipWhitespace();
            return pos < input.length() && input.charAt(pos) == c;
        }

        boolean matchOp(String op) {
            skipWhitespace();
            if (input.startsWith(op, pos)) {
                // For single char ops like < >, make sure they're not part of <= >=
                if (op.length() == 1 && (op.equals("<") || op.equals(">"))) {
                    if (pos + 1 < input.length() && input.charAt(pos + 1) == '=') {
                        return false;
                    }
                }
                pos += op.length();
                return true;
            }
            return false;
        }

        boolean matchKeyword(String keyword) {
            skipWhitespace();
            if (input.startsWith(keyword, pos)) {
                int end = pos + keyword.length();
                if (end >= input.length() || !Character.isLetterOrDigit(input.charAt(end))) {
                    pos = end;
                    return true;
                }
            }
            return false;
        }

        void expect(char c) {
            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != c) {
                throw ErrorHelper.buildError(StatusCode.EXPRESSION_SYNTAX_ERROR,
                        "error_msg", "expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        String readString() {
            char quote = input.charAt(pos);
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < input.length() && input.charAt(pos) != quote) {
                if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                    pos++;
                    sb.append(input.charAt(pos));
                } else {
                    sb.append(input.charAt(pos));
                }
                pos++;
            }
            if (pos < input.length()) {
                pos++; // skip closing quote
            }
            return sb.toString();
        }

        Number readNumber() {
            int start = pos;
            boolean hasDecimal = false;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                if (input.charAt(pos) == '.') {
                    hasDecimal = true;
                }
                pos++;
            }
            String numStr = input.substring(start, pos);
            if (hasDecimal) {
                return Double.parseDouble(numStr);
            }
            try {
                return Integer.parseInt(numStr);
            } catch (NumberFormatException e) {
                return Long.parseLong(numStr);
            }
        }

        String readIdentifier() {
            skipWhitespace();
            int start = pos;
            while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos))
                    || input.charAt(pos) == '_')) {
                pos++;
            }
            return input.substring(start, pos);
        }
    }
}
