/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for null-value checks (IS NULL / IS NOT NULL).
 * 
 * @since 0.1.7
 */
public class NullExpr extends QueryExpr {
    private final String field;
    private final boolean isNull;

    /**
     * NullExpr.
     * 
     * @param field field
     * @param isNull isNull
     * @since 0.1.7
     */
    public NullExpr(String field, boolean isNull) {
        this.field = field;
        this.isNull = isNull;
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
     * isNull.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isNull() {
        return isNull;
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
        return QueryLanguageRegistry.get(database).applyNullCheck(this);
    }
}
