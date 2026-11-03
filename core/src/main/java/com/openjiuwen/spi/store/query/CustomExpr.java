/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for custom / pass-through queries.
 * 
 * @since 0.1.7
 */
public class CustomExpr extends QueryExpr {
    private final Object expr;

    /**
     * CustomExpr.
     * 
     * @param expr expr
     * @since 0.1.7
     */
    public CustomExpr(Object expr) {
        this.expr = expr;
    }

    /**
     * getExpr.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getExpr() {
        return expr;
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
        return expr;
    }
}
