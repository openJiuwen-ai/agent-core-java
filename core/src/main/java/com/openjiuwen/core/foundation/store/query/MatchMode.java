/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import java.util.Locale;

/**
 * Mirrors Python's {@code MatchExpr.match_mode} literal values in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public enum MatchMode {
    PREFIX,
    SUFFIX,
    INFIX,
    EXACT;

    public static MatchMode fromValue(String value) {
        return MatchMode.valueOf(value.toUpperCase(Locale.ROOT));
    }

    public String toPythonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
