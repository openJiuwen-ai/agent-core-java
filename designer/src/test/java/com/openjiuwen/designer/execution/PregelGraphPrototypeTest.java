/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that the core Pregel graph can schedule a designer-shaped DAG.
 *
 * <p>Raw {@link PregelGraph} nodes do not receive upstream payloads in their
 * {@code inputs} map unless an input schema is configured. Designer execution
 * therefore uses the graph for ordering and a per-run context for node results.</p>
 *
 * @since 2026-11-06
 */
class PregelGraphPrototypeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PregelGraphPrototypeTest.class);

    private final Map<String, Object> outputsByNode = new ConcurrentHashMap<>();
    private final Map<String, Object> upstreamSeenByNode = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> inputsByNode = new ConcurrentHashMap<>();

    @Test
    void runsLinearDesignerGraphAndSharesRunContext() {
        PregelGraph graph = new PregelGraph();
        graph.addNode("n_brief", node("n_brief", Map.of("text", "brief")));
        graph.addNode("n_character_1", node("n_character_1", Map.of("text", "character")));
        graph.addNode("n_clip_1", node("n_clip_1", Map.of("text", "clip")));
        graph.addEdge("n_brief", "n_character_1");
        graph.addEdge("n_character_1", "n_clip_1");
        graph.startNode("n_brief");
        graph.endNode("n_clip_1");

        BaseSession session = new WorkflowRuntimeSession(
                "designer-prototype", null, "designer-prototype", WorkflowRuntimeState.create(), null);
        ExecutableGraph<?, ?> compiled = graph.compile(session, Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) compiled.invoke(envelope(), session);

        assertThat(result).isNotNull();
        assertThat(inputsByNode).containsKeys("n_brief", "n_character_1", "n_clip_1");
        assertThat(upstreamSeenByNode.get("n_character_1"))
                .as("character saw brief: %s", upstreamSeenByNode.get("n_character_1"))
                .isEqualTo(Map.of("text", "brief"));
        assertThat(upstreamSeenByNode.get("n_clip_1"))
                .as("clip saw character: %s", upstreamSeenByNode.get("n_clip_1"))
                .isEqualTo(Map.of("text", "character"));
        LOGGER.info("prototype result keys: {}, context: {}", result.keySet(), outputsByNode);
    }

    private Map<String, Object> envelope() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(Constant.INPUTS_KEY, Map.of());
        envelope.put(Constant.CONFIG_KEY, Map.of());
        return envelope;
    }

    private Executable<Map<String, Object>, Object> node(String nodeId, Map<String, Object> output) {
        return new Executable<Map<String, Object>, Object>() {
            @Override
            public Object onInvoke(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
                inputsByNode.put(nodeId, inputs == null ? Map.of() : inputs);
                if ("n_character_1".equals(nodeId)) {
                    upstreamSeenByNode.put(nodeId, outputsByNode.get("n_brief"));
                }
                if ("n_clip_1".equals(nodeId)) {
                    upstreamSeenByNode.put(nodeId, outputsByNode.get("n_character_1"));
                }
                outputsByNode.put(nodeId, output);
                return output;
            }
        };
    }
}
