/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for context-evolver tool package exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.tool} package facade in
 * {@code openjiuwen/extensions/context_evolver/tool/__init__.py}.</p>
 */
class ContextEvolverToolPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        assertEquals("openjiuwen/extensions/context_evolver/tool/__init__.py",
                ContextEvolverToolPackage.PYTHON_MODULE);
        assertIterableEquals(List.of("wikipedia_tool"), ContextEvolverToolPackage.all());
        assertSame(ContextEvolverToolPackage.EXPORTED_SYMBOLS, ContextEvolverToolPackage.all());
    }

    @Test
    void resolvesWikipediaToolExport() {
        assertTrue(ContextEvolverToolPackage.exports("wikipedia_tool"));
        assertFalse(ContextEvolverToolPackage.exports("wikipedia_tool_card"));
        assertEquals("openjiuwen.extensions.context_evolver.tool.wikipedia_tool.wikipedia_tool",
                ContextEvolverToolPackage.sourceFor("wikipedia_tool"));
        assertSame(WikipediaTool.WIKIPEDIA_TOOL, ContextEvolverToolPackage.localFunctionFor("wikipedia_tool"));
    }

    @Test
    void returnsNullForUnknownSymbols() {
        assertEquals(null, ContextEvolverToolPackage.sourceFor("missing"));
        assertEquals(null, ContextEvolverToolPackage.localFunctionFor("missing"));
    }
}
