/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test DL transformer functionality.
 * <p>
 * Mirrors Python's {@code test_dl_transformer.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_dl_transformer.py}.
 */
class TestDlTransformer {

    @Test
    void testCollectPluginSuccess() {
        List<String> toolIds = List.of("tool_1", "tool_2");
        Map<String, Object> pluginDict = Map.of(
                "plugin_1", Map.of(
                        "plugin_name", "Test Plugin",
                        "plugin_version", "1.0",
                        "tools", Map.of(
                                "tool_1", Map.of(
                                        "tool_name", "Tool 1",
                                        "ori_inputs", List.of(),
                                        "ori_outputs", List.of()
                                )
                        )
                )
        );
        Map<String, String> toolIdMap = Map.of("tool_1", "plugin_1");

        List<Map<String, Object>> result = DlTransformer.collectPlugin(toolIds, pluginDict, toolIdMap);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("tool_id", "tool_1");
        assertThat(result.get(0)).containsEntry("plugin_id", "plugin_1");
    }

    @Test
    void testCollectPluginMissingTool() {
        List<Map<String, Object>> result = DlTransformer.collectPlugin(List.of("tool_missing"), Map.of(), Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void testCollectPluginEmptyList() {
        List<Map<String, Object>> result = DlTransformer.collectPlugin(List.of(), Map.of(), Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void testCollectPluginWithCode() {
        Map<String, Object> pluginDict = Map.of(
                "plugin_1", Map.of(
                        "plugin_name", "Code Plugin",
                        "plugin_version", "1.0",
                        "tools", Map.of(
                                "tool_1", Map.of(
                                        "tool_name", "Code Tool",
                                        "ori_inputs", List.of(),
                                        "ori_outputs", List.of(),
                                        "language", "python",
                                        "code", "print('hello')"
                                )
                        )
                )
        );

        List<Map<String, Object>> result = DlTransformer.collectPlugin(
                List.of("tool_1"), pluginDict, Map.of("tool_1", "plugin_1"));

        assertThat(result.get(0)).containsEntry("language", "python");
        assertThat(result.get(0)).containsEntry("code", "print('hello')");
    }

    @Test
    void testRegistryContainsAllTypes() {
        assertThat(DlTransformer.getDslConverterRegistry()).containsKeys(
                "Start", "End", "LLM", "IntentDetection", "Questioner", "Code", "Plugin", "Output", "Branch");
    }

    @Test
    void testRegistryValuesAreClasses() {
        for (Class<?> converterClass : DlTransformer.getDslConverterRegistry().values()) {
            assertThat(converterClass).isNotNull();
            assertThat(converterClass.isInterface()).isFalse();
        }
    }

    @Test
    void testInitSuccess() {
        DlTransformer transformer = new DlTransformer();
        assertThat(transformer).isNotNull();
    }
}
