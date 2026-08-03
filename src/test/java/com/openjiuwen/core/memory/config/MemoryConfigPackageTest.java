/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Focused tests for the memory config package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.config} package facade in
 * {@code openjiuwen/core/memory/config/__init__.py}.</p>
 */
class MemoryConfigPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        List<String> expected = List.of(
                "MemoryEngineConfig",
                "MemoryScopeConfig",
                "AgentMemoryConfig"
        );

        assertEquals(expected, MemoryConfigPackage.EXPORTED_SYMBOLS);
        assertSame(MemoryConfigPackage.EXPORTED_SYMBOLS, MemoryConfigPackage.all());
        assertEquals(expected, new ArrayList<>(MemoryConfigPackage.EXPORT_SOURCES.keySet()));
        assertEquals(expected, new ArrayList<>(MemoryConfigPackage.JAVA_TYPE_NAMES.keySet()));
    }

    @Test
    void exportMetadataMatchesPythonImportSource() {
        assertEquals(
                "openjiuwen.core.memory.config.config.MemoryEngineConfig",
                MemoryConfigPackage.sourceFor("MemoryEngineConfig")
        );
        assertEquals(
                "openjiuwen.core.memory.config.config.MemoryScopeConfig",
                MemoryConfigPackage.sourceFor("MemoryScopeConfig")
        );
        assertEquals(
                "openjiuwen.core.memory.config.config.AgentMemoryConfig",
                MemoryConfigPackage.sourceFor("AgentMemoryConfig")
        );

        assertEquals(
                "com.openjiuwen.core.memory.config.MemoryEngineConfig",
                MemoryConfigPackage.javaTypeNameFor("MemoryEngineConfig")
        );
        assertEquals(
                "com.openjiuwen.core.memory.config.MemoryScopeConfig",
                MemoryConfigPackage.javaTypeNameFor("MemoryScopeConfig")
        );
        assertEquals(
                "com.openjiuwen.core.memory.config.AgentMemoryConfig",
                MemoryConfigPackage.javaTypeNameFor("AgentMemoryConfig")
        );

        assertEquals(
                MemoryEngineConfig.class,
                MemoryConfigPackage.resolveType("MemoryEngineConfig").orElseThrow()
        );
        assertEquals(
                MemoryScopeConfig.class,
                MemoryConfigPackage.resolveType("MemoryScopeConfig").orElseThrow()
        );
        assertEquals(
                AgentMemoryConfig.class,
                MemoryConfigPackage.resolveType("AgentMemoryConfig").orElseThrow()
        );
    }

    @Test
    void unknownSymbolIsNotExported() {
        assertTrue(MemoryConfigPackage.exports("MemoryEngineConfig"));
        assertFalse(MemoryConfigPackage.exports("MissingConfig"));
        assertNull(MemoryConfigPackage.sourceFor("MissingConfig"));
        assertNull(MemoryConfigPackage.javaTypeNameFor("MissingConfig"));
        assertTrue(MemoryConfigPackage.resolveType("MissingConfig").isEmpty());
    }

    @Test
    void exportedCollectionsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> MemoryConfigPackage.EXPORTED_SYMBOLS.add("Unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> MemoryConfigPackage.EXPORT_SOURCES.put("Unexpected", "unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> MemoryConfigPackage.JAVA_TYPE_NAMES.put("Unexpected", "Unexpected")
        );
    }
}
