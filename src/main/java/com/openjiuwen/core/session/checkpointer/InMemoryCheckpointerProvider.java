/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.Map;

/**
 * Built-in checkpointer provider for in-memory storage.
 * <p>
 * Creates a shared singleton in-memory checkpointer instance. Suitable for
 * testing and single-process scenarios where persistence is not required.
 *
 * @since 0.1.12
 * @see CheckpointerProvider
 * @see com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer
 */
public final class InMemoryCheckpointerProvider implements CheckpointerProvider {
    private static final Checkpointer INSTANCE = new InMemoryCheckpointer();

    /**
     * Returns the in-memory checkpointer type name.
     *
     * @return the type name "in_memory"
     */
    public String typeName() {
        return "in_memory";
    }

    /**
     * Creates or returns the shared in-memory checkpointer instance.
     *
     * @param conf the configuration map (ignored for in-memory implementation)
     * @return the shared InMemoryCheckpointer instance
     */
    @Override
    public Checkpointer create(Map<String, Object> conf) {
        return INSTANCE;
    }
}
