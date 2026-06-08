/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test DL transformer models functionality.
 * <p>
 * Mirrors Python's {@code test_models.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/test_models.py}.
 */
class TestModels {

    @Test
    void testStartType() {
        assertThat(NodeType.Start.getDlType()).isEqualTo("Start");
        assertThat(NodeType.Start.getDslType()).isEqualTo("1");
    }

    @Test
    void testEndType() {
        assertThat(NodeType.End.getDlType()).isEqualTo("End");
        assertThat(NodeType.End.getDslType()).isEqualTo("2");
    }

    @Test
    void testLlmType() {
        assertThat(NodeType.LLM.getDlType()).isEqualTo("LLM");
        assertThat(NodeType.LLM.getDslType()).isEqualTo("3");
    }

    @Test
    void testIntentDetectionType() {
        assertThat(NodeType.IntentDetection.getDlType()).isEqualTo("IntentDetection");
        assertThat(NodeType.IntentDetection.getDslType()).isEqualTo("6");
    }

    @Test
    void testQuestionerType() {
        assertThat(NodeType.Questioner.getDlType()).isEqualTo("Questioner");
        assertThat(NodeType.Questioner.getDslType()).isEqualTo("7");
    }

    @Test
    void testCodeType() {
        assertThat(NodeType.Code.getDlType()).isEqualTo("Code");
        assertThat(NodeType.Code.getDslType()).isEqualTo("10");
    }

    @Test
    void testPluginType() {
        assertThat(NodeType.Plugin.getDlType()).isEqualTo("Plugin");
        assertThat(NodeType.Plugin.getDslType()).isEqualTo("19");
    }

    @Test
    void testOutputType() {
        assertThat(NodeType.Output.getDlType()).isEqualTo("Output");
        assertThat(NodeType.Output.getDslType()).isEqualTo("9");
    }

    @Test
    void testBranchType() {
        assertThat(NodeType.Branch.getDlType()).isEqualTo("Branch");
        assertThat(NodeType.Branch.getDslType()).isEqualTo("4");
    }

    @Test
    void testRefType() {
        assertThat(SourceType.ref.getValue()).isEqualTo("ref");
    }

    @Test
    void testConstantType() {
        assertThat(SourceType.constant.getValue()).isEqualTo("constant");
    }

    @Test
    void testPositionInitSuccess() {
        Position position = new Position(100.0, 200.0);
        assertThat(position.getX()).isEqualTo(100.0);
        assertThat(position.getY()).isEqualTo(200.0);
    }

    @Test
    void testPositionInitWithIntegers() {
        Position position = new Position(100, 200);
        assertThat(position.getX()).isEqualTo(100.0);
        assertThat(position.getY()).isEqualTo(200.0);
    }

    @Test
    void testPositionInitWithZero() {
        Position position = new Position(0, 0);
        assertThat(position.getX()).isZero();
        assertThat(position.getY()).isZero();
    }

    @Test
    void testInputVariableInitSuccess() {
        InputVariable variable = new InputVariable("ref", List.of("node_start", "query"), Map.of());
        assertThat(variable.getType()).isEqualTo("ref");
        assertThat(variable.getContent()).isEqualTo(List.of("node_start", "query"));
        assertThat(variable.getExtra()).isEmpty();
    }

    @Test
    void testInputVariableInitWithSchema() {
        InputVariable variable = new InputVariable("constant", "test value", Map.of(), Map.of("type", "string"));
        assertThat(variable.getSchema()).isEqualTo(Map.of("type", "string"));
    }

    @Test
    void testInputsFieldInitSuccess() {
        InputsField inputs = new InputsField();
        assertThat(inputs.getInputParameters()).isEmpty();
        assertThat(inputs.getLlmParam()).isNull();
        assertThat(inputs.getSystemPrompt()).isNull();
        assertThat(inputs.getIntents()).isNull();
        assertThat(inputs.getLanguage()).isNull();
        assertThat(inputs.getCode()).isNull();
        assertThat(inputs.getPluginParam()).isNull();
        assertThat(inputs.getContent()).isNull();
        assertThat(inputs.getHistoryEnable()).isNull();
        assertThat(inputs.getMaxResponse()).isNull();
    }

    @Test
    void testInputsFieldInitWithLlmParam() {
        InputsField inputs = new InputsField(Map.of("system_prompt", "test"));
        assertThat(inputs.getLlmParam()).isEqualTo(Map.of("system_prompt", "test"));
    }

