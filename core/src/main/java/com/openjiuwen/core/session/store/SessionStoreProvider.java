/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import java.util.Map;

/**
 * Provider SPI for creating {@link Store} instances by type name.
 *
 * @since 0.1.7
 */
public interface SessionStoreProvider {
    /**
     * Return the type name this provider handles.
     *
     * @return the store type name
     * @since 0.1.7
     */
    String typeName();

    /**
     * Create a store from the given configuration.
     *
     * @param conf the store configuration, or null for defaults
     * @return the created store
     * @since 0.1.7
     */
    Store createStore(Map<String, Object> conf);
}
