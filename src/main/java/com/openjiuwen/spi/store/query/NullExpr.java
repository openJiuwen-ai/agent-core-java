/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Null-check filter expression (IS NULL / IS NOT NULL).
 * <p>
 * Mirrors Python's {@code NullExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class NullExpr extends QueryExpr {

    private final String field;
    private final boolean isNull;

    /**
     * Create a null-check expression.
     *
     * @param field  field name
     * @param isNull true for IS NULL, false for IS NOT NULL
     */
    public NullExpr(String field, boolean isNull) {
        this.field = field;
        this.isNull = isNull;
    }

    public String getField() {
        return field;
    }

    public boolean isNull() {
        return isNull;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyNull(this);
    }
}
