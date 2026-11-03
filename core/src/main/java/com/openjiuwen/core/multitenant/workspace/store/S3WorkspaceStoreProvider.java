/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace.store;

/**
 * Provider for S3-backed object storage workspaces.
 *
 * @since 0.1.7
 */
public class S3WorkspaceStoreProvider extends ObjectStorageWorkspaceStoreProvider {
    public S3WorkspaceStoreProvider() {
        super("s3");
    }
}
