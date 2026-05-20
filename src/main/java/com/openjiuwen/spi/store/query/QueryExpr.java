/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Abstract base class for all query filter expressions.
 * <p>
 * Provides combinators ({@link #and}, {@link #or}, {@link #xor}, {@link #not})
 * and a {@link #sanitizeStr} helper.
 * Mirrors Python's {@code QueryExpr} hierarchy.
 */
public abstract class QueryExpr {

    /**
     * Combine this filter with another using AND.
     */
    public LogicalExpr and(QueryExpr other) {
        return new LogicalExpr("and", this, other);
    }

    /**
     * Combine this filter with another using OR.
     */
    public LogicalExpr or(QueryExpr other) {
        return new LogicalExpr("or", this, other);
    }

    /**
     * Combine this filter with another using XOR.
     */
    public LogicalExpr xor(QueryExpr other) {
        return new LogicalExpr("xor", this, other);
    }

    /**
     * Negate this filter with NOT.
     */
    public LogicalExpr not() {
        return new LogicalExpr("not", this, null);
    }

    /**
     * Sanitize a value by wrapping in double quotes and escaping internal quotes.
     */
    public static String sanitizeStr(Object value) {
        String str = String.valueOf(value);
        if (str.contains("\"")) {
            str = str.replace("\"", "\\\"");
        }
        return "\"" + str + "\"";
    }

    /**
     * Convert this expression to a database-specific representation.
     *
     * @param database name of the registered database query language
     * @return database-specific expression object
     */
    public abstract Object toExpr(String database);
}
