/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the graph extraction prompts package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.prompts} package facade in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/__init__.py}.</p>
 */
class GraphExtractionPromptsPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        assertEquals("openjiuwen/core/memory/graph/extraction/prompts/__init__.py",
                GraphExtractionPromptsPackage.PYTHON_MODULE);
        assertIterableEquals(List.of("TemplateManager"), GraphExtractionPromptsPackage.all());
        assertSame(GraphExtractionPromptsPackage.EXPORTED_SYMBOLS, GraphExtractionPromptsPackage.all());
    }

    @Test
    void resolvesTemplateManagerAlias() {
        assertTrue(GraphExtractionPromptsPackage.exports("TemplateManager"));
        assertFalse(GraphExtractionPromptsPackage.exports("ThreadSafePromptManager"));

        assertEquals("openjiuwen.core.memory.graph.extraction.prompts.manager.ThreadSafePromptManager",
                GraphExtractionPromptsPackage.sourceFor("TemplateManager"));
        assertSame(ThreadSafePromptManager.class, GraphExtractionPromptsPackage.javaTypeFor("TemplateManager"));
    }
}
