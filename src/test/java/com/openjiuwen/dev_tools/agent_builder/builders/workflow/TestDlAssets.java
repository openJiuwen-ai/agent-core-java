/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * Test DlAssets constants.
 * <p>
 * Mirrors Python's {@code test_dl_assets.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_dl_assets.py}.
 */
class TestDlAssets {

    /**
     * Test COMPONENTS_INFO constant.
     * <p>
     * Mirrors Python's {@code TestComponentsInfo} class.
     */
    static class TestComponentsInfo {

        @Test
        void testComponentsInfoIsString() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO instanceof String);
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.length() > 0);
        }

        @Test
        void testComponentsInfoContainsStart() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("Start"));
        }

        @Test
        void testComponentsInfoContainsEnd() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("End"));
        }

        @Test
        void testComponentsInfoContainsLlm() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("LLM"));
        }

        @Test
        void testComponentsInfoContainsIntentDetection() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("IntentDetection"));
        }

        @Test
        void testComponentsInfoContainsQuestioner() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("Questioner"));
        }

        @Test
        void testComponentsInfoContainsCode() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("Code"));
        }

        @Test
        void testComponentsInfoContainsPlugin() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("Plugin"));
        }

        @Test
        void testComponentsInfoContainsOutput() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("Output"));
        }

        @Test
        void testComponentsInfoContainsBranch() {
            Assertions.assertTrue(DlAssets.COMPONENTS_INFO.contains("Branch"));
        }
    }

    /**
     * Test SCHEMA_INFO constant.
     * <p>
     * Mirrors Python's {@code TestSchemaInfo} class.
     */
    static class TestSchemaInfo {

        @Test
        void testSchemaInfoIsString() {
            Assertions.assertTrue(DlAssets.SCHEMA_INFO instanceof String);
            Assertions.assertTrue(DlAssets.SCHEMA_INFO.length() > 0);
        }

        @Test
        void testSchemaInfoContainsId() {
            Assertions.assertTrue(DlAssets.SCHEMA_INFO.contains("id"));
        }

        @Test
        void testSchemaInfoContainsType() {
            Assertions.assertTrue(DlAssets.SCHEMA_INFO.contains("type"));
        }

        @Test
        void testSchemaInfoContainsParameters() {
            Assertions.assertTrue(DlAssets.SCHEMA_INFO.contains("parameters"));
        }
    }

    /**
     * Test EXAMPLES constant.
     * <p>
     * Mirrors Python's {@code TestExamples} class.
     */
    static class TestExamples {

        @Test
        void testExamplesIsString() {
            Assertions.assertTrue(DlAssets.EXAMPLES instanceof String);
            Assertions.assertTrue(DlAssets.EXAMPLES.length() > 0);
        }

        @Test
        void testExamplesContainsWorkflow() {
            Assertions.assertTrue(DlAssets.EXAMPLES.contains("workflow"));
        }

        @Test
        void testExamplesContainsStart() {
            Assertions.assertTrue(DlAssets.EXAMPLES.contains("Start"));
        }

        @Test
        void testExamplesContainsEnd() {
            Assertions.assertTrue(DlAssets.EXAMPLES.contains("End"));
        }
    }
}