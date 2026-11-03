/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace.store;

/**
 * Provider for HDFS-backed object storage workspaces.
 *
 * @since 0.1.7
 */
public class HdfsWorkspaceStoreProvider extends ObjectStorageWorkspaceStoreProvider {
    public HdfsWorkspaceStoreProvider() {
        super("hdfs");
    }
}
