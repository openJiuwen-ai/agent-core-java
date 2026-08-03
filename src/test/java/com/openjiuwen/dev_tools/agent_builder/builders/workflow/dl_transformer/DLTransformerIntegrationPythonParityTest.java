/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_dl_transformer_integration.py}.
 */
@DisplayName("DL transformer integration Python parity tests")
class DLTransformerIntegrationPythonParityTest {

    @Test
    void transformerInitialization() {
        DLTransformer transformer = new DLTransformer();

        assertThat(transformer).isNotNull();
        assertThat(DLTransformer.getDslConverterRegistry()).isNotEmpty();
    }

    @Test
    void transformerRegistryContainsAllTypes() {
        Set<String> expectedTypes = Set.of(
                "Start",
                "End",
                "LLM",
                "IntentDetection",
                "Questioner",
                "Code",
                "Plugin",
                "Output",
                "Branch");

        assertThat(DLTransformer.getDslConverterRegistry().keySet()).containsExactlyInAnyOrderElementsOf(expectedTypes);
    }

    @Test
    void transformToMermaidSimple() {
        String mermaid = DLTransformer.transformToMermaid("""
                [
                    {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": [{"name": "query", "description": "用户输入"}]}},
                    {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                ]
                """);

        assertThat(mermaid).isNotNull();
        assertThat(mermaid).isInstanceOf(String.class);
        assertThat(mermaid).satisfiesAnyOf(
                value -> assertThat(value).contains("graph TD"),
                value -> assertThat(value.toLowerCase()).contains("flowchart"));
    }

    @Test
    void transformToMermaidWithBranch() {
        String mermaid = DLTransformer.transformToMermaid("""
                [
                    {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": []}, "next": "branch1"},
                    {"id": "branch1", "type": "Branch", "description": "分支", "parameters": {"inputs": [], "conditions": [{"branch": "b1", "expression": "default", "next": "end"}]}},
                    {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                ]
                """);

        assertThat(mermaid).isNotNull();
        assertThat(mermaid).isInstanceOf(String.class);
    }

    @Test
    void transformToDslSimple() {
        String dsl = new DLTransformer().transformToDsl("""
                [
                    {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": [{"name": "query", "description": "用户输入"}]}},
                    {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                ]
                """);

        assertThat(dsl).isNotNull();
        assertThat(dsl).isInstanceOf(String.class);
    }

    @Test
    void transformToDslWithResource() {
        Map<String, Object> resource = mapOf(
                "plugins", List.of(mapOf("tool_id", "tool1")),
                "plugin_dict", Map.of(),
                "tool_id_map", Map.of());

        String dsl = new DLTransformer().transformToDsl("""
                [
                    {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": []}},
                    {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                ]
                """, resource);

        assertThat(dsl).isNotNull();
    }

    @Test
    void transformInvalidJsonRaisesError() {
        assertThrows(Exception.class, () -> DLTransformer.transformToMermaid("not a valid json"));
    }

