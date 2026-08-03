/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

/**
 * Mirrors Python's {@code NullExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public final class NullExpr extends QueryExpr {

    private final String field;
    private final boolean isNull;

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
        return QueryExpr.getLanguageDefinition(database).applyNull(this);
    }
}
