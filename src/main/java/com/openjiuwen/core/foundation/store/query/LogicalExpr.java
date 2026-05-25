/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

/**
 * Logical expression for combining query filters.
 * <p>
 * Mirrors Python's {@code LogicalExpr} class from
 * <code>foundation/store/query/base.py</code>.
 */
public class LogicalExpr extends QueryExpr {

    private final String operator;
    private final QueryExpr left;
    private final QueryExpr right;

    public LogicalExpr(String operator, QueryExpr left, QueryExpr right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public String getOperator() { return operator; }
    public QueryExpr getLeft() { return left; }
    public QueryExpr getRight() { return right; }

    @Override
    public Object toExpr(String database) {
        QueryLanguageDefinition lang = QueryExpr.getLanguage(database);
        return lang.getLogical().apply(this);
    }

    @Override
    public String toString() {
        if ("not".equals(operator)) {
            return "NOT(" + left + ")";
        }
        return "(" + left + " " + operator.toUpperCase() + " " + right + ")";
    }
}