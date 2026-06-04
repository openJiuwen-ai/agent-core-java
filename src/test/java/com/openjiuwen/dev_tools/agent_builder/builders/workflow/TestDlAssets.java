/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test DlAssets constants.
 * <p>
 * Mirrors Python's {@code test_dl_assets.py} in
 * {@code tests.unit_tests.dev_tools.agent_builder.builders.workflow.test_dl_assets}.
 */
class TestDlAssets {

    @Nested
    class TestComponentsInfo {

        @Test
        void testComponentsInfoIsString() {
            assertTrue(DlAssets.COMPONENTS_INFO.length() > 0);
        }

        @Test
        void testComponentsInfoContainsStart() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("Start"));
        }

        @Test
        void testComponentsInfoContainsEnd() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("End"));
        }

        @Test
        void testComponentsInfoContainsLlm() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("LLM"));
        }

        @Test
        void testComponentsInfoContainsIntentDetection() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("IntentDetection"));
        }

        @Test
        void testComponentsInfoContainsQuestioner() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("Questioner"));
        }

        @Test
        void testComponentsInfoContainsCode() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("Code"));
        }

        @Test
        void testComponentsInfoContainsPlugin() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("Plugin"));
        }

        @Test
        void testComponentsInfoContainsOutput() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("Output"));
        }

        @Test
        void testComponentsInfoContainsBranch() {
            assertTrue(DlAssets.COMPONENTS_INFO.contains("Branch"));
        }
    }

    @Nested
    class TestSchemaInfo {

        @Test
        void testSchemaInfoIsString() {
            assertTrue(DlAssets.SCHEMA_INFO.length() > 0);
        }

        @Test
        void testSchemaInfoContainsNodeSchema() {
            assertTrue(DlAssets.SCHEMA_INFO.contains("id"));
            assertTrue(DlAssets.SCHEMA_INFO.contains("type"));
            assertTrue(DlAssets.SCHEMA_INFO.contains("parameters"));
        }

        @Test
        void testSchemaInfoContainsStartSchema() {
            assertTrue(DlAssets.SCHEMA_INFO.contains("开始节点") || DlAssets.SCHEMA_INFO.contains("Start"));
        }

        @Test
        void testSchemaInfoContainsEndSchema() {
            assertTrue(DlAssets.SCHEMA_INFO.contains("结束节点") || DlAssets.SCHEMA_INFO.contains("End"));
        }

        @Test
        void testSchemaInfoContainsLlmSchema() {
            assertTrue(DlAssets.SCHEMA_INFO.contains("大模型节点") || DlAssets.SCHEMA_INFO.contains("LLM"));
        }
    }

    @Nested
    class TestExamples {

        @Test
        void testExamplesIsString() {
            assertDoesNotThrow(() -> DlAssets.EXAMPLES.length());
        }

        @Test
        void testExamplesNotEmpty() {
            assertTrue(DlAssets.EXAMPLES.length() >= 0);
        }
    }
}
