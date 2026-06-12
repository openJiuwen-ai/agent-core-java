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
 * Mirrors Python's {@code OutputConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/output_converter.py}.
 */
class OutputConverterTest {

    @Test
    void convertBuildsTemplateContentInputsAndInheritedEdge() {
        OutputConverter converter = new OutputConverter(nodeData(true), Map.of(), new Position(3, 4));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_output");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Output.getDslType());
        assertThat(converter.getNode().getData().getInputs().getInputParameters()).containsKeys("answer", "literal");
        assertThat(converter.getNode().getData().getInputs().getInputParameters().get("answer").getType())
                .isEqualTo(SourceType.ref.getValue());
        assertThat(converter.getNode().getData().getInputs().getInputParameters().get("literal").getType())
                .isEqualTo(SourceType.constant.getValue());
        assertThat(converter.getNode().getData().getInputs().getContent())
                .containsEntry("type", "template")
                .containsEntry("content", "{{answer}}\n{{literal}}");
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().getFirst().getSourceNodeId()).isEqualTo("node_output");
        assertThat(converter.getEdges().getFirst().getTargetNodeId()).isEqualTo("node_end");
    }

    @Test
    void inheritedEdgesSkipWhenNextIsAbsent() {
        OutputConverter converter = new OutputConverter(nodeData(false), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getEdges()).isEmpty();
    }

    private static Map<String, Object> nodeData(boolean withNext) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_output");
        node.put("type", "Output");
        node.put("description", "output node");
        node.put("parameters", Map.of(
                "inputs", List.of(
                        Map.of("name", "answer", "value", "${node_llm.answer}"),
                        Map.of("name", "literal", "value", "static text")
                ),
                "configs", Map.of("template", "{{answer}}\n{{literal}}")
        ));
        if (withNext) {
            node.put("next", "node_end");
        }
        return node;
    }
}
