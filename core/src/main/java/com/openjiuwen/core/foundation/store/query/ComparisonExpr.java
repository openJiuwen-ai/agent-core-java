/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

/**
 * Mirrors Python's {@code ComparisonExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public final class ComparisonExpr extends QueryExpr {

    private final String field;
    private final String operator;
    private final Object value;

    public ComparisonExpr(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public Object toExpr(String database) {
        return QueryExpr.getLanguageDefinition(database).applyComparison(this);
    }
}
