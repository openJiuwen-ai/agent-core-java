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
 * Mirrors Python's {@code CodeConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/code_converter.py}.
 */
class CodeConverterTest {

    @Test
    void convertBuildsCodeInputsOutputsExceptionConfigAndEdge() {
        CodeConverter converter = new CodeConverter(nodeData(true), Map.of(), new Position(1, 2));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_code");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Code.getDslType());
        assertThat(converter.getNode().getData().getInputs().getLanguage()).isEqualTo("python");
        assertThat(converter.getNode().getData().getInputs().getCode()).isEqualTo("result = query.upper()");
        assertThat(converter.getNode().getData().getInputs().getInputParameters()).containsKey("query");
        assertThat(converter.getNode().getData().getOutputs().getProperties()).containsKeys("result", "metadata");
        assertThat(converter.getNode().getData().getOutputs().getRequired()).containsExactly("result", "metadata");
        assertThat(converter.getNode().getData().getExceptionConfig())
                .containsEntry("retryTimes", 3)
                .containsEntry("timeoutSeconds", 30)
                .containsEntry("processType", "break");
        assertThat(castMap(converter.getNode().getData().getExceptionConfig().get("executeStep")))
                .containsEntry("defaultStep", "0")
                .containsEntry("errorStep", "1");
        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().get(0).getSourceNodeId()).isEqualTo("node_code");
        assertThat(converter.getEdges().get(0).getTargetNodeId()).isEqualTo("node_end");
        assertThat(converter.getEdges().get(0).getSourcePortId()).isEqualTo("0");
    }

    @Test
    void convertEdgesSkipsWhenNextKeyIsAbsent() {
        CodeConverter converter = new CodeConverter(nodeData(false), Map.of(), new Position(0, 0));

        converter.convertEdges();

        assertThat(converter.getEdges()).isEmpty();
    }

    private static Map<String, Object> nodeData(boolean withNext) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_code");
        node.put("type", "Code");
        node.put("description", "code node");
        node.put("parameters", Map.of(
                "inputs", List.of(Map.of("name", "query", "value", "${node_start.query}")),
                "outputs", List.of(
                        Map.of("name", "result", "description", "code result", "type", "string"),
                        Map.of("name", "metadata", "description", "metadata", "type", "object")
                ),
                "configs", Map.of("code", "result = query.upper()")
        ));
        if (withNext) {
            node.put("next", "node_end");
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
