/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused parity tests for the prompt package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.prompt} package facade in
 * {@code openjiuwen/core/foundation/prompt/__init__.py}.</p>
 */
class PromptPackageTest {

    @Test
    void exposesPythonAllInOrder() {
        List<String> expected = List.of("PromptTemplate");

        assertEquals("openjiuwen/core/foundation/prompt/__init__.py", PromptPackage.PYTHON_MODULE);
        assertIterableEquals(expected, PromptPackage.PROMPT_TEMPLATE_CLASSES);
        assertSame(PromptPackage.PROMPT_TEMPLATE_CLASSES, PromptPackage.EXPORTED_SYMBOLS);
        assertSame(PromptPackage.EXPORTED_SYMBOLS, PromptPackage.all());
    }
}
