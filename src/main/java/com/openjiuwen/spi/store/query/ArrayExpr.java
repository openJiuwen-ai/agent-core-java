/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Array index filter expression.
 * <p>
 * Mirrors Python's {@code ArrayExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class ArrayExpr extends QueryExpr {

    private final String field;
    private final Integer index;
    private final String operator;
    private final Object value;

    /**
     * Create an array index filter expression.
     *
     * @param field    field name
     * @param index    array index
     * @param operator comparison operator
     * @param value    comparison value
     */
    public ArrayExpr(String field, Integer index, String operator, Object value) {
        this.field = field;
        this.index = index;
        this.operator = operator;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public Integer getIndex() {
        return index;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyArray(this);
    }
}
