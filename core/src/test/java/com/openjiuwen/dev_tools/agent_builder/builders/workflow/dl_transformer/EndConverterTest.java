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
 * Mirrors Python's {@code EndConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/end_converter.py}.
 */
class EndConverterTest {

    @Test
    void convertBuildsInputParametersWithoutEdgesWhenNoNextExists() {
        EndConverter converter = new EndConverter(nodeData(), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_end");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.End.getDslType());
        assertThat(converter.getNode().getData().getInputs().getInputParameters()).containsKeys("answer", "literal");
        assertThat(converter.getNode().getData().getInputs().getInputParameters().get("answer").getType())
                .isEqualTo(SourceType.ref.getValue());
        assertThat(converter.getNode().getData().getInputs().getInputParameters().get("literal").getType())
                .isEqualTo(SourceType.constant.getValue());
        assertThat(converter.getEdges()).isEmpty();
    }

    private static Map<String, Object> nodeData() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_end");
        node.put("type", "End");
        node.put("description", "end node");
        node.put("parameters", Map.of(
                "inputs", List.of(
                        Map.of("name", "answer", "value", "${node_llm.rawOutput}"),
                        Map.of("name", "literal", "value", "done")
                ),
                "configs", Map.of("template", "{{answer}}")
        ));
        return node;
    }
}
