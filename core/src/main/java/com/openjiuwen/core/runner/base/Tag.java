/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Tag constants for categorizing and filtering resources.
 *
 * @since 0.1.7
 */
public final class Tag {
    /**
     * Wildcard matching all tags.
     */
    public static final String ALL = "*";

    /**
     * Global resource tag.
     */
    public static final String GLOBAL = "__global__";

    /**
     * Active resource tag.
     */
    public static final String ACTIVE = "__active__";

    /**
     * Inactive resource tag.
     */
    public static final String INACTIVE = "__inactive__";

    private Tag() {
    }
}