    @Test
    void transformNonArrayRaisesError() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DLTransformer.transformToMermaid("{\"key\": \"value\"}"));

        assertThat(exception.getMessage()).contains("expected JSON array");
    }

    @Test
    void collectPluginEmpty() {
        List<Map<String, Object>> result = DLTransformer.collectPlugin(
                List.of(),
                Map.<String, Map<String, Object>>of(),
                Map.<String, String>of());

        assertThat(result).isEmpty();
    }

    @Test
    void collectPluginSingle() {
        Map<String, Object> tool = mapOf(
                "tool_name", "测试工具",
                "ori_inputs", List.of(),
                "ori_outputs", List.of());
        Map<String, Map<String, Object>> pluginDict = Map.of(
                "plugin1",
                mapOf(
                        "plugin_name", "测试插件",
                        "plugin_version", "1.0.0",
                        "tools", Map.of("tool1", tool)));
        Map<String, String> toolIdMap = Map.of("tool1", "plugin1");

        List<Map<String, Object>> result = DLTransformer.collectPlugin(List.of("tool1"), pluginDict, toolIdMap);

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .containsEntry("tool_id", "tool1")
                .containsEntry("plugin_name", "测试插件");
    }

    @Test
    void collectPluginMissingToolId() {
        List<Map<String, Object>> result = DLTransformer.collectPlugin(
                List.of("non_existent"),
                Map.<String, Map<String, Object>>of(),
                Map.<String, String>of());

        assertThat(result).hasSize(0);
    }

    @Test
    void startConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "start",
                "type", "Start",
                "description", "开始节点",
                "parameters", mapOf("outputs", List.of()));

        StartConverter converter = new StartConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("start");
    }

    @Test
    void startConverterConvert() {
        Map<String, Object> nodeData = mapOf(
                "id", "start",
                "type", "Start",
                "description", "开始节点",
                "parameters", mapOf(
                        "outputs", List.of(mapOf("name", "query", "description", "用户输入"))),
                "next", "next_node");

        StartConverter converter = new StartConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));
        converter.convert();

        assertThat(converter.getNode()).isNotNull();
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().get(0).getTargetNodeId()).isEqualTo("next_node");
    }

    @Test
    void endConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "end",
                "type", "End",
                "description", "结束节点",
                "parameters", mapOf("inputs", List.of(), "configs", mapOf("template", "")));

        EndConverter converter = new EndConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("end");
    }

    @Test
    void endConverterConvert() {
        Map<String, Object> nodeData = mapOf(
                "id", "end",
                "type", "End",
                "description", "结束节点",
                "parameters", mapOf(
                        "inputs", List.of(mapOf("name", "result", "value", "test")),
                        "configs", mapOf("template", "结果: ${result}")));

        EndConverter converter = new EndConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));
        converter.convert();

        assertThat(converter.getNode()).isNotNull();
        assertThat(converter.getEdges()).hasSize(0);
    }

    @Test
    void llmConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "llm1",
                "type", "LLM",
                "description", "LLM节点",
                "parameters", mapOf(
                        "inputs", List.of(),
                        "outputs", List.of(),
                        "configs", mapOf("system_prompt", "", "user_prompt", "")));

        LLMConverter converter = new LLMConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("llm1");
    }

    @Test
    void llmConverterConvert() {
        Map<String, Object> nodeData = mapOf(
                "id", "llm1",
                "type", "LLM",
                "description", "LLM节点",
                "parameters", mapOf(
                        "inputs", List.of(mapOf("name", "prompt", "value", "Hello")),
                        "outputs", List.of(mapOf("name", "response", "description", "响应")),
                        "configs", mapOf("system_prompt", "You are helpful", "user_prompt", "${prompt}")),
                "next", "end");

        LLMConverter converter = new LLMConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));
        converter.convert();

        assertThat(converter.getNode()).isNotNull();
        assertThat(converter.getEdges()).hasSize(1);
    }

    @Test
    void branchConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "branch1",
                "type", "Branch",
                "description", "分支节点",
                "parameters", mapOf("inputs", List.of(), "conditions", List.of()));

        BranchConverter converter = new BranchConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("branch1");
    }

    @Test
    void branchConverterConvert() {
        Map<String, Object> nodeData = mapOf(
                "id", "branch1",
                "type", "Branch",
                "description", "分支节点",
                "parameters", mapOf(
                        "inputs", List.of(mapOf("name", "input", "value", "test")),
                        "conditions", List.of(
                                mapOf(
                                        "branch", "branch1",
                                        "description", "条件1",
                                        "expression", "'test' eq 'yes'",
                                        "next", "node1"),
                                mapOf(
                                        "branch", "default",
                                        "description", "默认",
                                        "expression", "default",
                                        "next", "node2"))));

        BranchConverter converter = new BranchConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));
        converter.convert();

        assertThat(converter.getNode()).isNotNull();
        assertThat(converter.getEdges()).hasSize(2);
    }

    @Test
    void intentDetectionConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "intent1",
                "type", "IntentDetection",
                "description", "意图检测节点",
                "parameters", mapOf("inputs", List.of(), "configs", mapOf("prompt", ""), "conditions", List.of()));

        IntentDetectionConverter converter = new IntentDetectionConverter(
                nodeData,
                Map.<String, Object>of(),
                new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("intent1");
    }

    @Test
    void intentDetectionConverterConvert() {
        Map<String, Object> nodeData = mapOf(
                "id", "intent1",
                "type", "IntentDetection",
                "description", "意图检测节点",
                "parameters", mapOf(
                        "inputs", List.of(mapOf("name", "input", "value", "test")),
                        "configs", mapOf("prompt", "检测意图"),
                        "conditions", List.of(
                                mapOf(
                                        "branch", "intent1",
                                        "description", "意图1",
                                        "expression", "${intent1.rawOutput} contain 'intent1'",
                                        "next", "node1"),
                                mapOf(
                                        "branch", "default",
                                        "description", "默认",
                                        "expression", "default",
                                        "next", "node2"))));

        IntentDetectionConverter converter = new IntentDetectionConverter(
                nodeData,
                Map.<String, Object>of(),
                new Position(0, 0));
        converter.convert();

        assertThat(converter.getNode()).isNotNull();
        assertThat(converter.getEdges()).hasSize(2);
    }

    @Test
    void pluginConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "plugin1",
                "type", "Plugin",
                "description", "插件节点",
                "parameters", mapOf(
                        "inputs", List.of(),
                        "outputs", List.of(),
                        "configs", mapOf("tool_id", "tool1")));

        PluginConverter converter = new PluginConverter(
                nodeData,
                Map.<String, Object>of(),
                Map.<String, Object>of(),
                new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("plugin1");
    }

    @Test
    void codeConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "code1",
                "type", "Code",
                "description", "代码节点",
                "parameters", mapOf(
                        "inputs", List.of(),
                        "outputs", List.of(),
                        "configs", mapOf("code", "")));

        CodeConverter converter = new CodeConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("code1");
    }

    @Test
    void questionerConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "q1",
                "type", "Questioner",
                "description", "提问节点",
                "parameters", mapOf(
                        "inputs", List.of(),
                        "outputs", List.of(),
                        "configs", mapOf("prompt", "")));

        QuestionerConverter converter = new QuestionerConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("q1");
    }

    @Test
    void outputConverterInitialization() {
        Map<String, Object> nodeData = mapOf(
                "id", "output1",
                "type", "Output",
                "description", "输出节点",
                "parameters", mapOf("inputs", List.of(), "configs", mapOf("template", "")));

        OutputConverter converter = new OutputConverter(nodeData, Map.<String, Object>of(), new Position(0, 0));

        assertThat(converter).isNotNull();
        assertThat(converter.getNode().getId()).isEqualTo("output1");
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
