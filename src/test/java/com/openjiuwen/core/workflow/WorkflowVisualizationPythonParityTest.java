/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.visualization.Drawable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.component.AdvancedLoopComponent;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.LoopComponent;
import com.openjiuwen.core.workflow.component.SubWorkflowComponent;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionComponentImpl;
import com.openjiuwen.core.workflow.component.loop.callback.LoopCallback;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code test_visualize_workflow} in
 * {@code tests/unit_tests/core/component/test_visualize_workflow.py}.
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class WorkflowVisualizationPythonParityTest {

    @BeforeEach
    void enableDrawable() {
        System.setProperty("WORKFLOW_DRAWABLE", "true");
    }

    @Test
    void visualizeSimpleWorkflow() {
        Workflow flow = simpleWorkflow();

        String mermaid = flow.draw("jiuwen workflow");

        assertThat(mermaid)
                .contains("title: jiuwen workflow")
                .contains("flowchart TB")
                .contains("(\"start\")")
                .contains("[\"a\"]")
                .contains("(\"end\")")
                .contains("node_1 --> node_2")
                .contains("node_2 --> node_3");
    }

    @Test
    void renderBinaryDiagramFromConfiguredServer() throws IOException {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x01};
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                .getBytes(StandardCharsets.UTF_8);
        AtomicInteger pngAttempts = new AtomicInteger();
        AtomicInteger svgAttempts = new AtomicInteger();
        AtomicReference<String> pngQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/img/", exchange -> {
            pngQuery.set(exchange.getRequestURI().getRawQuery());
            if (pngAttempts.incrementAndGet() < 3) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.createContext("/svg/", exchange -> {
            svgAttempts.incrementAndGet();
            exchange.sendResponseHeaders(200, svg.length);
            exchange.getResponseBody().write(svg);
            exchange.close();
        });
        server.start();

        String previousServer = System.getProperty("MERMAID_INK_SERVER");
        try {
            System.setProperty(
                    "MERMAID_INK_SERVER",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/"
            );
            Workflow flow = simpleWorkflow();

            assertThat(flow.drawBytes("configured", "png", false)).containsExactly(png);
            assertThat(flow.drawBytes("configured", "svg", false)).containsExactly(svg);
            assertThat(svgAttempts).hasValue(2);
            assertThat(pngAttempts).hasValue(4);
            assertThat(pngQuery).hasValue("format=png");
        } finally {
            restoreSystemProperty("MERMAID_INK_SERVER", previousServer);
            server.stop(0);
        }
    }

    @Test
    void renderFailureRaisesDrawableErrorAfterRetries() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        byte[] svg = "<svg></svg>".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/svg/", exchange -> {
            exchange.sendResponseHeaders(200, svg.length);
            exchange.getResponseBody().write(svg);
            exchange.close();
        });
        server.createContext("/img/", exchange -> {
            attempts.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        String previousServer = System.getProperty("MERMAID_INK_SERVER");
        try {
            System.setProperty(
                    "MERMAID_INK_SERVER",
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );
            Workflow flow = simpleWorkflow();

            assertBaseError(
                    StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.drawBytes("unavailable", "png", false)
            );
            assertThat(attempts).hasValue(3);
        } finally {
            restoreSystemProperty("MERMAID_INK_SERVER", previousServer);
            server.stop(0);
        }
    }

    @Test
    void visualizeSimpleStreamWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new TestComponent(), Map.of("a", "${a}"));
        flow.addWorkflowComp(
                "a",
                new TestComponent(),
                Map.of("value", "${start.a}"),
                null,
                true,
                List.of(ComponentAbility.STREAM));
        flow.addWorkflowComp(
                "b",
                new TestComponent(),
                Map.of("value", "${a.value}"),
                null,
                true,
                List.of(ComponentAbility.COLLECT));
        flow.setEndComp("end", new TestComponent(), Map.of("result1", "${b.value}"));
        flow.addConnection("start", "a");
        flow.addStreamConnection("a", "b");
        flow.addConnection("b", "end");

        String mermaid = flow.draw("jiuwen workflow");

        assertThat(mermaid)
                .contains("(\"start\")")
                .contains("[\"a\"]")
                .contains("[\"b\"]")
                .contains("(\"end\")")
                .contains("node_2 ==> node_3")
                .contains("node_3 --> node_4");
    }

    @Test
    void visualizeWorkflowWithBranchComponent() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new TestComponent(), null);
        flow.setEndComp("end", new TestComponent(), Map.of("a", "${a.result}", "b", "${b.result}"));
        BranchComponent branch = new BranchComponent();
        branch.addBranch("${a} <= 10", List.of("b"), "1");
        branch.addBranch("${a} > 10", List.of("a"), "2");
        flow.addWorkflowComp("sw", branch);
        flow.addWorkflowComp("a", new TestComponent(), Map.of("result", "${a}"));
        flow.addWorkflowComp("b", new TestComponent(), Map.of("source", "${a}"));
        flow.addConnection("start", "sw");
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        String mermaid = flow.draw();

        assertThat(mermaid)
                .contains("[\"sw\"]")
                .contains("-.->|\"${a} <= 10\"|")
                .contains("-.->|\"${a} > 10\"|")
                .contains("-->");
    }

    @Test
    void visualizeWorkflowWithBranchRouter() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new TestComponent(), Map.of("a", "${a}", "b", "${b}"));
        BranchRouter router = new BranchRouter();
        router.addBranch("${start.a} is not None", "a");
        router.addBranch("${start.b} is not None", "b");
        flow.addConditionalConnection("start", router);
        flow.addWorkflowComp("a", new TestComponent(), Map.of("a", "${start.a}"));
        flow.addWorkflowComp("b", new TestComponent(), Map.of("b", "${start.b}"));
        flow.setEndComp("end", new TestComponent(), Map.of("result1", "${a.a}", "result2", "${b.b}"));
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        String mermaid = flow.draw("jiuwen workflow");

        assertThat(mermaid)
                .contains("-.->|\"${start.a} is not None\"|")
                .contains("-.->|\"${start.b} is not None\"|")
                .contains("[\"a\"]")
                .contains("[\"b\"]");
    }

    @Test
    void visualizeWorkflowWithConditionTargets() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new TestComponent(), Map.of("a", "${a}", "b", "${b}"));
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> true, "a");
        router.addBranch(() -> true, "b");
        flow.addConditionalConnection("start", router);
        flow.addWorkflowComp("a", new TestComponent(), Map.of("a", "${start.a}"));
        flow.addWorkflowComp("b", new TestComponent(), Map.of("b", "${start.b}"));
        flow.setEndComp("end", new TestComponent(), Map.of("result1", "${a.a}", "result2", "${b.b}"));
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        String mermaid = flow.draw("jiuwen workflow");

        assertThat(mermaid)
                .contains("node_1 -.->|\"\"| node_2")
                .contains("node_1 -.->|\"\"| node_3")
                .contains("node_2 --> node_4")
                .contains("node_3 --> node_4");
    }

    @Test
    void visualizeSubWorkflow() {
        Workflow subFlow = simpleSubWorkflow("sub_start", "sub_a", "sub_end");
        Workflow flow = simpleWorkflow();
        flow.addWorkflowComp("sub_flow", new TestSubWorkflowComponent(subFlow));
        flow.addConnection("a", "sub_flow");
        flow.addConnection("sub_flow", "end");

        String collapsed = flow.draw("jiuwen workflow");
        String expanded = flow.draw("jiuwen workflow", "mermaid", true);

        assertThat(collapsed).contains("[\"sub_flow\"]");
        assertThat(expanded)
                .contains("subgraph")
                .contains("[\"sub_flow\"]")
                .contains("(\"sub_start\")")
                .contains("[\"sub_a\"]")
                .contains("(\"sub_end\")");
    }

    @Test
    void visualizeMultiLayerSubWorkflow() {
        Workflow subSubFlow = simpleSubWorkflow("sub_sub_start", "sub_sub_a", "sub_sub_end");
        Workflow subFlow = simpleSubWorkflow("sub_start", "sub_a", "sub_end");
        subFlow.addWorkflowComp("sub_sub_flow", new TestSubWorkflowComponent(subSubFlow));
        subFlow.addConnection("sub_a", "sub_sub_flow");
        subFlow.addConnection("sub_sub_flow", "sub_end");
        Workflow flow = simpleWorkflow();
        flow.addWorkflowComp("sub_flow", new TestSubWorkflowComponent(subFlow));
        flow.addConnection("a", "sub_flow");
        flow.addConnection("sub_flow", "end");

        String expanded = flow.draw("jiuwen workflow", "mermaid", true);

        assertThat(expanded)
                .contains("[\"sub_flow\"]")
                .contains("[\"sub_sub_flow\"]")
                .contains("(\"sub_sub_start\")")
                .contains("[\"sub_sub_a\"]")
                .contains("(\"sub_sub_end\")");
    }

    @Test
    void visualizeWorkflowWithAdvancedLoop() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new TestComponent(), null);
        flow.addWorkflowComp("a", new TestComponent());
        flow.addWorkflowComp("l", new TestAdvancedLoopComponent(loopGroup("1", "2", "3")));
        flow.addWorkflowComp("b", new TestComponent());
        flow.setEndComp("e", new TestComponent(), null);
        flow.addConnection("s", "a");
        flow.addConnection("a", "l");
        flow.addConnection("l", "b");
        flow.addConnection("b", "e");

        String collapsed = flow.draw("jiuwen workflow");
        String expanded = flow.draw("jiuwen workflow", "mermaid", true);

        assertThat(collapsed).contains("[\"l\"]").contains("-.->");
        assertThat(expanded)
                .contains("subgraph")
                .contains("[\"l\"]")
                .contains("(\"1\")")
                .contains("[\"2\"]")
                .contains("(\"3\")");
    }

    @Test
    void visualizeWorkflowWithLoop() {
        Workflow flow = loopWorkflow(loopGroup("1", "2", "3", "4"));

        String collapsed = flow.draw("jiuwen workflow");
        String expanded = flow.draw("jiuwen workflow", "mermaid", true);

        assertThat(collapsed).contains("[\"l\"]").contains("-.->");
        assertThat(expanded)
                .contains("subgraph")
                .contains("(\"1\")")
                .contains("[\"2\"]")
                .contains("[\"3\"]")
                .contains("(\"4\")");
    }

    @Test
    void visualizeWorkflowWithLoopUnsetEndNodes() {
        LoopGroup group = loopGroup("1", "2", "3", "4");
        group.getDrawable().getGraph().getEndNodes().clear();
        Workflow flow = loopWorkflow(group);

        String expanded = flow.draw("jiuwen workflow", "mermaid", true);

        assertThat(expanded)
                .contains("subgraph")
                .contains("(\"4\")")
                .contains("-.->");
    }

    @Test
    void drawableException() {
        Drawable drawable = new Drawable();

        assertBaseError(StatusCode.DRAWABLE_GRAPH_START_NODE_INVALID, () -> drawable.setStartNode("start"));
        assertBaseError(StatusCode.DRAWABLE_GRAPH_END_NODE_INVALID, () -> drawable.setEndNode("end"));
        assertBaseError(StatusCode.DRAWABLE_GRAPH_BREAK_NODE_INVALID, () -> drawable.setBreakNode("break"));
        assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID, () -> drawable.toMermaid(null, 0, false));
        assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID, () -> drawable.toMermaid("", -1, false));
        assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID, () -> simpleWorkflow().draw("title", "mermaid", true, 1));
    }

    @Test
    void visualizeSimpleStreamWorkflowAnimation() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new TestComponent(), Map.of("a", "${a}"));
        flow.addWorkflowComp("a", new TestComponent(), Map.of("value", "${start.a}"), null, true,
                List.of(ComponentAbility.STREAM));
        flow.addWorkflowComp("b", new TestComponent(), Map.of("value", "${a.value}"), null, true,
                List.of(ComponentAbility.COLLECT));
        flow.setEndComp("end", new TestComponent(), Map.of("result1", "${b.value}"));
        flow.addConnection("start", "a");
        flow.addStreamConnection("a", "b");
        flow.addConnection("b", "end");

        String mermaid = flow.draw("jiuwen workflow", "mermaid", false, true);

        assertThat(mermaid)
                .contains("link_1@==>")
                .contains("link_1@{animate: true}");
    }

    @Test
    void visualizeSimpleWorkflowIntent() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new TestComponent(), Map.of("a", "${a}"));
        IntentDetectionComponentImpl intent = new IntentDetectionComponentImpl();
        intent.addBranch("${intent.classification_id} == 1", List.of("llm"), "weather branch");
        intent.addBranch("${intent.classification_id} == 0", List.of("end"), "default branch");
        flow.addWorkflowComp("intent", intent, Map.of("query", "${start.query}"));
        flow.addWorkflowComp("llm", new TestComponent(), Map.of("query", "${start.query}"));
        flow.addWorkflowComp("plugin", new TestComponent(), Map.of("location", "${llm.location}", "date", "${llm.date}"));
        flow.setEndComp("end", new TestComponent(), Map.of("output", "${plugin.data}"));
        flow.addConnection("start", "intent");
        flow.addConnection("llm", "plugin");
        flow.addConnection("plugin", "end");

        String mermaid = flow.draw("jiuwen workflow");

        assertThat(mermaid)
                .contains("[\"intent\"]")
                .contains("[\"llm\"]")
                .contains("[\"plugin\"]")
                .contains("-.->|\"${intent.classification_id} == 1\"|")
                .contains("-.->|\"${intent.classification_id} == 0\"|")
                .contains("node_3 --> node_4")
                .contains("node_4 --> node_5");
    }

    private static Workflow simpleWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new TestComponent(), Map.of("a", "${a}", "b", "${b}", "c", 1));
        flow.addWorkflowComp("a", new TestComponent(), Map.of("aa", "${start.a}", "ac", "${start.c}"));
        flow.setEndComp("end", new TestComponent(), Map.of("result", "${a.aa}"));
        flow.addConnection("start", "a");
        flow.addConnection("a", "end");
        return flow;
    }

    private static Workflow simpleSubWorkflow(String start, String middle, String end) {
        Workflow flow = new Workflow();
        flow.setStartComp(start, new TestComponent(), Map.of("a", "${a}"));
        flow.addWorkflowComp(middle, new TestComponent(), Map.of("aa", "${" + start + ".a}"));
        flow.setEndComp(end, new TestComponent(), Map.of("result", "${" + middle + ".aa}"));
        flow.addConnection(start, middle);
        flow.addConnection(middle, end);
        return flow;
    }

    private static Workflow loopWorkflow(LoopGroup group) {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new TestComponent(), Map.of("a", "${input_number}"));
        flow.addWorkflowComp("a", new TestComponent(), Map.of("array", "${input_array}"));
        flow.addWorkflowComp("l", new TestLoopComponent(group));
        flow.addWorkflowComp("b", new TestComponent(), Map.of("array_result", "${l.results}"));
        flow.setEndComp("e", new TestComponent(), Map.of("array_result", "${b.array_result}"));
        flow.addConnection("s", "a");
        flow.addConnection("a", "l");
        flow.addConnection("l", "b");
        flow.addConnection("b", "e");
        return flow;
    }

    private static LoopGroup loopGroup(String... nodes) {
        LoopGroup group = new LoopGroup();
        for (int index = 0; index < nodes.length; index++) {
            String node = nodes[index];
            group.getDrawable().addNode(node, new TestComponent());
            group.addWorkflowComp(node, new TestComponent());
            if (index == 0) {
                group.getDrawable().setStartNode(node);
                group.startComp(node);
            }
            if (index == nodes.length - 1) {
                group.getDrawable().setEndNode(node);
                group.endComp(node);
            }
            if (index > 0) {
                group.getDrawable().addEdge(nodes[index - 1], node);
            }
        }
        return group;
    }

    private static void assertBaseError(StatusCode expected, Runnable action) {
        BaseError error = assertThrows(BaseError.class, action::run);
        assertEquals(expected.getCode(), error.getCode());
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static class TestComponent extends Executable<Object, Object> implements ComponentComposable {

        @Override
        public Object onInvoke(Object inputs, BaseSession session, Object... kwargs) {
            return Map.of();
        }
    }

    private static final class TestSubWorkflowComponent extends TestComponent implements SubWorkflowComponent {
        private final Workflow subWorkflow;

        private TestSubWorkflowComponent(Workflow subWorkflow) {
            this.subWorkflow = subWorkflow;
        }

        @Override
        public Workflow getSubWorkflow() {
            return subWorkflow;
        }

        @Override
        public HasDrawable getSubWorkflowInternal() {
            return subWorkflow.getInternalDrawable();
        }

        @Override
        public boolean isCacheStream() {
            return false;
        }
    }

    private static final class TestLoopComponent extends TestComponent implements LoopComponent {
        private final HasDrawable loopGroup;

        private TestLoopComponent(HasDrawable loopGroup) {
            this.loopGroup = loopGroup;
        }

        @Override
        public HasDrawable getLoopGroup() {
            return loopGroup;
        }
    }

    private static final class TestAdvancedLoopComponent extends TestComponent implements AdvancedLoopComponent {
        private final HasDrawable body;

        private TestAdvancedLoopComponent(HasDrawable body) {
            this.body = body;
        }

        @Override
        public HasDrawable getBody() {
            return body;
        }

        @Override
        public void registerCallback(LoopCallback callback) {
        }
    }
}
