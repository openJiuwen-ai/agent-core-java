/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.memory;

import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Resolves optional Harness memory rails from the Memory module.
 *
 * @since 0.1.15
 */
public final class HarnessMemoryRailsResolver {
    private HarnessMemoryRailsResolver() {
    }

    /**
     * Find the installed Harness memory rails implementation.
     *
     * @return the implementation, or empty when Memory is not installed
     * @since 0.1.15
     */
    public static Optional<HarnessMemoryRails> find() {
        Iterator<HarnessMemoryRails> rails = ServiceLoader.load(HarnessMemoryRails.class).iterator();
        if (!rails.hasNext()) {
            return Optional.empty();
        }
        HarnessMemoryRails first = rails.next();
        if (rails.hasNext()) {
            throw new IllegalStateException("Multiple HarnessMemoryRails implementations are installed");
        }
        return Optional.of(first);
    }
}
