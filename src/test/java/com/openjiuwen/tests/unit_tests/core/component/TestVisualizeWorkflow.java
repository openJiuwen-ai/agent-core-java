/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApiCard;
import com.openjiuwen.core.graph.visualization.Drawable;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig;
import com.openjiuwen.core.workflow.component.llm.LLMCompConfig;
import com.openjiuwen.core.workflow.component.loop.AdvancedLoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;
import com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent;
import com.openjiuwen.core.workflow.component.loop.callback.IntermediateLoopVarCallback;
import com.openjiuwen.core.workflow.component.loop.callback.OutputCallback;
import com.openjiuwen.core.workflow.component.tool.ToolComponent;
import com.openjiuwen.core.workflow.component.tool.ToolComponentConfig;
import com.openjiuwen.core.workflow.components.llm.IntentDetectionComponent;
import com.openjiuwen.core.workflow.components.llm.LLMComponent;
import com.openjiuwen.core.workflow.condition.NumberCondition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests/unit_tests/core/component/test_visualize_workflow.py}.
 */
class TestVisualizeWorkflow {

    @Test
    @DisplayName("simple workflow visualization")
    void testVisualizeSimpleWorkflow() {
        String actual = withDrawable(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockStartNode("start"),
                    Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)));
            flow.addWorkflowComp("a", new Node1("a"),
                    Map.of("aa", "${start.a}", "ac", "${start.c}"));
            flow.setEndComp("end", new MockEndNode("end"), Map.of("result", "${a.aa}"));
            flow.addConnection("start", "a");
            flow.addConnection("a", "end");
            return flow.draw("jiuwen workflow");
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_3(\"end\")",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3"), actual);
    }

    @Test
    @DisplayName("stream workflow visualization")
    void testVisualizeSimpleStreamWorkflow() {
        String actual = withDrawable(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockStartNode("start"), Map.of("a", "${a}"));
            flow.addWorkflowComp("a", new StreamCompNode("a"),
                    Map.of("value", "${start.a}"), true, List.of(ComponentAbility.STREAM));
            flow.addWorkflowComp("b", new CollectCompNode("b"),
                    Map.of("value", "${a.value}"), null, true,
                    Map.of("value1", "${a.value}"), null);
            flow.setEndComp("end", new MockEndNode("end"), Map.of("result1", "${b.value}"));
            flow.addConnection("start", "a");
            flow.addStreamConnection("a", "b");
            flow.addConnection("b", "end");
            return flow.draw("jiuwen workflow");
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"b\"]",
                "\tnode_4(\"end\")",
                "\tnode_1 --> node_2",
                "\tnode_2 ==> node_3",
                "\tnode_3 --> node_4"), actual);
    }

    @Test
    @DisplayName("branch component visualization")
    void testVisualizeWorkflowWithBranchComp() {
        String actual = withDrawable(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockStartNode("start"), null);
            flow.setEndComp("end", new MockEndNode("end"),
                    Map.of("a", "${a.result}", "b", "${b.result}"));

            BranchComponent branch = new BranchComponent();
            branch.addBranch("${a} <= 10", List.of("b"), "1");
            branch.addBranch("${a} > 10", List.of("a"), "2");

            flow.addWorkflowComp("sw", branch);
            flow.addWorkflowComp("a", new CommonNode("a"), Map.of("result", "${a}"));
            flow.addWorkflowComp("b", new AddTenNode("b"), Map.of("source", "${a}"));
            flow.addConnection("start", "sw");
            flow.addConnection("a", "end");
            flow.addConnection("b", "end");
            return flow.draw();
        });

        assertEquals(mermaid(
                "---",
                "title: ",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2(\"end\")",
                "\tnode_3[\"sw\"]",
                "\tnode_4[\"a\"]",
                "\tnode_5[\"b\"]",
                "\tnode_3 -.->|\"${a} <= 10\"| node_5",
                "\tnode_3 -.->|\"${a} > 10\"| node_4",
                "\tnode_1 --> node_3",
                "\tnode_4 --> node_2",
                "\tnode_5 --> node_2"), actual);
    }

    @Test
    @DisplayName("branch router visualization")
    void testVisualizeWorkflowWithBranchRouter() {
        String actual = withDrawable(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockStartNode("start"),
                    Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)));
            BranchRouter router = new BranchRouter();
            router.addBranch("${start.a} is not None", "a", null);
            router.addBranch("${start.b} is not None", "b", null);
            flow.addConditionalConnection("start", router);
            flow.addWorkflowComp("a", new Node1("a"), Map.of("a", "${start.a}", "b", "${start.c}"));
            flow.addWorkflowComp("b", new Node1("b"), Map.of("b", "${start.b}"));
            flow.setEndComp("end", new MockEndNode("end"),
                    Map.of("result1", "${a.a}", "result2", "${b.b}"));
            flow.addConnection("a", "end");
            flow.addConnection("b", "end");
            return flow.draw("jiuwen workflow");
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"b\"]",
                "\tnode_4(\"end\")",
                "\tnode_1 -.->|\"${start.a} is not None\"| node_2",
                "\tnode_1 -.->|\"${start.b} is not None\"| node_3",
                "\tnode_2 --> node_4",
                "\tnode_3 --> node_4"), actual);
    }

    @Test
    @DisplayName("callable condition visualization")
    void testVisualizeWorkflowWithCondition() {
        String actual = withDrawable(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockStartNode("start"),
                    Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)));
            flow.addConditionalConnection("start", new LiteralRouter());
            flow.addWorkflowComp("a", new Node1("a"), Map.of("a", "${start.a}", "b", "${start.c}"));
            flow.addWorkflowComp("b", new Node1("b"), Map.of("b", "${start.b}"));
            flow.setEndComp("end", new MockEndNode("end"),
                    Map.of("result1", "${a.a}", "result2", "${b.b}"));
            flow.addConnection("a", "end");
            flow.addConnection("b", "end");
            return flow.draw("jiuwen workflow");
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"b\"]",
                "\tnode_4(\"end\")",
                "\tnode_1 -.-> node_2",
                "\tnode_1 -.-> node_3",
                "\tnode_2 --> node_4",
                "\tnode_3 --> node_4"), actual);
    }

    @Test
    @DisplayName("sub workflow visualization")
    void testVisualizeSubWorkflow() {
        List<String> actual = withDrawable(() -> {
            Workflow subFlow = buildSubWorkflow("sub_start", "sub_a", "sub_end");
            Workflow flow = buildOuterSubWorkflow(subFlow);
            return List.of(flow.draw("jiuwen workflow"),
                    flow.draw("jiuwen workflow", "mermaid", true));
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"sub_flow\"]",
                "\tnode_4(\"end\")",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_3 --> node_4"), actual.get(0));

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_7(\"end\")",
                "\tsubgraph node_6 [\"sub_flow\"]",
                "\tdirection TB",
                "\tnode_3(\"sub_start\")",
                "\tnode_4[\"sub_a\"]",
                "\tnode_5(\"sub_end\")",
                "\tend",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_5 --> node_7",
                "\tnode_3 --> node_4",
                "\tnode_4 --> node_5"), actual.get(1));
    }

    @Test
    @DisplayName("multi-layer sub workflow visualization")
    void testVisualizeMultiLayerSubWorkflow() {
        List<String> actual = withDrawable(() -> {
            Workflow subSubFlow = buildSubWorkflow("sub_sub_start", "sub_sub_a", "sub_sub_end");

            Workflow subFlow = new Workflow();
            subFlow.setStartComp("sub_start", new MockStartNode("start"),
                    Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)));
            subFlow.addWorkflowComp("sub_a", new Node1("a"),
                    Map.of("aa", "${start.a}", "ac", "${start.c}"));
            subFlow.addWorkflowComp("sub_sub_flow", new SubWorkflowComponentImpl(subSubFlow));
            subFlow.setEndComp("sub_end", new MockEndNode("end"), Map.of("result", "${a.aa}"));
            subFlow.addConnection("sub_start", "sub_a");
            subFlow.addConnection("sub_a", "sub_sub_flow");
            subFlow.addConnection("sub_sub_flow", "sub_end");

            Workflow flow = buildOuterSubWorkflow(subFlow);
            return List.of(
                    flow.draw("jiuwen workflow"),
                    flow.draw("jiuwen workflow", "mermaid", 1),
                    flow.draw("jiuwen workflow", "mermaid", 2),
                    flow.draw("jiuwen workflow", "mermaid", true));
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"sub_flow\"]",
                "\tnode_4(\"end\")",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_3 --> node_4"), actual.get(0));

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_8(\"end\")",
                "\tsubgraph node_7 [\"sub_flow\"]",
                "\tdirection TB",
                "\tnode_3(\"sub_start\")",
                "\tnode_4[\"sub_a\"]",
                "\tnode_5[\"sub_sub_flow\"]",
                "\tnode_6(\"sub_end\")",
                "\tend",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_6 --> node_8",
                "\tnode_3 --> node_4",
                "\tnode_4 --> node_5",
                "\tnode_5 --> node_6"), actual.get(1));

        String fullyExpanded = mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_11(\"end\")",
                "\tsubgraph node_10 [\"sub_flow\"]",
                "\tdirection TB",
                "\tnode_3(\"sub_start\")",
                "\tnode_4[\"sub_a\"]",
                "\tnode_9(\"sub_end\")",
                "\tsubgraph node_8 [\"sub_sub_flow\"]",
                "\tdirection TB",
                "\tnode_5(\"sub_sub_start\")",
                "\tnode_6[\"sub_sub_a\"]",
                "\tnode_7(\"sub_sub_end\")",
                "\tend",
                "\tend",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_9 --> node_11",
                "\tnode_3 --> node_4",
                "\tnode_4 --> node_5",
                "\tnode_7 --> node_9",
                "\tnode_5 --> node_6",
                "\tnode_6 --> node_7");
        assertEquals(fullyExpanded, actual.get(2));
        assertEquals(fullyExpanded, actual.get(3));
    }

    @Test
    @DisplayName("advanced loop visualization")
    void testVisualizeWorkflowWithAdvancedLoop() {
        List<String> actual = withDrawable(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("s", new MockStartNode("s"), null);
            flow.addWorkflowComp("a", new CommonNode("a"));

            LoopGroup loopGroup = new LoopGroup();
            loopGroup.addWorkflowComp("1", new AddTenNode("1"), Map.of("source", "${l.index}"));
            loopGroup.addWorkflowComp("2", new AddTenNode("2"),
                    Map.of("source", "${l.intermediate_loop_var.user_var}"));
            loopGroup.addWorkflowComp("3",
                    new LoopSetVariableComponent(Map.of("${l.intermediate_loop_var.user_var}", "${2.result}")));
            loopGroup.startNodes(List.of("1"));
            loopGroup.endNodes(List.of("3"));
            loopGroup.addConnection("1", "2");
            loopGroup.addConnection("2", "3");
            AdvancedLoopComponentImpl loop = new AdvancedLoopComponentImpl(
                    loopGroup,
                    new NumberCondition("${loop_number}"),
                    loopGroup.getBreakComponents(),
                    List.of(new OutputCallback(Map.of("results", "${1.result}",
                                    "user_var", "${l.intermediate_loop_var.user_var}")),
                            new IntermediateLoopVarCallback(Map.of("user_var", "${input_number}"),
                                    "intermediate_loop_var")));

            flow.addWorkflowComp("l", loop, Map.of("input_number", "${input_number}"));
            flow.addWorkflowComp("b", new CommonNode("b"),
                    Map.of("array_result", "${l.results}", "user_var", "${l.user_var}"));
            flow.setEndComp("e", new MockEndNode("e"),
                    Map.of("array_result", "${b.array_result}", "user_var", "${b.user_var}"));
            flow.addConnection("s", "a");
            flow.addConnection("a", "l");
            flow.addConnection("l", "b");
            flow.addConnection("b", "e");
            return List.of(flow.draw("jiuwen workflow"),
                    flow.draw("jiuwen workflow", "mermaid", true));
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"s\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"l\"]",
                "\tnode_4[\"b\"]",
                "\tnode_5(\"e\")",
                "\tnode_3 -.-> node_3",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_3 -.-> node_4",
                "\tnode_4 --> node_5"), actual.get(0));

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"s\")",
                "\tnode_2[\"a\"]",
                "\tnode_7[\"b\"]",
                "\tnode_8(\"e\")",
                "\tsubgraph node_6 [\"l\"]",
                "\tdirection TB",
                "\tnode_3(\"1\")",
                "\tnode_4[\"2\"]",
                "\tnode_5(\"3\")",
                "\tend",
                "\tnode_5 -.-> node_3",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_5 -.-> node_7",
                "\tnode_7 --> node_8",
                "\tnode_3 --> node_4",
                "\tnode_4 --> node_5"), actual.get(1));
    }

    @Test
    @DisplayName("array loop visualization")
    void testVisualizeWorkflowWithLoop() {
        List<String> actual = withDrawable(() -> {
            Workflow flow = buildLoopWorkflow(true);
            return List.of(flow.draw("jiuwen workflow"),
                    flow.draw("jiuwen workflow", "mermaid", true));
        });
        assertLoopMermaid(actual);
    }

    @Test
    @DisplayName("array loop visualization with end_nodes alias")
    void testVisualizeWorkflowWithLoopUnsetEndNodes() {
        List<String> actual = withDrawable(() -> {
            Workflow flow = buildLoopWorkflow(false);
            return List.of(flow.draw("jiuwen workflow"),
                    flow.draw("jiuwen workflow", "mermaid", true));
        });
        assertLoopMermaid(actual);
    }

    @Test
    @DisplayName("Drawable raises Python-equivalent validation errors")
    void testDrawableException() {
        Drawable drawable = new Drawable();
        assertBaseError(StatusCode.DRAWABLE_GRAPH_START_NODE_INVALID,
                () -> drawable.setStartNode("start"));
        assertBaseError(StatusCode.DRAWABLE_GRAPH_END_NODE_INVALID,
                () -> drawable.setEndNode("end"));
        assertBaseError(StatusCode.DRAWABLE_GRAPH_BREAK_NODE_INVALID,
                () -> drawable.setBreakNode("break"));

        Workflow flow = withDrawable(Workflow::new);
        List<Object> invalidTitles = List.of(-1, Map.of(), Map.of("a", "b"), List.of(), List.of(1, 2));
        for (Object invalidTitle : invalidTitles) {
            assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.draw(invalidTitle, "mermaid", false));
            assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.drawBytes(invalidTitle, "svg", false));
            assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.drawBytes(invalidTitle, "png", false));
        }

        List<Object> invalidExpandSubgraphs = List.of(-1, "", "true", "xxx", Map.of(), Map.of("a", "b"),
                List.of(), List.of(1, 2));
        for (Object invalidExpandSubgraph : invalidExpandSubgraphs) {
            assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.draw("", "mermaid", invalidExpandSubgraph));
            assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.drawBytes("", "svg", invalidExpandSubgraph));
            assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.drawBytes("", "png", invalidExpandSubgraph));
        }

        List<Object> invalidAnimations = List.of("", "true", "xxx", 1, 0, Map.of(), Map.of("a", "b"),
                List.of(), List.of(1, 2));
        for (Object invalidAnimation : invalidAnimations) {
            assertBaseError(StatusCode.DRAWABLE_GRAPH_TO_MERMAID_INVALID,
                    () -> flow.draw("", "mermaid", 1, invalidAnimation));
        }
    }

    @Test
    @DisplayName("stream workflow visualization with animation metadata")
    void testVisualizeSimpleStreamWorkflowAnimation() {
        String actual = withDrawable(() -> {
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockStartNode("start"), Map.of("a", "${a}"));
            flow.addWorkflowComp("a", new StreamCompNode("a"),
                    Map.of("value", "${start.a}"), true, List.of(ComponentAbility.STREAM));
            flow.addWorkflowComp("b", new CollectCompNode("b"),
                    Map.of("value", "${a.value}"), null, true,
                    Map.of("value1", "${a.value}"), null);
            flow.setEndComp("end", new MockEndNode("end"), Map.of("result1", "${b.value}"));
            flow.addConnection("start", "a");
            flow.addStreamConnection("a", "b");
            flow.addConnection("b", "end");
            return flow.draw("jiuwen workflow", "mermaid", false, true);
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"b\"]",
                "\tnode_4(\"end\")",
                "\tnode_1 --> node_2",
                "\tnode_2 link_1@==> node_3",
                "\tlink_1@{animate: true}",
                "\tnode_3 --> node_4"), actual);
    }

    @Test
    @DisplayName("intent workflow visualization")
    void testVisualizeSimpleWorkflowIntent() {
        String actual = withDrawable(() -> {
            System.setProperty("SSRF_PROTECT_ENABLED", "false");
            Workflow flow = new Workflow();
            flow.setStartComp("start", new MockStartNode("start"), Map.of("a", "${a}"));

            IntentDetectionCompConfig intentConfig = new IntentDetectionCompConfig();
            intentConfig.setUserPrompt("judge intent");
            intentConfig.setCategoryNameList(List.of("query weather"));
            IntentDetectionComponent intent = new IntentDetectionComponent(intentConfig);
            intent.addBranch("${intent.classification_id} == 1", List.of("llm"), "weather branch");
            intent.addBranch("${intent.classification_id} == 0", List.of("end"), "default branch");
            flow.addWorkflowComp("intent", intent, Map.of("query", "${start.query}"));

            LLMCompConfig llmConfig = new LLMCompConfig();
            llmConfig.setTemplateContent(List.of(Map.of("role", "user", "content", "")));
            llmConfig.setResponseFormat(Map.of("type", "json"));
            llmConfig.setOutputConfig(Map.of(
                    "location", Map.of("type", "string", "description", "location", "required", true),
                    "date", Map.of("type", "string", "description", "date", "required", true),
                    "query", Map.of("type", "string", "description", "query", "required", true)));
            flow.addWorkflowComp("llm", new LLMComponent(llmConfig), Map.of("query", "${start.query}"));

            ToolComponentConfig toolConfig = new ToolComponentConfig();
            toolConfig.setToolId("WeatherReporter");
            Runner.resourceMgr().addTool(new RestfulApi(RestfulApiCard.builder()
                    .id("WeatherReporter")
                    .name("WeatherReporter")
                    .description("weather plugin")
                    .inputParams(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "location", Map.of("type", "string"),
                                    "date", Map.of("type", "string")),
                            "required", List.of("location", "date")))
                    .url("http://127.0.0.1:9000/weather")
                    .method("GET")
                    .headers(Map.of())
                    .build()), null);
            flow.addWorkflowComp("plugin", new ToolComponent(toolConfig),
                    Map.of("location", "${llm.location}", "date", "${llm.date}"));
            flow.setEndComp("end", new MockEndNode("end"), Map.of("output", "${plugin.data}"));
            flow.addConnection("start", "intent");
            flow.addConnection("llm", "plugin");
            flow.addConnection("plugin", "end");
            return flow.draw("jiuwen workflow");
        });

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"start\")",
                "\tnode_2[\"intent\"]",
                "\tnode_3[\"llm\"]",
                "\tnode_4[\"plugin\"]",
                "\tnode_5(\"end\")",
                "\tnode_2 -.->|\"${intent.classification_id} == 1\"| node_3",
                "\tnode_2 -.->|\"${intent.classification_id} == 0\"| node_5",
                "\tnode_1 --> node_2",
                "\tnode_3 --> node_4",
                "\tnode_4 --> node_5"), actual);
    }

    private static Workflow buildSubWorkflow(String startId, String middleId, String endId) {
        Workflow flow = new Workflow();
        flow.setStartComp(startId, new MockStartNode("start"),
                Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)));
        flow.addWorkflowComp(middleId, new Node1("a"),
                Map.of("aa", "${start.a}", "ac", "${start.c}"));
        flow.setEndComp(endId, new MockEndNode("end"), Map.of("result", "${a.aa}"));
        flow.addConnection(startId, middleId);
        flow.addConnection(middleId, endId);
        return flow;
    }

    private static Workflow buildOuterSubWorkflow(Workflow subFlow) {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new MockStartNode("start"),
                Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)));
        flow.addWorkflowComp("a", new Node1("a"), Map.of("aa", "${start.a}", "ac", "${start.c}"));
        flow.addWorkflowComp("sub_flow", new SubWorkflowComponentImpl(subFlow));
        flow.setEndComp("end", new MockEndNode("end"), Map.of("result", "${a.aa}"));
        flow.addConnection("start", "a");
        flow.addConnection("a", "sub_flow");
        flow.addConnection("sub_flow", "end");
        return flow;
    }

    private static Workflow buildLoopWorkflow(boolean setEndCompDirectly) {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new MockStartNode("s"), Map.of("a", "${input_number}"));
        flow.addWorkflowComp("a", new CommonNode("a"), Map.of("array", "${input_array}"));

        LoopGroup loopGroup = new LoopGroup();
        loopGroup.addWorkflowComp("1", new AddTenNode("1"), Map.of("source", "${l.item}", "check", "${s.a}"));
        loopGroup.addWorkflowComp("2", new AddTenNode("2"), Map.of("source", "${l.user_var}"));
        loopGroup.addWorkflowComp("3", new LoopSetVariableComponent(Map.of("${l.user_var}", "${2.result}")));
        loopGroup.addWorkflowComp("4", new CommonNode("4"), Map.of("index", "${l.index}"));
        loopGroup.startComp("1");
        loopGroup.addConnection("1", "2");
        loopGroup.addConnection("2", "3");
        loopGroup.addConnection("3", "4");
        if (setEndCompDirectly) {
            loopGroup.endComp("4");
        } else {
            loopGroup.endNodes("4");
        }

        LoopComponentImpl loop = new LoopComponentImpl(loopGroup,
                Map.of("results", "${1.result}", "user_var", "${l.user_var}",
                        "index_collect", "${4.index}"));
        flow.addWorkflowComp("l", loop,
                Map.of("loop_type", "array",
                        "loop_array", Map.of("item", "${a.array}"),
                        "intermediate_var", Map.of("user_var", "${s.a}")));
        flow.addWorkflowComp("b", new CommonNode("b"),
                Map.of("array_result", "${l.results}", "user_var", "${l.user_var}"));
        flow.setEndComp("e", new MockEndNode("e"),
                Map.of("array_result", "${b.array_result}", "user_var", "${b.user_var}",
                        "index", "${l.index_collect}"));
        flow.addConnection("s", "a");
        flow.addConnection("a", "l");
        flow.addConnection("l", "b");
        flow.addConnection("b", "e");
        return flow;
    }

    private static void assertLoopMermaid(List<String> actual) {
        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"s\")",
                "\tnode_2[\"a\"]",
                "\tnode_3[\"l\"]",
                "\tnode_4[\"b\"]",
                "\tnode_5(\"e\")",
                "\tnode_3 -.-> node_3",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_3 -.-> node_4",
                "\tnode_4 --> node_5"), actual.get(0));

        assertEquals(mermaid(
                "---",
                "title: jiuwen workflow",
                "---",
                "flowchart TB",
                "\tnode_1(\"s\")",
                "\tnode_2[\"a\"]",
                "\tnode_8[\"b\"]",
                "\tnode_9(\"e\")",
                "\tsubgraph node_7 [\"l\"]",
                "\tdirection TB",
                "\tnode_3(\"1\")",
                "\tnode_4[\"2\"]",
                "\tnode_5[\"3\"]",
                "\tnode_6(\"4\")",
                "\tend",
                "\tnode_6 -.-> node_3",
                "\tnode_1 --> node_2",
                "\tnode_2 --> node_3",
                "\tnode_6 -.-> node_8",
                "\tnode_8 --> node_9",
                "\tnode_3 --> node_4",
                "\tnode_4 --> node_5",
                "\tnode_5 --> node_6"), actual.get(1));
    }

    private static <T> T withDrawable(Supplier<T> supplier) {
        String previous = System.getProperty("WORKFLOW_DRAWABLE");
        System.setProperty("WORKFLOW_DRAWABLE", "true");
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                System.clearProperty("WORKFLOW_DRAWABLE");
            } else {
                System.setProperty("WORKFLOW_DRAWABLE", previous);
            }
        }
    }

    private static String mermaid(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static void assertBaseError(StatusCode code, Runnable action) {
        BaseError error = assertThrows(BaseError.class, action::run);
        assertEquals(code.getCode(), error.getCode());
    }

    private static class LiteralRouter implements Function<Object, Object>, Drawable.TargetProvider {
        @Override
        public Object apply(Object ignored) {
            return "a";
        }

        @Override
        public List<String> getTargets() {
            return List.of("a", "b");
        }
    }

    private abstract static class NamedNode extends WorkflowComponent {
        private final String nodeId;

        private NamedNode(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static final class MockStartNode extends Start {
        private final String nodeId;

        private MockStartNode(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }
    }

    private static final class MockEndNode extends End {
        private final String nodeId;

        private MockEndNode(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }
    }

    private static final class Node1 extends NamedNode {
        private Node1(String nodeId) {
            super(nodeId);
        }
    }

    private static final class CommonNode extends NamedNode {
        private CommonNode(String nodeId) {
            super(nodeId);
        }
    }

    private static final class AddTenNode extends NamedNode {
        private AddTenNode(String nodeId) {
            super(nodeId);
        }
    }

    private static final class StreamCompNode extends NamedNode {
        private StreamCompNode(String nodeId) {
            super(nodeId);
        }
    }

    private static final class CollectCompNode extends NamedNode {
        private CollectCompNode(String nodeId) {
            super(nodeId);
        }
    }
}
