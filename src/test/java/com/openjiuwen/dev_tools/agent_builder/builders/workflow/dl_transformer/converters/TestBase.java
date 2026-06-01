/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.BaseConverter;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test base converter functionality.
 * <p>
 * Mirrors Python's {@code test_base.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/test_base.py}.
 */
class TestBase {

    @Test
    void testInitSuccess() {
        Map<String, Object> nodeData = basicNode();
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        assertThat(converter.getNodeData()).isEqualTo(nodeData);
        assertThat(converter.getNodesDict()).isEqualTo(Map.of());
        assertThat(converter.getResource()).isNull();
        assertThat(converter.getEdges()).isEmpty();
    }

    @Test
    void testInitWithResource() {
        Map<String, Object> resource = Map.of("plugins", List.of());
        ConcreteConverter converter = new ConcreteConverter(basicNode(), Map.of(), resource, new BaseConverter.Position(0, 0));

        assertThat(converter.getResource()).isEqualTo(resource);
    }

    @Test
    void testInitWithPosition() {
        BaseConverter.Position position = new BaseConverter.Position(100, 200);
        ConcreteConverter converter = new ConcreteConverter(basicNode(), Map.of(), null, position);

        assertThat(converter.getPosition()).isEqualTo(position);
    }

    @Test
    void testInitCreatesNode() {
        ConcreteConverter converter = new ConcreteConverter(basicNode(), Map.of());

        assertThat(converter.getNode().getId()).isEqualTo("node_1");
        assertThat(converter.getNode().getType()).isEqualTo("1");
    }

    @Test
    void testConvertCallsMethods() {
        Map<String, Object> nodeData = basicNode();
        nodeData.put("next", "node_2");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_1");
        assertThat(converter.getEdges()).hasSize(1);
    }

    @Test
    void testConvertCommonConfigSetsId() {
        ConcreteConverter converter = new ConcreteConverter(basicNode(), Map.of());

        converter.runConvertCommonConfig();

        assertThat(converter.getNode().getId()).isEqualTo("node_1");
    }

    @Test
    void testConvertCommonConfigSetsMeta() {
        ConcreteConverter converter = new ConcreteConverter(basicNode(), Map.of(), null, new BaseConverter.Position(100, 200));

        converter.runConvertCommonConfig();

        assertThat(converter.getNode().getMeta()).containsKey("position");
        @SuppressWarnings("unchecked")
        Map<String, Object> position = (Map<String, Object>) converter.getNode().getMeta().get("position");
        assertThat(position).containsEntry("x", 100.0).containsEntry("y", 200.0);
    }

    @Test
    void testConvertCommonConfigSetsTitle() {
        Map<String, Object> nodeData = basicNode();
        nodeData.put("description", "Test Node Description");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.runConvertCommonConfig();

        assertThat(converter.getNode().getData().getTitle()).isEqualTo("Test Node Description");
    }

    @Test
    void testConvertEdgesWithNext() {
        Map<String, Object> nodeData = basicNode();
        nodeData.put("next", "node_2");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.runConvertEdges();

        assertThat(converter.getEdges()).hasSize(1);
        assertThat(converter.getEdges().get(0).getSourceNodeId()).isEqualTo("node_1");
        assertThat(converter.getEdges().get(0).getTargetNodeId()).isEqualTo("node_2");
    }

    @Test
    void testConvertEdgesWithoutNext() {
        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("id", "node_1");
        nodeData.put("type", "End");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.runConvertEdges();

        assertThat(converter.getEdges()).isEmpty();
    }

    @Test
    void testConvertEdgesWithEmptyNext() {
        Map<String, Object> nodeData = basicNode();
        nodeData.put("next", "");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.runConvertEdges();

        assertThat(converter.getEdges()).isEmpty();
    }

    private static Map<String, Object> basicNode() {
        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("id", "node_1");
        nodeData.put("type", "Start");
        nodeData.put("description", "Test Node");
        return nodeData;
    }

    private static final class ConcreteConverter extends BaseConverter {
        private ConcreteConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
            super(nodeData, nodesDict);
        }

        private ConcreteConverter(Map<String, Object> nodeData,
                                  Map<String, Object> nodesDict,
                                  Map<String, Object> resource,
                                  BaseConverter.Position position) {
            super(nodeData, nodesDict, resource, position);
        }

        private void runConvertCommonConfig() {
            convertCommonConfig();
        }

        private void runConvertEdges() {
            convertEdges();
        }

        @Override
        protected void convertSpecificConfig() {
        }
    }
}
