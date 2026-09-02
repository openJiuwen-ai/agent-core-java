/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import java.util.Locale;

/**
 * Public enum TeamLanguage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public enum TeamLanguage {
    CN,
    EN;

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
