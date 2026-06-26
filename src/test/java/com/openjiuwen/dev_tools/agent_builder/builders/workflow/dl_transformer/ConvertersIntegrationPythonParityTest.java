/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's converter integration tests in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_converters_integration.py}.</p>
 */
class ConvertersIntegrationPythonParityTest {

    @Test
    void registryContainsAllTypes() {
        Set<String> expectedTypes = Set.of(
                "Start",
                "End",
                "LLM",
                "IntentDetection",
                "Questioner",
                "Code",
                "Plugin",
                "Output",
                "Branch"
        );

        assertThat(DLTransformer.getDslConverterRegistry().keySet()).containsExactlyInAnyOrderElementsOf(expectedTypes);
    }

    @Test
    void registryValuesAreClasses() {
        assertThat(DLTransformer.getDslConverterRegistry().values())
                .allSatisfy(converterClass -> assertThat(BaseConverter.class).isAssignableFrom(converterClass));
    }

    @Test
    void startConverterCreation() {
        Map<String, Object> nodeData = startNodeData();

        StartConverter converter = new StartConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void startConverterConvert() {
        Map<String, Object> nodeData = startNodeData();
        StartConverter converter = new StartConverter(nodeData, Map.of());

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_start");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Start.getDslType());
    }

    @Test
    void endConverterCreation() {
        Map<String, Object> nodeData = endNodeData();

        EndConverter converter = new EndConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void endConverterConvert() {
        Map<String, Object> nodeData = endNodeData();
        EndConverter converter = new EndConverter(nodeData, Map.of());

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_end");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.End.getDslType());
    }

    @Test
    void llmConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_llm",
                "type", "LLM",
                "description", "大模型节点",
                "parameters", mapOf(
                        "inputs", List.of(mapOf("name", "query", "value", "${node_start.query}")),
                        "outputs", List.of(mapOf("name", "output", "description", "输出")),
                        "configs", mapOf("system_prompt", "You are helpful", "user_prompt", "{{query}}")
                ),
                "next", "node_end"
        );

        LLMConverter converter = new LLMConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void branchConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_branch",
                "type", "Branch",
                "description", "分支节点",
                "parameters", mapOf(
                        "conditions", List.of(
                                mapOf("branch", "branch_1", "description", "条件1", "next", "node_1"),
                                mapOf("branch", "branch_2", "description", "条件2", "next", "node_2")
                        )
                )
        );

        BranchConverter converter = new BranchConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void pluginConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_plugin",
                "type", "Plugin",
                "description", "插件节点",
                "parameters", mapOf(
                        "plugin_id", "plugin_1",
                        "tool_id", "tool_1",
                        "inputs", List.of(),
                        "outputs", List.of()
                ),
                "next", "node_end"
        );

        PluginConverter converter = new PluginConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void codeConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_code",
                "type", "Code",
                "description", "代码节点",
                "parameters", mapOf(
                        "language", "python",
                        "code", "print('hello')",
                        "inputs", List.of(),
                        "outputs", List.of()
                ),
                "next", "node_end"
        );

        CodeConverter converter = new CodeConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void questionerConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_questioner",
                "type", "Questioner",
                "description", "提问节点",
                "parameters", mapOf(
                        "question", "请问有什么可以帮助您？",
                        "outputs", List.of(mapOf("name", "answer", "description", "用户回答"))
                ),
                "next", "node_end"
        );

        QuestionerConverter converter = new QuestionerConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void intentDetectionConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_intent",
                "type", "IntentDetection",
                "description", "意图识别节点",
                "parameters", mapOf(
                        "conditions", List.of(
                                mapOf("branch", "branch_1", "description", "查询", "next", "node_1"),
                                mapOf("branch", "branch_2", "description", "闲聊", "next", "node_2")
                        )
                )
        );

        IntentDetectionConverter converter = new IntentDetectionConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    @Test
    void outputConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_output",
                "type", "Output",
                "description", "输出节点",
                "parameters", mapOf(
                        "inputs", List.of(),
                        "outputs", List.of()
                ),
                "next", "node_end"
        );

        OutputConverter converter = new OutputConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static Map<String, Object> startNodeData() {
        return mapOf(
                "id", "node_start",
                "type", "Start",
                "description", "开始节点",
                "parameters", mapOf(
                        "outputs", List.of(mapOf("name", "query", "description", "用户输入"))
                ),
                "next", "node_end"
        );
    }

    private static Map<String, Object> endNodeData() {
        return mapOf(
                "id", "node_end",
                "type", "End",
                "description", "结束节点",
                "parameters", mapOf(
                        "inputs", List.of(),
                        "configs", mapOf("template", "{{result}}")
                )
        );
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }
}
