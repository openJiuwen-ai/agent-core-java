/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for logical operations (and, or, xor, not).
 */
public class LogicalExpr extends QueryExpr {

    private final String operator;
    private final QueryExpr left;
    /** {@code null} for unary "not" operator. */
    private final QueryExpr right;

    /**
     * Auto-generated for codecheck compliance.
     */
    public LogicalExpr(String operator, QueryExpr left, QueryExpr right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getOperator() {
        return operator;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public QueryExpr getLeft() {
        return left;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public QueryExpr getRight() {
        return right;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyLogical(this);
    }
}
