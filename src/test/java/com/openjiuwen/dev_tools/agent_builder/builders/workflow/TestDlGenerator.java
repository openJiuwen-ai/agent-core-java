/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test DlGenerator functionality.
 * <p>
 * Mirrors Python's {@code test_dl_generator.py} in
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.workflow.test_dl_generator}.
 */
class TestDlGenerator {

    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            Object mockModel = new Object();
            DlGenerator generator = new DlGenerator(mockModel);

            assertEquals(mockModel, generator.getLlm());
        }

        @Test
        void testInitWithNoneLlm() {
            DlGenerator generator = new DlGenerator(null);

            assertNull(generator.getLlm());
        }
    }

    @Nested
    class TestTemplates {

        @Test
        void testGenerateSystemTemplateExists() {
            assertNotNull(DlGenerator.DL_GENERATE_SYSTEM_TEMPLATE);
        }

        @Test
        void testRefineUserTemplateExists() {
            assertNotNull(DlGenerator.DL_REFINE_USER_TEMPLATE);
        }

        @Test
        void testGenerateSystemTemplateFormat() {
            String messages = DlGenerator.formatGenerateSystemTemplate(
                    "test components",
                    "test schema",
                    "test plugins",
                    "test examples"
            );

            assertTrue(messages.length() > 0);
            assertTrue(messages.contains("test components"));
            assertTrue(messages.contains("test schema"));
        }

        @Test
        void testRefineUserTemplateFormat() {
            String messages = DlGenerator.formatRefineUserTemplate(
                    "test input",
                    "test mermaid",
                    "test dl"
            );

            assertTrue(messages.length() > 0);
            assertTrue(messages.contains("test input"));
            assertTrue(messages.contains("test mermaid"));
            assertTrue(messages.contains("test dl"));
        }
    }
}
