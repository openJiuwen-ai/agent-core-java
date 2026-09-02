/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.Map;

/**
 * Provider interface for creating checkpointer instances.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} from
 * {@code META-INF/services/com.openjiuwen.core.session.checkpointer.CheckpointerProvider}.
 * Each provider declares which {@code typeName()} it supports.
 * Service adapters can also register providers programmatically via
 * {@link CheckpointerFactory#register(String, CheckpointerProvider)}.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.checkpointer.CheckpointerProvider}.
 * 
 * @since 0.1.7
 */
public interface CheckpointerProvider {
    /**
     * typeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    String typeName();

    /**
     * Create a checkpointer with the given configuration.
     * 
     * @param conf the configuration map
     * @return a checkpointer instance
     * @since 0.1.7
     */
    Checkpointer create(Map<String, Object> conf);
}
