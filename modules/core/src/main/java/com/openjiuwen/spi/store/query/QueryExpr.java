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
 * 
 * @since 0.1.7
 */
public abstract class QueryExpr {
    /**
     * and.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    public LogicalExpr and(QueryExpr other) {
        return new LogicalExpr("and", this, other);
    }

    /**
     * Combine this filter with another using OR.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    public LogicalExpr or(QueryExpr other) {
        return new LogicalExpr("or", this, other);
    }

    /**
     * Combine this filter with another using XOR.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    public LogicalExpr xor(QueryExpr other) {
        return new LogicalExpr("xor", this, other);
    }

    /**
     * Negate this filter with NOT.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LogicalExpr not() {
        return new LogicalExpr("not", this, null);
    }

    /**
     * Sanitize a value by wrapping in double quotes and escaping internal quotes.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public abstract Object toExpr(String database);
}
