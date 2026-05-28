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
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestDlAssetsIntegration {

    @Nested
    class TestDLAssetsConstants {

        @Test
        void componentsInfoIsString() {
            assertThat(DlAssets.COMPONENTS_INFO).isNotEmpty();
        }

        @Test
        void componentsInfoContainsAllNodes() {
            String[] nodeTypes = {"Start", "End", "LLM", "IntentDetection",
                    "Questioner", "Code", "Plugin", "Output", "Branch"};
            for (String nodeType : nodeTypes) {
                assertThat(DlAssets.COMPONENTS_INFO).contains(nodeType);
            }
        }

        @Test
        void schemaInfoIsString() {
            assertThat(DlAssets.SCHEMA_INFO).isNotEmpty();
        }

        @Test
        void schemaInfoContainsNodeFields() {
            String[] fields = {"id", "type", "parameters"};
            for (String field : fields) {
                assertThat(DlAssets.SCHEMA_INFO).contains(field);
            }
        }

        @Test
        void examplesIsString() {
            assertThat(DlAssets.EXAMPLES).isNotNull();
        }
    }

    @Nested
    class TestComponentsInfoContent {

        @Test
        void containsStartDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).containsIgnoringCase("Start");
        }

        @Test
        void containsEndDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).containsIgnoringCase("End");
        }

        @Test
        void containsLlmDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).containsIgnoringCase("LLM");
        }

        @Test
        void containsPluginDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).containsIgnoringCase("Plugin");
        }

        @Test
        void containsCodeDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).containsIgnoringCase("Code");
        }

        @Test
        void containsBranchDescription() {
            assertThat(DlAssets.COMPONENTS_INFO).containsIgnoringCase("Branch");
        }
    }
}
