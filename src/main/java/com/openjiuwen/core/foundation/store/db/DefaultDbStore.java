/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.db;

import com.openjiuwen.core.foundation.store.BaseDbStore;

/**
 * Mirrors Python's {@code DefaultDbStore} in
 * {@code openjiuwen/core/foundation/store/db/default_db_store.py}.
 *
 * @param <E> concrete asynchronous engine handle type
 */
public class DefaultDbStore<E> extends BaseDbStore<E> {

    private final E asyncConn;

    public DefaultDbStore(E asyncConn) {
        this.asyncConn = asyncConn;
    }

    public E getAsyncConn() {
        return asyncConn;
    }

    @Override
    public E getAsyncEngine() {
        return asyncConn;
    }
}
