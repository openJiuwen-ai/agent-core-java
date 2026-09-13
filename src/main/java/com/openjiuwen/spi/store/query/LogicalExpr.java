/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for logical operations (and, or, xor, not).
 * 
 * @since 0.1.7
 */
public class LogicalExpr extends QueryExpr {
    private final String operator;
    private final QueryExpr left;

    /** {@code null} for unary "not" operator. */
    private final QueryExpr right;

    /**
     * LogicalExpr.
     * 
     * @param operator operator
     * @param left left
     * @param right right
     * @since 0.1.7
     */
    public LogicalExpr(String operator, QueryExpr left, QueryExpr right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    /**
     * getOperator.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOperator() {
        return operator;
    }

    /**
     * getLeft.
     * 
     * @return the result
     * @since 0.1.7
     */
    public QueryExpr getLeft() {
        return left;
    }

    /**
     * getRight.
     * 
     * @return the result
     * @since 0.1.7
     */
    public QueryExpr getRight() {
        return right;
    }

    /**
     * toExpr.
     * 
     * @param database database
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyLogical(this);
    }
}
