/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.state;

/**
 * Read-only state interface.
 * <p>
 * Mirrors Python's {@code ReadableStateLike}.
 * 
 * @since 0.1.7
 */
public interface ReadableState {
    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    Object get(Object key);

    /**
     * Get value by key with nested path prefix.
     * 
     * @param key key
     * @param nestedPrefix nestedPrefix
     * @return the result
     * @since 0.1.7
     */
    Object getByPrefix(Object key, String nestedPrefix);
}
