/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the Pregel package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.graph.pregel} package facade in
 * {@code openjiuwen/core/graph/pregel/__init__.py}.</p>
 */
class PregelPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        List<String> expected = List.of(
                "PregelBuilder",
                "PregelConfig",
                "Pregel",
                "GraphInterrupt",
                "Interrupt",
                "TASK_STATUS_INTERRUPT",
                "MAX_RECURSIVE_LIMIT",
                "START",
                "END"
        );

        assertEquals(expected, PregelPackage.EXPORTED_SYMBOLS);
        assertSame(PregelPackage.EXPORTED_SYMBOLS, PregelPackage.all());
        assertEquals(expected, new ArrayList<>(PregelPackage.EXPORT_SOURCES.keySet()));
    }

    @Test
    void exportMetadataMatchesPythonImportSources() {
        assertEquals(
                "openjiuwen.core.graph.pregel.builder.PregelBuilder",
                PregelPackage.sourceFor("PregelBuilder")
        );
        assertEquals(
                "openjiuwen.core.graph.pregel.config.PregelConfig",
                PregelPackage.sourceFor("PregelConfig")
        );
        assertEquals(
                "openjiuwen.core.graph.pregel.engine.Pregel",
                PregelPackage.sourceFor("Pregel")
        );
        assertEquals(
                "openjiuwen.core.graph.pregel.base.GraphInterrupt",
                PregelPackage.sourceFor("GraphInterrupt")
        );
        assertEquals(
                "openjiuwen.core.graph.pregel.base.Interrupt",
                PregelPackage.sourceFor("Interrupt")
        );
    }

    @Test
    void resolveTypeFindsExportedClasses() {
        assertEquals(PregelBuilder.class, PregelPackage.resolveType("PregelBuilder").orElseThrow());
        assertEquals(PregelConfig.class, PregelPackage.resolveType("PregelConfig").orElseThrow());
        assertEquals(Pregel.class, PregelPackage.resolveType("Pregel").orElseThrow());
        assertEquals(GraphInterrupt.class, PregelPackage.resolveType("GraphInterrupt").orElseThrow());
        assertEquals(Interrupt.class, PregelPackage.resolveType("Interrupt").orElseThrow());
    }

    @Test
    void constantValuesMirrorPythonConstants() {
        assertEquals(PregelConstants.TASK_STATUS_INTERRUPT,
                PregelPackage.constantValueFor("TASK_STATUS_INTERRUPT"));
        assertEquals(PregelConstants.MAX_RECURSIVE_LIMIT,
                PregelPackage.constantValueFor("MAX_RECURSIVE_LIMIT"));
        assertEquals(PregelConstants.START, PregelPackage.constantValueFor("START"));
        assertEquals(PregelConstants.END, PregelPackage.constantValueFor("END"));
    }

    @Test
    void unknownSymbolIsNotExported() {
        assertTrue(PregelPackage.exports("Pregel"));
        assertFalse(PregelPackage.exports("MissingPregelSymbol"));
        assertNull(PregelPackage.sourceFor("MissingPregelSymbol"));
        assertNull(PregelPackage.javaTypeNameFor("MissingPregelSymbol"));
        assertNull(PregelPackage.constantValueFor("MissingPregelSymbol"));
        assertTrue(PregelPackage.resolveType("MissingPregelSymbol").isEmpty());
    }

    @Test
    void exportedCollectionsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> PregelPackage.EXPORTED_SYMBOLS.add("Unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> PregelPackage.EXPORT_SOURCES.put("Unexpected", "unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> PregelPackage.JAVA_TYPE_NAMES.put("Unexpected", "Unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> PregelPackage.CONSTANT_VALUES.put("Unexpected", "Unexpected")
        );
    }
}
