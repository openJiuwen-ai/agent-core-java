/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors Python's {@code QueryExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public abstract class QueryExpr {

    private static final Map<String, QueryLanguageDefinition> QUERY_EXPR_FUNCTIONS = new ConcurrentHashMap<>();

    public LogicalExpr and(QueryExpr other) {
        return new LogicalExpr("and", this, other);
    }

    public LogicalExpr or(QueryExpr other) {
        return new LogicalExpr("or", this, other);
    }

    public LogicalExpr xor(QueryExpr other) {
        return new LogicalExpr("xor", this, other);
    }

    public LogicalExpr not() {
        return new LogicalExpr("not", this, null);
    }

    public static String sanitizeStr(Object value) {
        String stringValue = pythonStringify(value);
        if (stringValue.contains("\"")) {
            stringValue = stringValue.replace("\"", "\\\"");
        }
        return "\"" + stringValue + "\"";
    }

    public static BaseError queryError(String reason) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_VECTOR_STORE_QUERY_INVALID,
                "error_msg",
                reason
        );
    }

    public static void raiseQueryError(String reason) {
        throw queryError(reason);
    }

    public static void validateLanguageRegistered(String name) {
        if (!QUERY_EXPR_FUNCTIONS.containsKey(name)) {
            raiseQueryError(
                    "Database query language " + name
                            + " not registered via registerDatabaseQueryLanguage method"
            );
        }
    }

    public static void registerLanguage(String name, QueryLanguageDefinition definition) {
        registerLanguage(name, definition, false);
    }

    public static void registerLanguage(String name, QueryLanguageDefinition definition, boolean force) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(definition, "definition");
        if (force) {
            QUERY_EXPR_FUNCTIONS.put(name, definition);
            return;
        }
        QueryLanguageDefinition previous = QUERY_EXPR_FUNCTIONS.putIfAbsent(name, definition);
        if (previous != null) {
            raiseQueryError("Database query language for name='" + name + "' already registered");
        }
    }

    public static boolean isLanguageRegistered(String name) {
        return QUERY_EXPR_FUNCTIONS.containsKey(name);
    }

    static QueryLanguageDefinition getLanguageDefinition(String name) {
        validateLanguageRegistered(name);
        return QUERY_EXPR_FUNCTIONS.get(name);
    }

    static void resetRegisteredLanguagesForTest() {
        QUERY_EXPR_FUNCTIONS.clear();
    }

    private static String pythonStringify(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "True" : "False";
        }
        return String.valueOf(value);
    }

    public abstract Object toExpr(String database);
}
