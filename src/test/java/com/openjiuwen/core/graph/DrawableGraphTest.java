/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.visualization.DrawableEdge;
import com.openjiuwen.core.graph.visualization.DrawableGraph;
import com.openjiuwen.core.graph.visualization.DrawableNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DrawableGraphTest {

    @Test
    void drawableGraphKeepsPythonDataclassFields() {
        DrawableNode start = new DrawableNode("start");
        DrawableNode end = new DrawableNode("end");
        DrawableEdge edge = new DrawableEdge("start", "end");
        DrawableGraph graph = new DrawableGraph(
                Map.of("start", start, "end", end),
                List.of(edge),
                List.of(start),
                List.of(end),
                null
        );

        assertThat(graph.getNodes()).containsEntry("start", start).containsEntry("end", end);
        assertThat(graph.getEdges()).containsExactly(edge);
        assertThat(graph.getStartNodes()).containsExactly(start);
        assertThat(graph.getEndNodes()).containsExactly(end);
        assertThat(graph.getBreakNodes()).isNull();

        graph.setBreakNodes(List.of(end));
        assertThat(graph.getBreakNodes()).containsExactly(end);
    }
}
