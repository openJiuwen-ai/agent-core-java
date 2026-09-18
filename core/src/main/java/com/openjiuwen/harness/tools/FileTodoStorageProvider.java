/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.nio.file.Path;
import java.util.Map;

/**
 * FileTodoStorageProvider.
 *
 * @since 0.1.7
 */
public class FileTodoStorageProvider implements TodoStorageProvider {
    @Override
    public String typeName() {
        return "file";
    }

    @Override
    public TodoStorage create(Map<String, Object> conf) {
        String basePath = null;
        if (conf != null) {
            Object raw = conf.get("basePath");
            if (raw instanceof String s) {
                basePath = s;
            }
        }
        return new FileTodoStorage(Path.of(basePath != null ? basePath : "."));
    }
}
