/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

/**
 * Mirrors Python's {@code CustomExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public final class CustomExpr extends QueryExpr {

    private final Object expr;

    public CustomExpr(Object expr) {
        this.expr = expr;
    }

    public Object getExpr() {
        return expr;
    }

    @Override
    public Object toExpr(String database) {
        return expr;
    }
}
