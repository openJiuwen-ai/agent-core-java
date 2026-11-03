/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Logical combination of two query filter expressions (AND, OR, XOR, NOT).
 * <p>
 * Mirrors Python's {@code LogicalExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class LogicalExpr extends QueryExpr {

    private final String operator;
    private final QueryExpr left;
    private final QueryExpr right;

    /**
     * Create a logical expression.
     *
     * @param operator logical operator ("and", "or", "xor", "not")
     * @param left     left operand
     * @param right    right operand (null for NOT)
     */
    public LogicalExpr(String operator, QueryExpr left, QueryExpr right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public String getOperator() {
        return operator;
    }

    public QueryExpr getLeft() {
        return left;
    }

    public QueryExpr getRight() {
        return right;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyLogical(this);
    }
}
