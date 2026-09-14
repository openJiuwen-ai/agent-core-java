package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.visualization.DrawableBranchRouter;
import com.openjiuwen.core.graph.visualization.DrawableEdge;
import com.openjiuwen.core.graph.visualization.DrawableNode;
import com.openjiuwen.core.graph.visualization.Stringifiable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphVisualizationTypesTest {

    @Test
    void graphStateKeepsPythonFieldNameAndAddSemantics() {
        GraphState state = new GraphState();
        state.setSourceNodeId(List.of("node-a"));
        state.mergeSourceNodeId(List.of("node-b", "node-c"));

        assertThat(state.getSourceNodeId()).containsExactly("node-a", "node-b", "node-c");
    }

    @Test
    void drawableEdgeBranchRouterAndNodeMatchPythonDefaults() {
        Stringifiable payload = new Stringifiable() {
            @Override
            public String toString() {
                return "payload";
            }
        };
        DrawableEdge edge = new DrawableEdge("from", "to", payload, true, false);
        DrawableBranchRouter router = new DrawableBranchRouter(List.of("left", "right"), List.of("A", "B"));
        DrawableNode node = new DrawableNode("id-1", null, Map.of("rank", 1));

        assertThat(edge.getSource()).isEqualTo("from");
        assertThat(edge.getTarget()).isEqualTo("to");
        assertThat(edge.getData().toString()).isEqualTo("payload");
        assertThat(edge.isConditional()).isTrue();
        assertThat(edge.isStreaming()).isFalse();
        assertThat(router.getTargets()).containsExactly("left", "right");
        assertThat(router.getDatas()).containsExactly("A", "B");
        assertThat(node.getId()).isEqualTo("id-1");
        assertThat(node.getName()).isNull();
        assertThat(node.getMetadata()).containsEntry("rank", 1);
    }
}
