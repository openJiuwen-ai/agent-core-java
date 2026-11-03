/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code test_base} module in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/test_base.py}.</p>
 */
class BaseConverterMissingTest {

    @Test
    void initSuccess() {
        Map<String, Object> nodeData = startNodeData();
        Map<String, Object> nodesDict = new LinkedHashMap<>();

        ConcreteConverter converter = new ConcreteConverter(nodeData, nodesDict);

        assertSame(nodeData, converter.getNodeData());
        assertSame(nodesDict, converter.getNodesDict());
        assertNull(converter.getResource());
        assertTrue(converter.getEdges().isEmpty());
    }

    @Test
    void initWithResource() {
        Map<String, Object> resource = Map.of("plugins", java.util.List.of());

        ConcreteConverter converter = new ConcreteConverter(startNodeData(), Map.of(), resource);

        assertSame(resource, converter.getResource());
    }

    @Test
    void initWithPosition() {
        Position position = new Position(100, 200);

        ConcreteConverter converter = new ConcreteConverter(startNodeData(), Map.of(), position);

        assertSame(position, converter.getPosition());
    }

    @Test
    void initCreatesNode() {
        ConcreteConverter converter = new ConcreteConverter(startNodeData(), Map.of());

        assertEquals("node_1", converter.getNode().getId());
        assertEquals(NodeType.Start.getDslType(), converter.getNode().getType());
    }

    @Test
    void convertCallsAllStages() {
        Map<String, Object> nodeData = startNodeData();
        nodeData.put("next", "node_2");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.convert();

        assertEquals("node_1", converter.getNode().getId());
        assertTrue(converter.specificConfigCalled);
        assertEquals(1, converter.getEdges().size());
    }

    @Test
    void convertCommonConfigSetsId() {
        ConcreteConverter converter = new ConcreteConverter(startNodeData(), Map.of());

        converter.convertCommonConfig();

        assertEquals("node_1", converter.getNode().getId());
    }

    @Test
    void convertCommonConfigSetsMeta() {
        Position position = new Position(100, 200);
        ConcreteConverter converter = new ConcreteConverter(startNodeData(), Map.of(), position);

        converter.convertCommonConfig();

        Map<?, ?> positionMeta = (Map<?, ?>) converter.getNode().getMeta().get("position");
        assertEquals(100.0d, positionMeta.get("x"));
        assertEquals(200.0d, positionMeta.get("y"));
    }

    @Test
    void convertCommonConfigSetsTitle() {
        Map<String, Object> nodeData = startNodeData();
        nodeData.put("description", "Test Node Description");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.convertCommonConfig();

        assertEquals("Test Node Description", converter.getNode().getData().getTitle());
    }

    @Test
    void convertEdgesWithNext() {
        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("id", "node_1");
        nodeData.put("type", "Start");
        nodeData.put("next", "node_2");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.convertEdges();

        assertEquals(1, converter.getEdges().size());
        assertEquals("node_1", converter.getEdges().get(0).getSourceNodeId());
        assertEquals("node_2", converter.getEdges().get(0).getTargetNodeId());
    }

    @Test
    void convertEdgesWithoutNext() {
        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("id", "node_1");
        nodeData.put("type", "End");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.convertEdges();

        assertTrue(converter.getEdges().isEmpty());
    }

    @Test
    void convertEdgesWithEmptyNext() {
        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("id", "node_1");
        nodeData.put("type", "Start");
        nodeData.put("next", "");
        ConcreteConverter converter = new ConcreteConverter(nodeData, Map.of());

        converter.convertEdges();

        assertTrue(converter.getEdges().isEmpty());
    }

    private static Map<String, Object> startNodeData() {
        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("id", "node_1");
        nodeData.put("type", "Start");
        nodeData.put("description", "Test Node");
        return nodeData;
    }

    private static final class ConcreteConverter extends BaseConverter {
        private boolean specificConfigCalled;

        private ConcreteConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict) {
            super(nodeData, nodesDict);
        }

        private ConcreteConverter(
                Map<String, Object> nodeData,
                Map<String, Object> nodesDict,
                Map<String, Object> resource
        ) {
            super(nodeData, nodesDict, resource);
        }

        private ConcreteConverter(Map<String, Object> nodeData, Map<String, Object> nodesDict, Position position) {
            super(nodeData, nodesDict, position);
        }

        @Override
        protected void convertSpecificConfig() {
            specificConfigCalled = true;
        }
    }
}
