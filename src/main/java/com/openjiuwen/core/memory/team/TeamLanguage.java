/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import java.util.Locale;

/**
 * Public enum TeamLanguage used by the Java parity implementation.
 *
 * @since 1.0
 */
public enum TeamLanguage {
    CN,
    EN;

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
