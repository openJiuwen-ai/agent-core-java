/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace.store;

/**
 * Provider for OBS-backed object storage workspaces.
 *
 * @since 0.1.7
 */
public class ObsWorkspaceStoreProvider extends ObjectStorageWorkspaceStoreProvider {
    public ObsWorkspaceStoreProvider() {
        super("obs");
    }
}
