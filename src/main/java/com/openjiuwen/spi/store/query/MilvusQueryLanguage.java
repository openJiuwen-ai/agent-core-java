/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Milvus-specific query language definition.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.foundation.store.query.milvus_query_func} in
 * {@code openjiuwen/core/foundation/store/query/milvus_query_func.py}.
 * </p>
 */
public final class MilvusQueryLanguage {

    /**
     * Pre-built Milvus query language definition.
     */
    public static final QueryLanguageDefinition MILVUS_DEF = new QueryLanguageDefinition(
            MilvusQueryLanguage::milvusComparisonFilter,
            MilvusQueryLanguage::milvusRangeFilter,
            MilvusQueryLanguage::milvusArithmeticFilter,
            MilvusQueryLanguage::milvusNullFilter,
            MilvusQueryLanguage::milvusJsonFilter,
            MilvusQueryLanguage::milvusArrayFilter,
            MilvusQueryLanguage::milvusLogicalFilter,
            MilvusQueryLanguage::milvusTextMatchFilter
    );

    private MilvusQueryLanguage() {
    }

    /**
     * Convert a comparison expression to Milvus filter string.
     *
     * @param expr comparison expression
     * @return Milvus filter string
     */
    public static String milvusComparisonFilter(ComparisonExpr expr) {
        if (expr.getValue() instanceof String) {
            return expr.getField() + " " + expr.getOperator() + " " + QueryExpr.sanitizeStr(expr.getValue());
        }
        return expr.getField() + " " + expr.getOperator() + " " + expr.getValue();
    }

    /**
     * Convert a range expression to Milvus filter string.
     *
     * @param expr range expression
     * @return Milvus filter string
     */
    public static String milvusRangeFilter(RangeExpr expr) {
        switch (expr.getOperator().toLowerCase(Locale.ROOT)) {
            case "in":
                List<Object> values = asSequence(expr.getValue());
                if (values == null) {
                    QueryExpr.raiseQueryError("in operator requires a sequence or set value");
                }
                return expr.getField() + " in [" + joinValues(values) + "]";
            case "like":
                if (expr.getValue() instanceof String value) {
                    if (!value.contains("%")) {
                        QueryExpr.raiseQueryError("Milvus's like operator uses % for wildcard matching");
                    }
                    return expr.getField() + " like " + QueryExpr.sanitizeStr(value);
                }
                QueryExpr.raiseQueryError("like operator requires a string value");
                return "";
            default:
                QueryExpr.raiseQueryError("Unsupported range operator: " + expr.getOperator());
                return "";
        }
    }

    /**
     * Convert an arithmetic expression to Milvus filter string.
     *
     * @param expr arithmetic expression
     * @return Milvus filter string
     */
    public static String milvusArithmeticFilter(ArithmeticExpr expr) {
        return expr.getField() + " " + expr.getArithmeticOperator() + " " + expr.getArithmeticValue()
                + expr.getComparisonOperator() + " " + expr.getComparisonValue();
    }

    /**
     * Convert a null-check expression to Milvus filter string.
     *
     * @param expr null expression
     * @return Milvus filter string
     */
    public static String milvusNullFilter(NullExpr expr) {
        if (expr.isNull()) {
            return expr.getField() + " is null";
        }
        return expr.getField() + " is not null";
    }

    /**
     * Convert a JSON filter expression to Milvus filter string.
     *
     * @param expr JSON expression
     * @return Milvus filter string
     */
    public static String milvusJsonFilter(JSONExpr expr) {
        if (expr.getValue() instanceof String) {
            return expr.getField() + "[" + QueryExpr.sanitizeStr(expr.getKey()) + "] "
                    + expr.getOperator() + " " + QueryExpr.sanitizeStr(expr.getValue());
        }
        return expr.getField() + "[" + QueryExpr.sanitizeStr(expr.getKey()) + "] "
                + expr.getOperator() + " " + expr.getValue();
    }

    /**
     * Convert an array filter expression to Milvus filter string.
     *
     * @param expr array expression
     * @return Milvus filter string
     */
    public static String milvusArrayFilter(ArrayExpr expr) {
        if (expr.getIndex() != null) {
            if (expr.getValue() instanceof String) {
                return expr.getField() + "[" + expr.getIndex() + "] " + expr.getOperator()
                        + " " + QueryExpr.sanitizeStr(expr.getValue());
            }
            return expr.getField() + "[" + expr.getIndex() + "] " + expr.getOperator() + " " + expr.getValue();
        }
        if (expr.getValue() instanceof String) {
            return expr.getField() + " " + expr.getOperator() + " " + QueryExpr.sanitizeStr(expr.getValue());
        }
        return expr.getField() + " " + expr.getOperator() + " " + expr.getValue();
    }

    /**
     * Convert a logical expression to Milvus filter string.
     *
     * @param expr logical expression
     * @return Milvus filter string
     */
    public static String milvusLogicalFilter(LogicalExpr expr) {
        switch (expr.getOperator().toLowerCase(Locale.ROOT)) {
            case "not":
                if (expr.getRight() != null) {
                    QueryExpr.raiseQueryError("not operator should not have a right operand");
                }
                return "not (" + expr.getLeft().toExpr("milvus") + ")";
            case "and":
            case "or":
                if (expr.getRight() == null) {
                    QueryExpr.raiseQueryError(expr.getOperator() + " operator requires both left and right operands");
                }
                return "(" + expr.getLeft().toExpr("milvus") + ") "
                        + expr.getOperator() + " (" + expr.getRight().toExpr("milvus") + ")";
            default:
                QueryExpr.raiseQueryError("Unsupported logical operator: " + expr.getOperator());
                return "";
        }
    }

    /**
     * Convert a text match expression to Milvus filter string.
     *
     * @param expr match expression
     * @return Milvus filter string
     */
    public static String milvusTextMatchFilter(MatchExpr expr) {
        String pattern = expr.getValue();
        switch (expr.getMatchMode().toPythonValue()) {
            case "exact":
                return "TEXT_MATCH(" + expr.getField() + ", " + QueryExpr.sanitizeStr(pattern) + ")";
            case "prefix":
                pattern = pattern + "%";
                break;
            case "suffix":
                pattern = "%" + pattern;
                break;
            case "infix":
                pattern = "%" + pattern + "%";
                break;
            default:
                QueryExpr.raiseQueryError("Unknown match mode: " + expr.getMatchMode().toPythonValue());
                return "";
        }
        return expr.getField() + " like " + QueryExpr.sanitizeStr(pattern);
    }

    private static String joinValues(List<Object> values) {
        boolean allStrings = true;
        for (Object value : values) {
            if (!(value instanceof String)) {
                allStrings = false;
                break;
            }
        }

        List<String> parts = new ArrayList<>(values.size());
        for (Object value : values) {
            if (allStrings) {
                parts.add(QueryExpr.sanitizeStr(value));
            } else {
                parts.add(String.valueOf(value));
            }
        }
        return String.join(",", parts);
    }

    private static List<Object> asSequence(Object value) {
        if (value instanceof CharSequence chars) {
            List<Object> items = new ArrayList<>(chars.length());
            for (int i = 0; i < chars.length(); i++) {
                items.add(String.valueOf(chars.charAt(i)));
            }
            return items;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(item);
            }
            return items;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(Array.get(value, i));
            }
            return items;
        }
        return null;
    }
}
