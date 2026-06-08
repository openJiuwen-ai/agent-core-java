/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

/**
 * Abstract base class for raw DB access.
 * <p>
 * Mirrors Python's {@code BaseDbStore} in
 * {@code openjiuwen/core/foundation/store/base_db_store.py}.
 *
 * @param <E> asynchronous engine type exposed by the concrete backend
 */
public abstract class BaseDbStore<E> {

    /**
     * Return the asynchronous engine used by the concrete store implementation.
     *
     * @return asynchronous engine handle
     */
    public abstract E getAsyncEngine();
}
