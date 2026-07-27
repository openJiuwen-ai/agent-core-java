/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Comparison filter expression (==, !=, >, <, >=, <=).
 * <p>
 * Mirrors Python's {@code ComparisonExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class ComparisonExpr extends QueryExpr {

    private final String field;
    private final String operator;
    private final Object value;

    /**
     * Create a comparison expression.
     *
     * @param field    field name
     * @param operator comparison operator
     * @param value    comparison value
     */
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
        return QueryLanguageRegistry.get(database).applyComparison(this);
    }
}
