/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.util.Collection;

/**
 * Expression for range operations (in, like, wildcard).
 */
public class RangeExpr extends QueryExpr {

    private final String field;
    private final String operator;
    /** Either a String pattern or a Collection of values. */
    private final Object value;

    public RangeExpr(String field, String operator, Object value) {
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

    /**
     * Convenience: return value as a Collection when applicable.
     */
    @SuppressWarnings("unchecked")
    public Collection<Object> getValueAsCollection() {
        if (value instanceof Collection<?> c) {
            return (Collection<Object>) c;
        }
        throw new ClassCastException("Value is not a Collection: " + value.getClass());
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyRange(this);
    }
}
