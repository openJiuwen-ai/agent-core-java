/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import java.nio.file.Path;
import java.util.Map;

/**
 * File-backed {@link SessionStoreProvider} that creates {@link FileStore} instances.
 *
 * @since 0.1.7
 */
public class FileSessionStoreProvider implements SessionStoreProvider {
    @Override
    public String typeName() {
        return "file";
    }

    @Override
    public Store createStore(Map<String, Object> conf) {
        Object raw = conf != null ? conf.get("storePath") : null;
        String storePath = raw instanceof String s ? s : null;
        return new FileStore(Path.of(storePath != null ? storePath : "session_store.json"));
    }
}
