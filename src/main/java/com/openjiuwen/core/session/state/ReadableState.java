/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.state;

/**
 * Read-only state interface.
 * <p>
 * Mirrors Python's {@code ReadableStateLike}.
 */
public interface ReadableState {

    /**
     * Get value by key (supports str, list, dict schema).
     */
    Object get(Object key);

    /**
     * Get value by key with nested path prefix.
     */
    Object getByPrefix(Object key, String nestedPrefix);
}
