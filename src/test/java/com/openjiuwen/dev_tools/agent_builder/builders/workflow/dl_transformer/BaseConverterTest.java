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
 * Mirrors Python's base-converter coverage for
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/base.py}.
 */
class BaseConverterTest {

    @Test
    void convertRunsCommonSpecificAndEdgeStages() {
        TestingConverter converter = new TestingConverter(nodeData(), Map.of(), null, new Position(10, 20));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_llm");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.LLM.getDslType());
        assertThat(converter.getNode().getMeta()).containsEntry("position", Map.of("x", 10.0, "y", 20.0));
        assertThat(converter.getNode().getData().getTitle()).isEqualTo("LLM node");
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().get(0).getSourceNodeId()).isEqualTo("node_llm");
        assertThat(converter.getEdges().get(0).getTargetNodeId()).isEqualTo("node_output");
        assertThat(converter.specificCalled).isTrue();
    }

    @Test
    void convertInputVariablesHandlesReferenceAndConstantValues() {
        TestingConverter converter = new TestingConverter(nodeData(), Map.of(), null, new Position(0, 0));

        Map<String, InputVariable> inputs = converter.exposeConvertInputVariables(List.of(
                Map.of("name", "query", "value", "${node_start.query}"),
                Map.of("name", "city", "value", "Shanghai", "type", "string")
        ));

        assertThat(inputs).containsKeys("query", "city");
        assertThat(inputs.get("query").getType()).isEqualTo(SourceType.ref.getValue());
        assertThat(inputs.get("query").getContent()).isEqualTo(List.of("node_start", "query"));
        assertThat(inputs.get("query").getExtra()).containsEntry("index", 0);
        assertThat(inputs.get("city").getType()).isEqualTo(SourceType.constant.getValue());
        assertThat(inputs.get("city").getContent()).isEqualTo("Shanghai");
        assertThat(inputs.get("city").getSchema()).containsEntry("type", "string");
        assertThat(inputs.get("city").getExtra()).containsEntry("index", 1);
    }

    @Test
    void convertOutputsFieldBuildsNestedPropertiesAndTracksIndexes() {
        TestingConverter converter = new TestingConverter(nodeData(), Map.of(), null, new Position(0, 0));

        OutputsField outputs = converter.exposeConvertOutputsField(List.of(
                Map.of("name", "summary_of_result", "description", "Summary", "type", "string"),
                Map.of(
                        "name", "metadata",
                        "description", "Metadata",
                        "type", "object",
                        "properties", Map.of("source", Map.of("type", "string")),
                        "required", List.of("source")
                )
        ));

        assertThat(outputs.getType()).isEqualTo("object");
        assertThat(outputs.getProperties()).containsKeys("result", "metadata");
        OutputsField resultField = outputs.getProperties().get("result");
        assertThat(resultField.getProperties()).containsKey("summary");
        assertThat(resultField.getProperties().get("summary").getDescription()).isEqualTo("Summary");
        assertThat(resultField.getProperties().get("summary").getExtra()).containsEntry("index", 0);
        OutputsField metadataField = outputs.getProperties().get("metadata");
        assertThat(metadataField.getType()).isEqualTo("object");
        assertThat(metadataField.getRequired()).containsExactly("source");
        assertThat(metadataField.getExtra()).containsEntry("index", 1);
    }

    @Test
    void convertEdgesSkipsMissingNextPointer() {
        Map<String, Object> node = new LinkedHashMap<>(nodeData());
        node.put("next", "");
        TestingConverter converter = new TestingConverter(node, Map.of(), null, new Position(0, 0));

        converter.convertEdges();

        assertThat(converter.getEdges()).isEmpty();
    }

    private static Map<String, Object> nodeData() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_llm");
        node.put("type", "LLM");
        node.put("description", "LLM node");
        node.put("next", "node_output");
        return node;
    }

    private static final class TestingConverter extends BaseConverter {
        private boolean specificCalled;

        private TestingConverter(
                Map<String, Object> nodeData,
                Map<String, Object> nodesDict,
                Map<String, Object> resource,
                Position position) {
            super(nodeData, nodesDict, resource, position);
        }

        @Override
        protected void convertSpecificConfig() {
            specificCalled = true;
        }

        private Map<String, InputVariable> exposeConvertInputVariables(List<Map<String, Object>> inputs) {
            return convertInputVariables(inputs);
        }

        private OutputsField exposeConvertOutputsField(List<Map<String, Object>> outputs) {
            return convertOutputsField(outputs);
        }
    }
}
