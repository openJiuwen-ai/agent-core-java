/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStoreProvider;

import java.util.Map;

/**
 * Provider for local-filesystem workspace stores.
 *
 * @since 0.1.7
 */
public class LocalWorkspaceStoreProvider implements WorkspaceStoreProvider {
    @Override
    public String typeName() {
        return "local";
    }

    @Override
    public WorkspaceStore create(Map<String, Object> conf) {
        Object basePathValue = conf.getOrDefault("basePath", System.getProperty("user.dir"));
        String basePath;
        if (basePathValue instanceof String s) {
            basePath = s;
        } else {
            basePath = System.getProperty("user.dir");
        }
        return new LocalWorkspaceStore(basePath);
    }
}
