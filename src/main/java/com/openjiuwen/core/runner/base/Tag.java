  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.runner.base;

/**
 * Tag type constants for categorizing and filtering resources.
 * <p>
 * Mirrors Python's Tag type alias and special tag constants.
 */
public final class Tag {

    /** Special tag matching all resources. */
    public static final String ALL = "*";

    /** Default tag for untagged resources. */
    public static final String GLOBAL = "__global__";

    /** Active state tag. */
    public static final String ACTIVE = "__active__";

    /** Inactive state tag. */
    public static final String INACTIVE = "__inactive__";

    private Tag() {
    }
}
