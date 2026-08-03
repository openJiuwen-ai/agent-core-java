/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import java.util.List;

/**
 * Module facade for memory rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/rails/memory/__init__.py}.</p>
 */
public final class MemoryRailsPackage {

    private MemoryRailsPackage() {
    }

    public static List<Class<?>> exports() {
        return List.of(CodingMemoryRail.class, ExternalMemoryRail.class, MemoryRail.class);
    }
}
