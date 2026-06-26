/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import java.util.List;

/**
 * Focused validation for {@link MemoryPackage}.
 *
 * <p>Mirrors Python's {@code __all__} facade in
 * {@code openjiuwen/core/memory/__init__.py}.</p>
 */
public final class MemoryPackageTest {

    private MemoryPackageTest() {
    }

    public static void main(String[] args) {
        require(MemoryPackage.all().equals(List.of(
                "MemoryEngineConfig",
                "MemoryScopeConfig",
                "AgentMemoryConfig",
                "LongTermMemory"
        )), "__all__ order");
        require(MemoryPackage.exports("AgentMemoryConfig"), "exports AgentMemoryConfig");
        require("openjiuwen.core.memory.long_term_memory.LongTermMemory"
                .equals(MemoryPackage.sourceFor("LongTermMemory")), "LongTermMemory source");
        require("com.openjiuwen.core.memory.LongTermMemory"
                .equals(MemoryPackage.javaTypeNameFor("LongTermMemory")), "LongTermMemory java type");
        require(MemoryPackage.resolveType("MemoryEngineConfig").isPresent(), "config type resolves");
        require(MemoryPackage.resolveType("LongTermMemory").isEmpty(), "future dependency can be unresolved");
        System.out.println("PASS MemoryPackageTest");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