    @Test
    void testOutputsFieldInitSuccess() {
        OutputsField outputs = new OutputsField();
        assertThat(outputs.getType()).isEqualTo("object");
        assertThat(outputs.getProperties()).isNull();
        assertThat(outputs.getRequired()).isNull();
        assertThat(outputs.getDescription()).isNull();
        assertThat(outputs.getDefault()).isNull();
        assertThat(outputs.getExtra()).isNull();
        assertThat(outputs.getItems()).isNull();
    }

    @Test
    void testOutputsFieldInitWithType() {
        OutputsField outputs = new OutputsField("string");
        assertThat(outputs.getType()).isEqualTo("string");
    }

    @Test
    void testAddPropertySimple() {
        OutputsField outputs = new OutputsField();
        outputs.addProperty(new OutputPropertySpec(List.of("output"), "output description", 0, "string"));

        assertThat(outputs.getProperties()).containsKey("output");
        assertThat(outputs.getProperties().get("output").getType()).isEqualTo("string");
        assertThat(outputs.getProperties().get("output").getDescription()).isEqualTo("output description");
    }

    @Test
    void testAddPropertyNested() {
        OutputsField outputs = new OutputsField();
        outputs.addProperty(new OutputPropertySpec(List.of("data", "name"), "name description", 0, "string"));

        assertThat(outputs.getProperties()).containsKey("data");
        assertThat(outputs.getProperties().get("data").getType()).isEqualTo("object");
        assertThat(outputs.getProperties().get("data").getProperties()).containsKey("name");
    }

    @Test
    void testAddPropertyWithItems() {
        OutputsField outputs = new OutputsField();
        outputs.addProperty(new OutputPropertySpec(
                List.of("list"), "list description", 0, "array", Map.of("type", "string"), null, null));

        assertThat(outputs.getProperties().get("list").getType()).isEqualTo("array");
        assertThat(outputs.getProperties().get("list").getItems()).isEqualTo(Map.of("type", "string"));
    }

    @Test
    void testAddPropertyObjectType() {
        OutputsField outputs = new OutputsField();
        outputs.addProperty(new OutputPropertySpec(
                List.of("config"), "config description", 0, "object", null, Map.of("key", Map.of()), List.of("key")));

        assertThat(outputs.getProperties().get("config").getType()).isEqualTo("object");
        assertThat(outputs.getProperties().get("config").getProperties()).containsKey("key");
        assertThat(outputs.getProperties().get("config").getRequired()).containsExactly("key");
    }

    @Test
    void testAddPropertyEmptyVariableNames() {
        OutputsField outputs = new OutputsField();
        outputs.addProperty(new OutputPropertySpec(List.of(), "description", 0, "string"));

        assertThat(outputs.getProperties()).isNull();
    }

    @Test
    void testDataConfigInitSuccess() {
        DataConfig config = new DataConfig();
        assertThat(config.getTitle()).isEmpty();
        assertThat(config.getInputs()).isNull();
        assertThat(config.getOutputs()).isNull();
        assertThat(config.getBranches()).isNull();
        assertThat(config.getExceptionConfig()).isNull();
    }

    @Test
    void testDataConfigInitWithTitle() {
        DataConfig config = new DataConfig("Test Node");
        assertThat(config.getTitle()).isEqualTo("Test Node");
    }

    @Test
    void testNodeInitSuccess() {
        Node node = new Node("node_1", "1");
        assertThat(node.getId()).isEqualTo("node_1");
        assertThat(node.getType()).isEqualTo("1");
        assertThat(node.getMeta()).isEmpty();
        assertThat(node.getData().getTitle()).isEmpty();
    }

    @Test
    void testNodeInitWithMeta() {
        Node node = new Node("node_1", "1", Map.of("position", Map.of("x", 100, "y", 200)));
        assertThat(node.getMeta()).isEqualTo(Map.of("position", Map.of("x", 100, "y", 200)));
    }

    @Test
    void testEdgeInitSuccess() {
        Edge edge = new Edge("node_1", "node_2");
        assertThat(edge.getSourceNodeId()).isEqualTo("node_1");
        assertThat(edge.getTargetNodeId()).isEqualTo("node_2");
        assertThat(edge.getSourcePortId()).isNull();
    }

    @Test
    void testEdgeInitWithSourcePort() {
        Edge edge = new Edge("node_1", "node_2", "output_1");
        assertThat(edge.getSourcePortId()).isEqualTo("output_1");
    }

    @Test
    void testWorkflowInitSuccess() {
        Workflow workflow = new Workflow();
        assertThat(workflow.getNodes()).isEmpty();
        assertThat(workflow.getEdges()).isEmpty();
    }
}
