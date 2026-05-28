/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Query Expression Definition.
 * <p>
 * Mirrors Python's {@code QueryExpr} classes from
 * <code>foundation/store/query/base.py</code>.
 */
public abstract class QueryExpr {

    private static final Map<String, QueryLanguageDefinition> queryExprFunctions = new HashMap<>();

    /**
     * Combine filters with AND operator.
     */
    public LogicalExpr and(QueryExpr other) {
        return new LogicalExpr("and", this, other);
    }

    /**
     * Combine filters with OR operator.
     */
    public LogicalExpr or(QueryExpr other) {
        return new LogicalExpr("or", this, other);
    }

    /**
     * Combine filters with XOR operator.
     */
    public LogicalExpr xor(QueryExpr other) {
        return new LogicalExpr("xor", this, other);
    }

    /**
     * Negate the filter with NOT operator.
     */
    public LogicalExpr not() {
        return new LogicalExpr("not", this, null);
    }

    /**
     * Sanitize string values for query.
     */
    public static String sanitizeStr(Object value) {
        String strValue = String.valueOf(value);
        if (strValue.contains("\"")) {
            strValue = strValue.replace("\"", "\\\"");
            return "\"" + strValue + "\"";
        }
        return "\"" + strValue + "\"";
    }

    /**
     * Convert to database-specific expression format.
     */
    public abstract Object toExpr(String database);

    /**
     * Register a database query language.
     */
    public static void registerLanguage(String name, QueryLanguageDefinition definition) {
        queryExprFunctions.put(name, definition);
    }

    /**
     * Get registered query language definition.
     */
    public static QueryLanguageDefinition getLanguage(String name) {
        if (!queryExprFunctions.containsKey(name)) {
            throw new IllegalArgumentException(
                "Database query language " + name + " not registered");
        }
        return queryExprFunctions.get(name);
    }

    /**
     * Check if a language is registered.
     */
    public static boolean isLanguageRegistered(String name) {
        return queryExprFunctions.containsKey(name);
    }
}