/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Text match filter expression with match mode.
 * <p>
 * Mirrors Python's {@code MatchExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class MatchExpr extends QueryExpr {

    private final String field;
    private final String value;
    private final MatchMode matchMode;

    /**
     * Create a text match expression with explicit match mode.
     *
     * @param field     field name
     * @param value     text value to match
     * @param matchMode match mode (PREFIX, SUFFIX, INFIX, EXACT)
     */
    public MatchExpr(String field, String value, MatchMode matchMode) {
        this.field = field;
        this.value = value;
        this.matchMode = matchMode;
    }

    /**
     * Create a text match expression with default EXACT mode.
     *
     * @param field field name
     * @param value text value to match
     */
    public MatchExpr(String field, String value) {
        this(field, value, MatchMode.EXACT);
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyTextMatch(this);
    }
}
