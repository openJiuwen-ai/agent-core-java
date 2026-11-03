/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace.store;

import com.openjiuwen.core.multitenant.workspace.WorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.WorkspaceStoreProvider;

import java.util.Map;

/**
 * Base provider for object-storage-backed workspace stores identified by a type name.
 *
 * @since 0.1.7
 */
public class ObjectStorageWorkspaceStoreProvider implements WorkspaceStoreProvider {
    private final String typeName;

    public ObjectStorageWorkspaceStoreProvider(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public String typeName() {
        return typeName;
    }

    @Override
    public WorkspaceStore create(Map<String, Object> conf) {
        Object bucketPrefixValue = conf.getOrDefault("bucketPrefix", "deepagent-workspace");
        String bucketPrefix;
        if (bucketPrefixValue instanceof String s) {
            bucketPrefix = s;
        } else {
            bucketPrefix = "deepagent-workspace";
        }
        Object endpointValue = conf.getOrDefault("endpoint", "");
        String endpoint;
        if (endpointValue instanceof String s) {
            endpoint = s;
        } else {
            endpoint = "";
        }
        return new ObjectStorageWorkspaceStore(typeName, bucketPrefix, endpoint);
    }
}
