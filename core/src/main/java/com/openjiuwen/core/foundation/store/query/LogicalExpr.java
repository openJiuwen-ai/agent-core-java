/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

/**
 * Mirrors Python's {@code LogicalExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public final class LogicalExpr extends QueryExpr {

    private final String operator;
    private final QueryExpr left;
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
        return QueryExpr.getLanguageDefinition(database).applyLogical(this);
    }
}
