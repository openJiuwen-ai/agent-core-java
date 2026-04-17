/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import java.util.Map;

/**
 * Placeholder file store (not yet implemented).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.store.FileStore}.
 */
public class FileStore extends Store {

    @Override
    public Object read(Object key) {
        // TODO: Implement file-based storage
        return null;
    }

    @Override
    public void write(Map<String, Object> value) {
        // TODO: Implement file-based storage
    }
}
