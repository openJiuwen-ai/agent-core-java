  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.session.store;

import java.util.Map;

/**
 * Abstract base class for key-value storage.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.store.Store}.
 */
public abstract class Store {

    /**
     * Read a value by key (string or dict schema).
     *
     * @param key the key or schema to read
     * @return the value, or null if not found
     */
    public abstract Object read(Object key);

    /**
     * Write data to the store.
     *
     * @param value the data to write
     */
    public abstract void write(Map<String, Object> value);
}
