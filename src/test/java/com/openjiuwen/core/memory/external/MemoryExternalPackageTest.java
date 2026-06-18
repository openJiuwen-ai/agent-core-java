/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the external memory provider package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.external} package facade in
 * {@code openjiuwen/core/memory/external/__init__.py}.</p>
 */
class MemoryExternalPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        assertEquals("openjiuwen/core/memory/external/__init__.py", MemoryExternalPackage.PYTHON_MODULE);
        assertIterableEquals(List.of(
                "MemoryProvider",
                "AgentArtsMemoryProvider",
                "OpenJiuwenMemoryProvider",
                "OpenVikingMemoryProvider",
                "Mem0MemoryProvider"
        ), MemoryExternalPackage.all());
        assertSame(MemoryExternalPackage.EXPORTED_SYMBOLS, MemoryExternalPackage.all());
    }

    @Test
    void resolvesExportSourcesAndJavaTypes() {
        assertTrue(MemoryExternalPackage.exports("MemoryProvider"));
        assertTrue(MemoryExternalPackage.exports("AgentArtsMemoryProvider"));
        assertTrue(MemoryExternalPackage.exports("OpenJiuwenMemoryProvider"));
        assertTrue(MemoryExternalPackage.exports("OpenVikingMemoryProvider"));
        assertTrue(MemoryExternalPackage.exports("Mem0MemoryProvider"));
        assertFalse(MemoryExternalPackage.exports("UnknownProvider"));

        assertEquals("openjiuwen.core.memory.external.provider.MemoryProvider",
                MemoryExternalPackage.sourceFor("MemoryProvider"));
        assertEquals("openjiuwen.core.memory.external.agentarts_memory_provider.AgentArtsMemoryProvider",
                MemoryExternalPackage.sourceFor("AgentArtsMemoryProvider"));
        assertEquals("openjiuwen.core.memory.external.openjiuwen_memory_provider.OpenJiuwenMemoryProvider",
                MemoryExternalPackage.sourceFor("OpenJiuwenMemoryProvider"));
        assertEquals("openjiuwen.core.memory.external.openviking_memory_provider.OpenVikingMemoryProvider",
                MemoryExternalPackage.sourceFor("OpenVikingMemoryProvider"));
        assertEquals("openjiuwen.core.memory.external.mem0_provider.Mem0MemoryProvider",
                MemoryExternalPackage.sourceFor("Mem0MemoryProvider"));

        assertSame(MemoryProvider.class, MemoryExternalPackage.javaTypeFor("MemoryProvider"));
        assertSame(AgentArtsMemoryProvider.class, MemoryExternalPackage.javaTypeFor("AgentArtsMemoryProvider"));
        assertSame(OpenJiuwenMemoryProvider.class, MemoryExternalPackage.javaTypeFor("OpenJiuwenMemoryProvider"));
        assertSame(OpenVikingMemoryProvider.class, MemoryExternalPackage.javaTypeFor("OpenVikingMemoryProvider"));
        assertSame(Mem0MemoryProvider.class, MemoryExternalPackage.javaTypeFor("Mem0MemoryProvider"));
    }
}
