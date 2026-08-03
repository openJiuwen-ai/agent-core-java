/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

/**
 * Tag type constants for categorizing and filtering resources.
 *
 * <p>Mirrors Python's {@code Tag} alias and special tag constants in
 * {@code openjiuwen/core/runner/resources_manager/base.py}.</p>
 */
public final class Tag {

    public static final String ALL = "*";

    public static final String GLOBAL = "__global__";

    public static final String ACTIVE = "__active__";

    public static final String INACTIVE = "__inactive__";

    private Tag() {
    }
}
