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
 * System tests for DL Transformer Converters module.
 * <p>
 * Mirrors Python's {@code test_converters_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow.dl_transformer}.
 */
class TestConvertersIntegration {

    @Test
    void testRegistryContainsAllTypes() {
        assertThat(DlTransformer.getDslConverterRegistry().keySet()).containsExactlyInAnyOrder(
                "Start", "End", "LLM", "IntentDetection", "Questioner", "Code", "Plugin", "Output", "Branch");
    }

    @Test
    void testRegistryValuesAreClasses() {
        for (Class<?> converterClass : DlTransformer.getDslConverterRegistry().values()) {
            assertThat(converterClass).isInstanceOf(Class.class);
        }
    }

    @Test
    void testStartConverterCreation() {
        StartConverter converter = new StartConverter(startNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(startNode());
    }

    @Test
    void testStartConverterConvert() {
        StartConverter converter = new StartConverter(startNode(), Map.of());
        converter.convert();
        assertThat(converter.getNode().getId()).isEqualTo("node_start");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Start.getDslType());
    }

    @Test
    void testEndConverterCreation() {
        EndConverter converter = new EndConverter(endNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(endNode());
    }

    @Test
    void testEndConverterConvert() {
        EndConverter converter = new EndConverter(endNode(), Map.of());
        converter.convert();
        assertThat(converter.getNode().getId()).isEqualTo("node_end");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.End.getDslType());
    }

    @Test
    void testLlmConverterCreation() {
        LlmConverter converter = new LlmConverter(llmNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(llmNode());
    }

    @Test
    void testBranchConverterCreation() {
        BranchConverter converter = new BranchConverter(branchNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(branchNode());
    }

    @Test
    void testPluginConverterCreation() {
        PluginConverter converter = new PluginConverter(pluginNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(pluginNode());
    }

    @Test
    void testCodeConverterCreation() {
        CodeConverter converter = new CodeConverter(codeNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(codeNode());
    }

    @Test
    void testQuestionerConverterCreation() {
        QuestionerConverter converter = new QuestionerConverter(questionerNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(questionerNode());
    }

    @Test
    void testIntentDetectionConverterCreation() {
        IntentDetectionConverter converter = new IntentDetectionConverter(intentNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(intentNode());
    }

    @Test
    void testOutputConverterCreation() {
        OutputConverter converter = new OutputConverter(outputNode(), Map.of());
        assertThat(converter.getNodeData()).isEqualTo(outputNode());
    }

    private static Map<String, Object> startNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_start");
        node.put("type", "Start");
        node.put("description", "开始节点");
        node.put("parameters", Map.of("outputs", List.of(Map.of("name", "query", "description", "用户输入"))));
        node.put("next", "node_end");
        return node;
    }

    private static Map<String, Object> endNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_end");
        node.put("type", "End");
        node.put("description", "结束节点");
        node.put("parameters", Map.of("inputs", List.of(), "configs", Map.of("template", "{{result}}")));
        return node;
    }

    private static Map<String, Object> llmNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_llm");
        node.put("type", "LLM");
        node.put("description", "大模型节点");
        node.put("parameters", Map.of(
                "inputs", List.of(Map.of("name", "query", "value", "${node_start.query}")),
                "outputs", List.of(Map.of("name", "output", "description", "输出")),
                "configs", Map.of("system_prompt", "You are helpful", "user_prompt", "{{query}}")));
        node.put("next", "node_end");
        return node;
    }

    private static Map<String, Object> branchNode() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_branch");
        node.put("type", "Branch");
        node.put("description", "分支节点");
        node.put("parameters", Map.of("conditions", List.of(
                Map.of("branch", "branch_1", "description", "条件1", "next", "node_1"),
                Map.of("branch", "branch_2", "description", "条件2", "next", "node_2"))));
        return node;
    }

    private static Map<String, Object> pluginNode() {
        return Map.of(
                "id", "node_plugin",
                "type", "Plugin",
                "description", "插件节点",
                "parameters", Map.of("plugin_id", "plugin_1", "tool_id", "tool_1", "inputs", List.of(), "outputs", List.of()),
                "next", "node_end");
    }

    private static Map<String, Object> codeNode() {
        return Map.of(
                "id", "node_code",
                "type", "Code",
                "description", "代码节点",
                "parameters", Map.of("language", "python", "code", "print('hello')", "inputs", List.of(), "outputs", List.of()),
                "next", "node_end");
    }

    private static Map<String, Object> questionerNode() {
        return Map.of(
                "id", "node_questioner",
                "type", "Questioner",
                "description", "提问节点",
                "parameters", Map.of("question", "请问有什么可以帮助您？", "outputs", List.of(Map.of("name", "answer", "description", "用户回答"))),
                "next", "node_end");
    }

    private static Map<String, Object> intentNode() {
        return Map.of(
                "id", "node_intent",
                "type", "IntentDetection",
                "description", "意图识别节点",
                "parameters", Map.of("conditions", List.of(
                        Map.of("branch", "branch_1", "description", "查询", "next", "node_1"),
                        Map.of("branch", "branch_2", "description", "闲聊", "next", "node_2"))));
    }

    private static Map<String, Object> outputNode() {
        return Map.of(
                "id", "node_output",
                "type", "Output",
                "description", "输出节点",
                "parameters", Map.of("inputs", List.of(), "outputs", List.of()),
                "next", "node_end");
    }
}
