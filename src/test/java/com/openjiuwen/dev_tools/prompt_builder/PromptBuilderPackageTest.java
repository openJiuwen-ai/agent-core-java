/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.dev_tools.prompt_builder.builder.BadCasePromptBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.FeedbackPromptBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.MetaTemplateBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder} module in
 * {@code openjiuwen/dev_tools/prompt_builder/__init__.py}.
 */
class PromptBuilderPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals("openjiuwen/dev_tools/prompt_builder/__init__.py", PromptBuilderPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "MetaTemplateBuilder",
                "FeedbackPromptBuilder",
                "BadCasePromptBuilder"
        ), PromptBuilderPackage.all());
        assertSame(PromptBuilderPackage.EXPORTED_SYMBOLS, PromptBuilderPackage.all());
    }

    @Test
    void resolvesPromptBuilderExportedTypes() {
        assertSame(MetaTemplateBuilder.class, PromptBuilderPackage.typeFor("MetaTemplateBuilder"));
        assertSame(FeedbackPromptBuilder.class, PromptBuilderPackage.typeFor("FeedbackPromptBuilder"));
        assertSame(BadCasePromptBuilder.class, PromptBuilderPackage.typeFor("BadCasePromptBuilder"));
        assertTrue(PromptBuilderPackage.exports("MetaTemplateBuilder"));
        assertFalse(PromptBuilderPackage.exports("missing"));
    }
}
