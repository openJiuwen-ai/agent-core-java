/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import java.util.Map;

/**
 * Abstract base class for key-value storage.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.store.Store}.
 * 
 * @since 0.1.7
 */
public abstract class Store {
    /**
     * read.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public abstract Object read(Object key);

    /**
     * Write data to the store.
     * 
     * @param value the data to write
     * @since 0.1.7
     */
    public abstract void write(Map<String, Object> value);
}
