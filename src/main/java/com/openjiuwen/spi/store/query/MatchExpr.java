/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for text match operations (prefix, suffix, infix, exact).
 * 
 * @since 0.1.7
 */
public class MatchExpr extends QueryExpr {
    private final String field;
    private final String value;
    private final MatchMode matchMode;

    /**
     * MatchExpr.
     * 
     * @param field field
     * @param value value
     * @param matchMode matchMode
     * @since 0.1.7
     */
    public MatchExpr(String field, String value, MatchMode matchMode) {
        this.field = field;
        this.value = value;
        this.matchMode = matchMode;
    }

    /**
     * MatchExpr.
     * 
     * @param field field
     * @param value value
     * @since 0.1.7
     */
    public MatchExpr(String field, String value) {
        this(field, value, MatchMode.EXACT);
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
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return value;
    }

    /**
     * getMatchMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public MatchMode getMatchMode() {
        return matchMode;
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
        return QueryLanguageRegistry.get(database).applyTextMatch(this);
    }
}
