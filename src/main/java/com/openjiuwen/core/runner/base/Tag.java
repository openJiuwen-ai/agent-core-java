/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Tag type constants for categorizing and filtering resources.
 * <p>
 * Mirrors Python's Tag type alias and special tag constants.
 * 
 * @since 0.1.7
 */
public final class Tag {
    /**
     * ALL.
     * 
     * @since 0.1.7
     */
    public static final String ALL = "*";

    /**
     * GLOBAL.
     * 
     * @since 0.1.7
     */
    public static final String GLOBAL = "__global__";

    /**
     * ACTIVE.
     * 
     * @since 0.1.7
     */
    public static final String ACTIVE = "__active__";

    /**
     * INACTIVE.
     * 
     * @since 0.1.7
     */
    public static final String INACTIVE = "__inactive__";

    /**
     * Tag.
     * 
     * @since 0.1.7
     */
    private Tag() {
    }
}
