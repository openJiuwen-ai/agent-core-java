/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.checkpointer;

import java.util.Map;

/**
 * Provider interface for creating checkpointer instances.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.checkpointer.checkpointer.CheckpointerProvider}.
 */
@FunctionalInterface
public interface CheckpointerProvider {

    /**
     * Create a checkpointer with the given configuration.
     *
     * @param conf the configuration map
     * @return a checkpointer instance
     */
    Checkpointer create(Map<String, Object> conf);
}
