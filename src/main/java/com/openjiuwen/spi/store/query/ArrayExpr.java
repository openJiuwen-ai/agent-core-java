/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for array field operations.
 */
public class ArrayExpr extends QueryExpr {

    private final String field;
    private final Integer index;
    private final String operator;
    private final Object value;

    public ArrayExpr(String field, Integer index, String operator, Object value) {
        this.field = field;
        this.index = index;
        this.operator = operator;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    /** May be {@code null} when not filtering by index. */
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
