/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental system parity tests for DL transformer converter integration.
 *
 * <p>Mirrors Python's
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_converters_integration.py}.
 * </p>
 */
class DLTransformerConvertersIntegrationPythonParityTest {

    private static final String SOURCE = "tests/system_tests/dev_tools/agent_builder/builders/workflow/"
            + "dl_transformer/test_converters_integration.py";

    @TestFactory
    Collection<DynamicTest> pythonConverterIntegrationCases() {
        return List.of(
                caseOf("TestDLTransformerRegistryIntegration::test_registry_contains_all_types",
                        DLTransformerConvertersIntegrationPythonParityTest::registryContainsAllTypes),
                caseOf("TestDLTransformerRegistryIntegration::test_registry_values_are_classes",
                        DLTransformerConvertersIntegrationPythonParityTest::registryValuesAreClasses),
                caseOf("TestStartConverterIntegration::test_start_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::startConverterCreation),
                caseOf("TestStartConverterIntegration::test_start_converter_convert",
                        DLTransformerConvertersIntegrationPythonParityTest::startConverterConvert),
                caseOf("TestEndConverterIntegration::test_end_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::endConverterCreation),
                caseOf("TestEndConverterIntegration::test_end_converter_convert",
                        DLTransformerConvertersIntegrationPythonParityTest::endConverterConvert),
                caseOf("TestLLMConverterIntegration::test_llm_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::llmConverterCreation),
                caseOf("TestBranchConverterIntegration::test_branch_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::branchConverterCreation),
                caseOf("TestPluginConverterIntegration::test_plugin_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::pluginConverterCreation),
                caseOf("TestCodeConverterIntegration::test_code_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::codeConverterCreation),
                caseOf("TestQuestionerConverterIntegration::test_questioner_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::questionerConverterCreation),
                caseOf("TestIntentDetectionConverterIntegration::test_intent_detection_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::intentDetectionConverterCreation),
                caseOf("TestOutputConverterIntegration::test_output_converter_creation",
                        DLTransformerConvertersIntegrationPythonParityTest::outputConverterCreation)
        );
    }

    private static DynamicTest caseOf(String pythonNode, ExecutableCase executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable::run);
    }

    private static void registryContainsAllTypes() {
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

    private static void registryValuesAreClasses() {
        assertThat(DLTransformer.getDslConverterRegistry().values())
                .allSatisfy(converterClass -> assertThat(BaseConverter.class).isAssignableFrom(converterClass));
    }

    private static void startConverterCreation() {
        Map<String, Object> nodeData = startNodeData();
        StartConverter converter = new StartConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void startConverterConvert() {
        StartConverter converter = new StartConverter(startNodeData(), Map.of());

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_start");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Start.getDslType());
    }

    private static void endConverterCreation() {
        Map<String, Object> nodeData = endNodeData();
        EndConverter converter = new EndConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void endConverterConvert() {
        EndConverter converter = new EndConverter(endNodeData(), Map.of());

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_end");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.End.getDslType());
    }

    private static void llmConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_llm",
                "type", "LLM",
                "description", "LLM node",
                "parameters", mapOf(
                        "inputs", List.of(mapOf("name", "query", "value", "${node_start.query}")),
                        "outputs", List.of(mapOf("name", "output", "description", "output")),
                        "configs", mapOf("system_prompt", "You are helpful", "user_prompt", "{{query}}")),
                "next", "node_end"
        );

        LLMConverter converter = new LLMConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void branchConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_branch",
                "type", "Branch",
                "description", "branch node",
                "parameters", mapOf(
                        "conditions", List.of(
                                mapOf("branch", "branch_1", "description", "condition 1", "next", "node_1"),
                                mapOf("branch", "branch_2", "description", "condition 2", "next", "node_2")))
        );

        BranchConverter converter = new BranchConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void pluginConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_plugin",
                "type", "Plugin",
                "description", "plugin node",
                "parameters", mapOf(
                        "plugin_id", "plugin_1",
                        "tool_id", "tool_1",
                        "inputs", List.of(),
                        "outputs", List.of()),
                "next", "node_end"
        );

        PluginConverter converter = new PluginConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void codeConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_code",
                "type", "Code",
                "description", "code node",
                "parameters", mapOf(
                        "language", "python",
                        "code", "print('hello')",
                        "inputs", List.of(),
                        "outputs", List.of()),
                "next", "node_end"
        );

        CodeConverter converter = new CodeConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void questionerConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_questioner",
                "type", "Questioner",
                "description", "questioner node",
                "parameters", mapOf(
                        "question", "How can I help?",
                        "outputs", List.of(mapOf("name", "answer", "description", "answer"))),
                "next", "node_end"
        );

        QuestionerConverter converter = new QuestionerConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void intentDetectionConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_intent",
                "type", "IntentDetection",
                "description", "intent detection node",
                "parameters", mapOf(
                        "conditions", List.of(
                                mapOf("branch", "branch_1", "description", "query", "next", "node_1"),
                                mapOf("branch", "branch_2", "description", "chat", "next", "node_2")))
        );

        IntentDetectionConverter converter = new IntentDetectionConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static void outputConverterCreation() {
        Map<String, Object> nodeData = mapOf(
                "id", "node_output",
                "type", "Output",
                "description", "output node",
                "parameters", mapOf("inputs", List.of(), "outputs", List.of()),
                "next", "node_end"
        );

        OutputConverter converter = new OutputConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isSameAs(nodeData);
    }

    private static Map<String, Object> startNodeData() {
        return mapOf(
                "id", "node_start",
                "type", "Start",
                "description", "start node",
                "parameters", mapOf(
                        "outputs", List.of(mapOf("name", "query", "description", "user input"))),
                "next", "node_end"
        );
    }

    private static Map<String, Object> endNodeData() {
        return mapOf(
                "id", "node_end",
                "type", "End",
                "description", "end node",
                "parameters", mapOf("inputs", List.of(), "configs", mapOf("template", "{{result}}"))
        );
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    @FunctionalInterface
    private interface ExecutableCase {
        void run() throws Exception;
    }
}
