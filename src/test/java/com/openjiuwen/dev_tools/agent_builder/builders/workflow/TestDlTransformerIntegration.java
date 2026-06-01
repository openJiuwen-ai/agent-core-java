/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.BaseConverter.Position;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.BranchConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.CodeConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DlTransformer;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.EndConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.IntentDetectionConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.LlmConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.OutputConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.PluginConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.QuestionerConverter;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.StartConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * System tests for DL Transformer module.
 * <p>
 * Mirrors Python's {@code test_dl_transformer_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestDlTransformerIntegration {

    private DlTransformer dlTransformer;

    @BeforeEach
    void setUp() {
        dlTransformer = new DlTransformer();
    }

    @Nested
    class TestDlTransformerIntegrationInner {

        @Test
        void testTransformerInitialization() {
            assertThat(dlTransformer).isNotNull();
            assertThat(DlTransformer.getDslConverterRegistry()).isNotEmpty();
        }

        @Test
        void testTransformerRegistryContainsAllTypes() {
            Map<String, Class<?>> registry = DlTransformer.getDslConverterRegistry();
            assertThat(registry.keySet()).containsExactlyInAnyOrder(
                    "Start", "End", "LLM", "IntentDetection",
                    "Questioner", "Code", "Plugin", "Output", "Branch"
            );
        }

        @Test
        void testTransformToMermaidSimple() {
            String dlContent = """
                    [
                      {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": [{"name": "query", "description": "用户输入"}]}},
                      {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                    ]
                    """;

            String mermaid = DlTransformer.transformToMermaid(dlContent);

            assertThat(mermaid).isNotBlank();
            assertThat(mermaid).contains("graph TD");
        }

        @Test
        void testTransformToMermaidWithBranch() {
            String dlContent = """
                    [
                      {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": []}, "next": "branch1"},
                      {"id": "branch1", "type": "Branch", "description": "分支", "parameters": {"inputs": [], "conditions": [{"branch": "b1", "expression": "default", "next": "end"}]}},
                      {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                    ]
                    """;

            String mermaid = DlTransformer.transformToMermaid(dlContent);

            assertThat(mermaid).isNotBlank();
            assertThat(mermaid).contains("branch1");
            assertThat(mermaid).contains("end");
        }

        @Test
        void testTransformToDslSimple() {
            String dlContent = """
                    [
                      {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": [{"name": "query", "description": "用户输入"}]}},
                      {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                    ]
                    """;

            String dsl = dlTransformer.transformToDsl(dlContent);

            assertThat(dsl).isNotBlank();
            assertThat(dsl).contains("\"nodes\"");
            assertThat(dsl).contains("\"edges\"");
        }

        @Test
        void testTransformToDslWithResource() {
            String dlContent = """
                    [
                      {"id": "start", "type": "Start", "description": "开始", "parameters": {"outputs": []}},
                      {"id": "end", "type": "End", "description": "结束", "parameters": {"inputs": [], "configs": {"template": ""}}}
                    ]
                    """;
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("plugins", List.of(Map.of("tool_id", "tool1")));
            resource.put("plugin_dict", Map.of());
            resource.put("tool_id_map", Map.of());

            String dsl = dlTransformer.transformToDsl(dlContent, resource);

            assertThat(dsl).isNotBlank();
            assertThat(dsl).contains("\"nodes\"");
        }

        @Test
        void testTransformInvalidJsonRaisesError() {
            assertThrows(IllegalArgumentException.class, () -> DlTransformer.transformToMermaid("not a valid json"));
        }

        @Test
        void testTransformNonArrayRaisesError() {
            assertThrows(IllegalArgumentException.class, () -> DlTransformer.transformToMermaid("{\"key\": \"value\"}"));
        }
    }

    @Nested
    class TestCollectPlugin {

        @Test
        void testCollectPluginEmpty() {
            List<Map<String, Object>> result = DlTransformer.collectPlugin(List.of(), Map.of(), Map.of());
            assertThat(result).isEmpty();
        }

        @Test
        void testCollectPluginSingle() {
            Map<String, Object> pluginDict = new LinkedHashMap<>();
            pluginDict.put("plugin1", Map.of(
                    "plugin_name", "测试插件",
                    "plugin_version", "1.0.0",
                    "tools", Map.of("tool1", Map.of(
                            "tool_name", "测试工具",
                            "ori_inputs", List.of(),
                            "ori_outputs", List.of()
                    ))
            ));

            List<Map<String, Object>> result = DlTransformer.collectPlugin(
                    List.of("tool1"),
                    pluginDict,
                    Map.of("tool1", "plugin1")
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("tool_id", "tool1");
            assertThat(result.get(0)).containsEntry("plugin_name", "测试插件");
        }

        @Test
        void testCollectPluginMissingToolId() {
            List<Map<String, Object>> result = DlTransformer.collectPlugin(
                    List.of("non_existent"),
                    Map.of(),
                    Map.of()
            );
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class TestStartConverterCases {

        @Test
        void testStartConverterInitialization() {
            StartConverter converter = new StartConverter(Map.of(
                    "id", "start",
                    "type", "Start",
                    "description", "开始节点",
                    "parameters", Map.of("outputs", List.of())
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("start");
        }

        @Test
        void testStartConverterConvert() {
            StartConverter converter = new StartConverter(Map.of(
                    "id", "start",
                    "type", "Start",
                    "description", "开始节点",
                    "parameters", Map.of("outputs", List.of(Map.of("name", "query", "description", "用户输入"))),
                    "next", "next_node"
            ), Map.of(), new Position(0, 0));

            converter.convert();

            assertThat(converter.getNode()).isNotNull();
            assertThat(converter.getEdges()).hasSize(1);
            assertThat(converter.getEdges().get(0).getTargetNodeId()).isEqualTo("next_node");
        }
    }

    @Nested
    class TestEndConverterCases {

        @Test
        void testEndConverterInitialization() {
            EndConverter converter = new EndConverter(Map.of(
                    "id", "end",
                    "type", "End",
                    "description", "结束节点",
                    "parameters", Map.of("inputs", List.of(), "configs", Map.of("template", ""))
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("end");
        }

        @Test
        void testEndConverterConvert() {
            EndConverter converter = new EndConverter(Map.of(
                    "id", "end",
                    "type", "End",
                    "description", "结束节点",
                    "parameters", Map.of(
                            "inputs", List.of(Map.of("name", "result", "value", "test")),
                            "configs", Map.of("template", "结果: ${result}")
                    )
            ), Map.of(), new Position(0, 0));

            converter.convert();

            assertThat(converter.getNode()).isNotNull();
            assertThat(converter.getEdges()).isEmpty();
        }
    }

    @Nested
    class TestLlmConverterCases {

        @Test
        void testLlmConverterInitialization() {
            LlmConverter converter = new LlmConverter(Map.of(
                    "id", "llm1",
                    "type", "LLM",
                    "description", "LLM节点",
                    "parameters", Map.of(
                            "inputs", List.of(),
                            "outputs", List.of(),
                            "configs", Map.of("system_prompt", "", "user_prompt", "")
                    )
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("llm1");
        }

        @Test
        void testLlmConverterConvert() {
            LlmConverter converter = new LlmConverter(Map.of(
                    "id", "llm1",
                    "type", "LLM",
                    "description", "LLM节点",
                    "parameters", Map.of(
                            "inputs", List.of(Map.of("name", "prompt", "value", "Hello")),
                            "outputs", List.of(Map.of("name", "response", "description", "响应")),
                            "configs", Map.of("system_prompt", "You are helpful", "user_prompt", "${prompt}")
                    ),
                    "next", "end"
            ), Map.of(), new Position(0, 0));

            converter.convert();

            assertThat(converter.getNode()).isNotNull();
            assertThat(converter.getEdges()).hasSize(1);
        }
    }

    @Nested
    class TestBranchConverterCases {

        @Test
        void testBranchConverterInitialization() {
            BranchConverter converter = new BranchConverter(Map.of(
                    "id", "branch1",
                    "type", "Branch",
                    "description", "分支节点",
                    "parameters", Map.of("inputs", List.of(), "conditions", List.of())
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("branch1");
        }

        @Test
        void testBranchConverterConvert() {
            BranchConverter converter = new BranchConverter(Map.of(
                    "id", "branch1",
                    "type", "Branch",
                    "description", "分支节点",
                    "parameters", Map.of(
                            "inputs", List.of(Map.of("name", "input", "value", "test")),
                            "conditions", List.of(
                                    Map.of("branch", "branch1", "description", "条件1",
                                            "expression", "'test' eq 'yes'", "next", "node1"),
                                    Map.of("branch", "default", "description", "默认",
                                            "expression", "default", "next", "node2")
                            )
                    )
            ), Map.of(), new Position(0, 0));

            converter.convert();

            assertThat(converter.getNode()).isNotNull();
            assertThat(converter.getEdges()).hasSize(2);
            assertThat(converter.getEdges()).extracting(edge -> edge.getTargetNodeId())
                    .containsExactlyInAnyOrder("node1", "node2");
        }
    }

    @Nested
    class TestIntentDetectionConverterCases {

        @Test
        void testIntentDetectionConverterInitialization() {
            IntentDetectionConverter converter = new IntentDetectionConverter(Map.of(
                    "id", "intent1",
                    "type", "IntentDetection",
                    "description", "意图检测节点",
                    "parameters", Map.of(
                            "inputs", List.of(),
                            "configs", Map.of("prompt", ""),
                            "conditions", List.of()
                    )
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("intent1");
        }

        @Test
        void testIntentDetectionConverterConvert() {
            IntentDetectionConverter converter = new IntentDetectionConverter(Map.of(
                    "id", "intent1",
                    "type", "IntentDetection",
                    "description", "意图检测节点",
                    "parameters", Map.of(
                            "inputs", List.of(Map.of("name", "input", "value", "test")),
                            "configs", Map.of("prompt", "检测意图"),
                            "conditions", List.of(
                                    Map.of("branch", "intent1", "description", "意图1",
                                            "expression", "${intent1.rawOutput} contain 'intent1'", "next", "node1"),
                                    Map.of("branch", "default", "description", "默认",
                                            "expression", "default", "next", "node2")
                            )
                    )
            ), Map.of(), new Position(0, 0));

            converter.convert();

            assertThat(converter.getNode()).isNotNull();
            assertThat(converter.getEdges()).hasSize(2);
            assertThat(converter.getEdges()).extracting(edge -> edge.getTargetNodeId())
                    .containsExactlyInAnyOrder("node1", "node2");
        }
    }

    @Nested
    class TestPluginConverterCases {

        @Test
        void testPluginConverterInitialization() {
            PluginConverter converter = new PluginConverter(Map.of(
                    "id", "plugin1",
                    "type", "Plugin",
                    "description", "插件节点",
                    "parameters", Map.of(
                            "inputs", List.of(),
                            "outputs", List.of(),
                            "configs", Map.of("tool_id", "tool1")
                    )
            ), Map.of(), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("plugin1");
        }
    }

    @Nested
    class TestCodeConverterCases {

        @Test
        void testCodeConverterInitialization() {
            CodeConverter converter = new CodeConverter(Map.of(
                    "id", "code1",
                    "type", "Code",
                    "description", "代码节点",
                    "parameters", Map.of(
                            "inputs", List.of(),
                            "outputs", List.of(),
                            "configs", Map.of("code", "")
                    )
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("code1");
        }
    }

    @Nested
    class TestQuestionerConverterCases {

        @Test
        void testQuestionerConverterInitialization() {
            QuestionerConverter converter = new QuestionerConverter(Map.of(
                    "id", "q1",
                    "type", "Questioner",
                    "description", "提问节点",
                    "parameters", Map.of(
                            "inputs", List.of(),
                            "outputs", List.of(),
                            "configs", Map.of("prompt", "")
                    )
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("q1");
        }
    }

    @Nested
    class TestOutputConverterCases {

        @Test
        void testOutputConverterInitialization() {
            OutputConverter converter = new OutputConverter(Map.of(
                    "id", "output1",
                    "type", "Output",
                    "description", "输出节点",
                    "parameters", Map.of(
                            "inputs", List.of(),
                            "configs", Map.of("template", "")
                    )
            ), Map.of(), new Position(0, 0));

            assertThat(converter).isNotNull();
            assertThat(converter.getNode().getId()).isEqualTo("output1");
        }
    }
}
