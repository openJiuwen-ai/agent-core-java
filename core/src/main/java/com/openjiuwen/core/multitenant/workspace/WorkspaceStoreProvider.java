/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant.workspace;

import java.util.Map;

/**
 * Service-provider interface for creating workspace stores by configuration.
 *
 * @since 0.1.7
 */
public interface WorkspaceStoreProvider {
    /**
     * Returns the type name identifying this provider.
     *
     * @return the type name
     * @since 0.1.7
     */
    String typeName();

    /**
     * Creates a workspace store from the given configuration.
     *
     * @param conf the provider configuration
     * @return the created workspace store
     * @since 0.1.7
     */
    WorkspaceStore create(Map<String, Object> conf);
}
