/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.util.Collection;

/**
 * Expression for range operations (in, like, wildcard).
 * 
 * @since 0.1.7
 */
public class RangeExpr extends QueryExpr {
    private final String field;
    private final String operator;

    /** Either a String pattern or a Collection of values. */
    private final Object value;

    /**
     * RangeExpr.
     * 
     * @param field field
     * @param operator operator
     * @param value value
     * @since 0.1.7
     */
    public RangeExpr(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    /**
     * getField.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getField() {
        return field;
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
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getValue() {
        return value;
    }

    /**
     * getValueAsCollection.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Collection<Object> getValueAsCollection() {
        if (value instanceof Collection<?> c) {
            return (Collection<Object>) c;
        }
        throw new ClassCastException("Value is not a Collection: " + value.getClass());
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
        return QueryLanguageRegistry.get(database).applyRange(this);
    }
}
