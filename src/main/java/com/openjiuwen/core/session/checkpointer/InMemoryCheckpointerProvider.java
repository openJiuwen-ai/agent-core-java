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
 * @see CheckpointerProvider
 * @see com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer
 * @since 0.1.7
 */
public final class InMemoryCheckpointerProvider implements CheckpointerProvider {
    private static final Checkpointer INSTANCE = new InMemoryCheckpointer();

    /**
     * Returns the in-memory checkpointer type name.
     * 
     * @return the type name "in_memory"
     * @since 0.1.7
     */
    public String typeName() {
        return "in_memory";
    }

    /**
     * Creates or returns the shared in-memory checkpointer instance.
     * 
     * @param conf the configuration map (ignored for in-memory implementation)
     * @return the shared InMemoryCheckpointer instance
     * @since 0.1.7
     */
    @Override
    public Checkpointer create(Map<String, Object> conf) {
        return INSTANCE;
    }
}
