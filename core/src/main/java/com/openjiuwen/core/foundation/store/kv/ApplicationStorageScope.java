/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import com.openjiuwen.spi.store.KVStoreContext;

/** Shared storage scope for one application execution context. */
public final class ApplicationStorageScope {
    private final KVStoreContext kvStores = new KVStoreContext();

    public KVStoreContext kvStores() {
        return kvStores;
    }
}
