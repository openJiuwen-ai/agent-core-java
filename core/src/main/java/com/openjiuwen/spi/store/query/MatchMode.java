/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.util.Locale;

/**
 * Match mode for text matching operations.
 * <p>
 * Mirrors Python's {@code MatchExpr.match_mode} literal values in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public enum MatchMode {
    PREFIX,
    SUFFIX,
    INFIX,
    EXACT;

    /**
     * Parse a match mode from its string value (case-insensitive).
     *
     * @param value string representation of the match mode
     * @return corresponding MatchMode
     */
    public static MatchMode fromValue(String value) {
        return MatchMode.valueOf(value.toUpperCase(Locale.ROOT));
    }

    /**
     * Return the Python-style lowercase value for this match mode.
     *
     * @return lowercase name matching Python convention
     */
    public String toPythonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
