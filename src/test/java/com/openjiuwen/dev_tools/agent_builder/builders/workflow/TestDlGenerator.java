/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * Test DlGenerator functionality.
 * <p>
 * Mirrors Python's {@code test_dl_generator.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_dl_generator.py}.
 */
class TestDlGenerator {

    /**
     * Test DlGenerator initialization.
     * <p>
     * Mirrors Python's {@code TestDLGeneratorInit} class.
     */
    static class TestInit {

        @Test
        void testInitSuccess() {
            Object mockModel = new Object();
            DlGenerator generator = new DlGenerator(mockModel);

            Assertions.assertEquals(mockModel, generator.getLlm());
        }

        @Test
        void testInitWithNullLlm() {
            DlGenerator generator = new DlGenerator(null);

            Assertions.assertNull(generator.getLlm());
        }
    }

    /**
     * Test DlGenerator templates.
     * <p>
     * Mirrors Python's {@code TestDLGeneratorTemplates} class.
     */
    static class TestTemplates {

        @Test
        void testFormatGenerateSystemTemplate() {
            String result = DlGenerator.formatGenerateSystemTemplate(
                "test components",
                "test schema",
                "test plugins",
                "test examples"
            );

            Assertions.assertNotNull(result);
            Assertions.assertTrue(result.contains("test components"));
            Assertions.assertTrue(result.contains("test schema"));
        }

        @Test
        void testFormatRefineUserTemplate() {
            String result = DlGenerator.formatRefineUserTemplate(
                "test input",
                "test mermaid",
                "test dl"
            );

            Assertions.assertNotNull(result);
            Assertions.assertTrue(result.contains("test input"));
            Assertions.assertTrue(result.contains("test mermaid"));
        }

        @Test
        void testFormatGenerateSystemTemplateWithNulls() {
            String result = DlGenerator.formatGenerateSystemTemplate(
                null,
                null,
                null,
                null
            );

            Assertions.assertNotNull(result);
        }

        @Test
        void testFormatRefineUserTemplateWithNulls() {
            String result = DlGenerator.formatRefineUserTemplate(
                null,
                null,
                null
            );

            Assertions.assertNotNull(result);
        }
    }
}