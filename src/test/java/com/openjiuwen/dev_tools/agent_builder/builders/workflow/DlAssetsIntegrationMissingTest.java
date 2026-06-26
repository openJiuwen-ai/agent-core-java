/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Missing system-test coverage for DL assets constants.
 * <p>
 * Mirrors Python's {@code test_dl_assets_integration} in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_dl_assets_integration.py}.
 */
class DlAssetsIntegrationMissingTest {

    /**
     * Mirrors Python's {@code TestDLAssetsIntegration} in
     * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_dl_assets_integration.py}.
     */
    @Nested
    class TestDLAssetsIntegration {

        @Test
        void testComponentsInfoIsString() {
            assertInstanceOf(String.class, DlAssets.COMPONENTS_INFO);
            assertFalse(DlAssets.COMPONENTS_INFO.isEmpty());
        }

        @Test
        void testComponentsInfoContainsAllNodes() {
            List.of("Start", "End", "LLM", "IntentDetection", "Questioner",
                            "Code", "Plugin", "Output", "Branch")
                    .forEach(nodeType -> assertTrue(DlAssets.COMPONENTS_INFO.contains(nodeType),
                            () -> "Missing " + nodeType + " in COMPONENTS_INFO"));
        }

        @Test
        void testSchemaInfoIsString() {
            assertInstanceOf(String.class, DlAssets.SCHEMA_INFO);
            assertFalse(DlAssets.SCHEMA_INFO.isEmpty());
        }

        @Test
        void testSchemaInfoContainsNodeFields() {
            List.of("id", "type", "parameters")
                    .forEach(field -> assertTrue(DlAssets.SCHEMA_INFO.contains(field),
                            () -> "Missing " + field + " in SCHEMA_INFO"));
        }

        @Test
        void testExamplesIsString() {
            assertInstanceOf(String.class, DlAssets.EXAMPLES);
        }
    }

    /**
     * Mirrors Python's {@code TestComponentsInfoContent} in
     * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_dl_assets_integration.py}.
     */
    @Nested
    class TestComponentsInfoContent {

        @Test
        void testContainsStartDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "开始节点", "Start"));
        }

        @Test
        void testContainsEndDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "结束节点", "End"));
        }

        @Test
        void testContainsLlmDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "大模型", "LLM"));
        }

        @Test
        void testContainsPluginDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "插件", "Plugin"));
        }

        @Test
        void testContainsCodeDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "代码", "Code"));
        }

        @Test
        void testContainsQuestionerDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "提问", "Questioner"));
        }

        @Test
        void testContainsIntentDetectionDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "意图", "IntentDetection"));
        }

        @Test
        void testContainsBranchDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "分支", "Branch"));
        }

        @Test
        void testContainsOutputDescription() {
            assertTrue(containsAny(DlAssets.COMPONENTS_INFO, "输出", "Output"));
        }
    }

    /**
     * Mirrors Python's {@code TestSchemaInfoContent} in
     * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_dl_assets_integration.py}.
     */
    @Nested
    class TestSchemaInfoContent {

        @Test
        void testContainsStartSchema() {
            assertTrue(containsAny(DlAssets.SCHEMA_INFO, "Start", "开始"));
        }

        @Test
        void testContainsEndSchema() {
            assertTrue(containsAny(DlAssets.SCHEMA_INFO, "End", "结束"));
        }

        @Test
        void testContainsLlmSchema() {
            assertTrue(containsAny(DlAssets.SCHEMA_INFO, "LLM", "大模型"));
        }
    }

    private static boolean containsAny(String actual, String first, String second) {
        return actual.contains(first) || actual.contains(second);
    }
}
