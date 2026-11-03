/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.component.LoopComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawableTest {

    @Test
    void constructorCreatesPythonDefaultGraphState() {
        Drawable drawable = new Drawable();
        DrawableGraph graph = drawable.getGraph();

        assertTrue(graph.getNodes().isEmpty());
        assertTrue(graph.getEdges().isEmpty());
        assertTrue(graph.getStartNodes().isEmpty());
        assertTrue(graph.getEndNodes().isEmpty());
        assertTrue(graph.getBreakNodes().isEmpty());
    }

    @Test
    void addNodeAndStartEndBreakMutateExistingNodeLists() {
        Drawable drawable = new Drawable();

        drawable.addNode("node-a", new PlainComponent());
        drawable.setStartNode("node-a");
        drawable.setEndNode("node-a");
        drawable.setBreakNode("node-a");

        DrawableNode node = drawable.getGraph().getNodes().get("node-a");
        assertSame(node, drawable.getGraph().getStartNodes().get(0));
        assertSame(node, drawable.getGraph().getEndNodes().get(0));
        assertSame(node, drawable.getGraph().getBreakNodes().get(0));
        assertThrows(RuntimeException.class, () -> drawable.setStartNode("missing"));
        assertThrows(RuntimeException.class, () -> drawable.setEndNode("missing"));
        assertThrows(RuntimeException.class, () -> drawable.setBreakNode("missing"));
    }

    @Test
    void addEdgePreservesNormalConditionalAndTargetProviderSemantics() {
        Drawable drawable = new Drawable();
        drawable.addNode("start", new PlainComponent());
        drawable.addNode("left", new PlainComponent());
        drawable.addNode("right", new PlainComponent());

        drawable.addEdge("start", "left");
        drawable.addEdge("start", null, true, false, (Drawable.TargetProvider) () -> List.of("left", "right"));

        List<DrawableEdge> edges = drawable.getGraph().getEdges();
        assertEquals(3, edges.size());
        assertEquals("start", edges.get(0).getSource());
        assertEquals("left", edges.get(0).getTarget());
        assertFalse(edges.get(0).isConditional());
        assertEquals("left", edges.get(1).getTarget());
        assertEquals("right", edges.get(2).getTarget());
        assertTrue(edges.get(1).isConditional());
        assertTrue(edges.get(2).isConditional());
    }

    @Test
    void branchRouterEdgesUseRouterTargetsAndDatas() {
        Drawable drawable = new Drawable();
        TestBranchRouter router = new TestBranchRouter(
                new DrawableBranchRouter(List.of("alpha", "beta"), List.of("A", "B"))
        );

        drawable.addEdge("branch", null, true, true, router);

        List<DrawableEdge> edges = drawable.getGraph().getEdges();
        assertEquals(2, edges.size());
        assertEquals("alpha", edges.get(0).getTarget());
        assertEquals("A", edges.get(0).getData().toString());
        assertTrue(edges.get(0).isConditional());
        assertTrue(edges.get(0).isStreaming());
        assertEquals("beta", edges.get(1).getTarget());
        assertEquals("B", edges.get(1).getData().toString());
    }

    @Test
    void loopComponentBecomesSubgraphAndGetsConditionalSelfEdge() {
        Drawable innerDrawable = new Drawable();
        innerDrawable.addNode("loop-start", new PlainComponent());
        innerDrawable.addNode("loop-end", new PlainComponent());
        innerDrawable.addEdge("loop-start", "loop-end");
        TestLoopComponent loopComponent = new TestLoopComponent(() -> innerDrawable);

        Drawable drawable = new Drawable();
        drawable.addNode("loop", loopComponent);

        DrawableNode node = drawable.getGraph().getNodes().get("loop");
        assertTrue(node instanceof DrawableSubgraphNode);
        assertEquals(1, innerDrawable.getGraph().getEndNodes().size());
        assertEquals("loop-end", innerDrawable.getGraph().getEndNodes().get(0).getId());
        assertEquals(1, drawable.getGraph().getEdges().size());
        assertEquals("loop", drawable.getGraph().getEdges().get(0).getSource());
        assertEquals("loop", drawable.getGraph().getEdges().get(0).getTarget());
        assertTrue(drawable.getGraph().getEdges().get(0).isConditional());
    }

    @Test
    void toMermaidRendersGraphTextAndValidatesArguments() {
        Drawable drawable = new Drawable();
        drawable.addNode("start", new PlainComponent());
        drawable.addNode("end", new PlainComponent());
        drawable.setStartNode("start");
        drawable.setEndNode("end");
        drawable.addEdge("start", "end", false, true);

        String mermaid = drawable.toMermaid("Example", 0, true);

        assertTrue(mermaid.contains("title: Example"));
        assertTrue(mermaid.contains("flowchart TB"));
        assertTrue(mermaid.contains("\"start\""));
        assertTrue(mermaid.contains("\"end\""));
        assertTrue(mermaid.contains("==>"));
        assertTrue(mermaid.contains("animate: true"));
        assertThrows(RuntimeException.class, () -> drawable.toMermaid(null, 0, false));
        assertThrows(RuntimeException.class, () -> drawable.toMermaid("", -1, false));
    }

    private static final class PlainComponent implements ComponentComposable {
    }

    private static final class TestLoopComponent implements LoopComponent {

        private final HasDrawable loopGroup;

        private TestLoopComponent(HasDrawable loopGroup) {
            this.loopGroup = loopGroup;
        }

        @Override
        public HasDrawable getLoopGroup() {
            return loopGroup;
        }
    }

    private static final class TestBranchRouter extends com.openjiuwen.core.workflow.BranchRouter {

        private final DrawableBranchRouter drawableBranchRouter;

        private TestBranchRouter(DrawableBranchRouter drawableBranchRouter) {
            this.drawableBranchRouter = drawableBranchRouter;
        }

        @Override
        public DrawableBranchRouter getDrawableBranchRouter() {
            return drawableBranchRouter;
        }
    }
}
