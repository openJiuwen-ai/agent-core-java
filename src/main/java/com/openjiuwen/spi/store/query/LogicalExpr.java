/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */

package com.openjiuwen.spi.store.query;

/**
 * Expression for logical operations (and, or, xor, not).
 */
public class LogicalExpr extends QueryExpr {

    private final String operator;
    private final QueryExpr left;
    /** {@code null} for unary "not" operator. */
    private final QueryExpr right;

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
