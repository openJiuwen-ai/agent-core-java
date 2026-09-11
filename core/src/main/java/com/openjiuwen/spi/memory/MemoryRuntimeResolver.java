/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.memory;

import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Resolves the optional Memory runtime implementation from the application class path.
 *
 * @since 0.1.15
 */
public final class MemoryRuntimeResolver {
    private MemoryRuntimeResolver() {
    }

    /**
     * Find the single available Memory runtime implementation.
     *
     * @return the implementation, or an empty value when Memory is not installed
     * @throws IllegalStateException when multiple implementations are installed
     * @since 0.1.15
     */
    public static Optional<MemoryRuntime> find() {
        Iterator<MemoryRuntime> runtimes = ServiceLoader.load(MemoryRuntime.class).iterator();
        if (!runtimes.hasNext()) {
            return Optional.empty();
        }
        MemoryRuntime runtime = runtimes.next();
        if (runtimes.hasNext()) {
            throw new IllegalStateException("Multiple MemoryRuntime implementations are installed");
        }
        return Optional.of(runtime);
    }

    /**
     * Resolve the required Memory runtime implementation.
     *
     * @return the installed implementation
     * @throws IllegalStateException when no implementation or multiple implementations are installed
     * @since 0.1.15
     */
    public static MemoryRuntime require() {
        return find().orElseThrow(() -> new IllegalStateException(
                "Memory is enabled but agent-core-memory-java is not installed"));
    }
}
