/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for DL Assets module.
 * <p>
 * Mirrors Python's {@code test_dl_assets_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.test_dl_assets_integration}.
 */
class TestDlAssetsIntegration {

    @Nested
    class TestDLAssetsIntegrationInner {

        @Test
        void testComponentsInfoIsString() {
            assertThat(DlAssets.COMPONENTS_INFO).isNotEmpty();
        }

        @Test
        void testComponentsInfoContainsAllNodes() {
            String[] nodeTypes = {"Start", "End", "LLM", "IntentDetection",
                    "Questioner", "Code", "Plugin", "Output", "Branch"};
            for (String nodeType : nodeTypes) {
                assertThat(DlAssets.COMPONENTS_INFO).contains(nodeType);
            }
        }

        @Test
        void testSchemaInfoIsString() {
            assertThat(DlAssets.SCHEMA_INFO).isNotEmpty();
        }

        @Test
        void testSchemaInfoContainsNodeFields() {
            String[] requiredFields = {"id", "type", "parameters"};
            for (String field : requiredFields) {
                assertThat(DlAssets.SCHEMA_INFO).contains(field);
            }
        }

        @Test
        void testExamplesIsString() {
            assertThat(DlAssets.EXAMPLES).isNotNull();
        }
    }

    @Nested
    class TestComponentsInfoContent {

        @Test
        void testContainsStartDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("开始节点"),
                    text -> assertThat(text).contains("Start"));
        }

        @Test
        void testContainsEndDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("结束节点"),
                    text -> assertThat(text).contains("End"));
        }

        @Test
        void testContainsLlmDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("大模型"),
                    text -> assertThat(text).contains("LLM"));
        }

        @Test
        void testContainsPluginDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("插件"),
                    text -> assertThat(text).contains("Plugin"));
        }

        @Test
        void testContainsCodeDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("代码"),
                    text -> assertThat(text).contains("Code"));
        }

        @Test
        void testContainsQuestionerDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("提问"),
                    text -> assertThat(text).contains("Questioner"));
        }

        @Test
        void testContainsIntentDetectionDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("意图"),
                    text -> assertThat(text).contains("IntentDetection"));
        }

        @Test
        void testContainsBranchDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("分支"),
                    text -> assertThat(text).contains("Branch"));
        }

        @Test
        void testContainsOutputDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("输出"),
                    text -> assertThat(text).contains("Output"));
        }
    }

    @Nested
    class TestSchemaInfoContent {

        @Test
        void testContainsStartSchema() {
            assertThat(DlAssets.SCHEMA_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("Start"),
                    text -> assertThat(text).contains("开始"));
        }

        @Test
        void testContainsEndSchema() {
            assertThat(DlAssets.SCHEMA_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("End"),
                    text -> assertThat(text).contains("结束"));
        }

        @Test
        void testContainsLlmSchema() {
            assertThat(DlAssets.SCHEMA_INFO).satisfiesAnyOf(
                    text -> assertThat(text).contains("LLM"),
                    text -> assertThat(text).contains("大模型"));
        }
    }
}
