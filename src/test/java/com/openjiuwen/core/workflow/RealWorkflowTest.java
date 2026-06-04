/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E tests for a minimal deterministic travel/weather assistant workflow.
 *
 * <p>Mirrors Python's {@code tests/system_tests/workflow/test_real_workflow.py}.</p>
 */
@Tag("system-test")
class RealWorkflowTest {

    private static final String FINAL_RESULT = "上海今天晴 30°C";

    @Test
    @Tag("level1")
    @DisplayName("Start -> Intent -> Branch -> LLM -> Questioner -> Plugin -> End")
    void testWorkflowLlmQuestionerPlugin() {
        Workflow flow = buildTravelAssistantWorkflow();

        WorkflowOutput output = flow.invoke(
                Map.of("query", "查询上海今天的天气"), newSession(), null);

        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertInstanceOf(Map.class, output.getResult());
        Map<?, ?> result = (Map<?, ?>) output.getResult();
        assertEquals(FINAL_RESULT, result.get("output"));
        assertNull(result.get("fallback"));
    }

    @Test
    @Tag("level1")
    @DisplayName("branch can route directly to End for non assistant intents")
    void testWorkflowIntentFallbackBranch() {
        Workflow flow = buildTravelAssistantWorkflow();

        WorkflowOutput output = flow.invoke(
                Map.of("query", "讲个笑话"), newSession(), null);

        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertInstanceOf(Map.class, output.getResult());
        Map<?, ?> result = (Map<?, ?>) output.getResult();
        assertNull(result.get("output"));
        assertEquals("unsupported intent: 讲个笑话", result.get("fallback"));
    }

    @Test
    @Tag("level1")
    @DisplayName("LLM-like component can stream through StreamWriter")
    void testStreamWorkflowLlmWithStreamWriter() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new Start(), Map.of("query", "${query}"), null);
        flow.addWorkflowComp("llm", new WriterLlmComponent(),
                Map.of("query", "${s.query}"), null);
        flow.setEndComp("e", new End(Map.of("responseTemplate", "{{output}}")),
                Map.of("output", "${llm.userFields.joke}"), null);
        flow.addConnection("s", "llm");
        flow.addConnection("llm", "e");

        Iterator<WorkflowChunk> iterator = flow.stream(
                Map.of("query", "写一个笑话。注意：不要超过20个字！"),
                newSession(),
                null,
                List.of(StreamMode.OUTPUT));

        List<WorkflowChunk> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }

        assertFalse(chunks.isEmpty(), "stream should emit output chunks");
        assertTrue(chunks.stream().anyMatch(chunk ->
                chunk instanceof OutputSchema output
                        && String.valueOf(output.getPayload()).contains("短笑话")),
                "stream writer should expose the LLM-like partial output");
    }

    private static Workflow buildTravelAssistantWorkflow() {
        Workflow flow = new Workflow(WorkflowCard.builder()
                .id("real_workflow_test")
                .name("Deterministic Travel Assistant")
                .version("1.0.0")
                .build());

        flow.setStartComp("start", new MockNodes.MockStartNode("start"),
                Map.of("query", "${query}"), null);
        flow.addWorkflowComp("intent", new DeterministicIntentComponent(),
                Map.of("input", "${query}"), null);
        flow.addWorkflowComp("llm", new DeterministicLlmComponent(),
                Map.of("userFields", Map.of("query", "${start.query}")), null);
        flow.addWorkflowComp("questioner", new DeterministicQuestionerComponent(),
                Map.of("query", "${start.query}", "llmFields", "${llm.userFields}"), null);
        flow.addWorkflowComp("plugin", new DeterministicToolComponent(),
                Map.of("userFields", "${questioner.userFields.key_fields}", "validated", true), null);
        flow.setEndComp("end", new MockNodes.MockEndNode("end"),
                Map.of("output", "${plugin.result}", "fallback", "${intent.fallback}"), null);

        BranchComponent branch = new BranchComponent();
        branch.addBranch("${intent.classificationId} < 1", List.of("llm"), "1");
        branch.addBranch("${intent.classificationId} = 1", List.of("end"), "2");
        flow.addWorkflowComp("branch", branch);

        flow.addConnection("start", "intent");
        flow.addConnection("intent", "branch");
        flow.addConnection("llm", "questioner");
        flow.addConnection("questioner", "plugin");
        flow.addConnection("plugin", "end");
        return flow;
    }

    private static WorkflowSessionApi newSession() {
        return new WorkflowSessionApi(null, UUID.randomUUID().toString(), Map.of());
    }

    /**
     * <p>Mirrors the intent-detection role in Python's real workflow test.</p>
     */
    private static final class DeterministicIntentComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            String input = String.valueOf(((Map<?, ?>) inputs).get("input"));
            boolean supported = input.contains("天气") || input.contains("旅游");
            if (supported) {
                return Map.of("classificationId", 0, "classification_id", 0);
            }
            return Map.of("classificationId", 1, "classification_id", 1,
                    "fallback", "unsupported intent: " + input);
        }
    }

    /**
     * <p>Mirrors the structured-field extraction role of Python's {@code LLMComponent}.</p>
     */
    private static final class DeterministicLlmComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            String query = String.valueOf(((Map<?, ?>) ((Map<?, ?>) inputs).get("userFields")).get("query"));
            String location = query.contains("上海") ? "上海" : "杭州";
            return Map.of("userFields", Map.of("location", location, "date", "today"));
        }
    }

    /**
     * <p>Mirrors the required-field collection role of Python's {@code QuestionerComponent}.</p>
     */
    private static final class DeterministicQuestionerComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object llmFields = ((Map<?, ?>) inputs).get("llmFields");
            assertInstanceOf(Map.class, llmFields);
            return Map.of("userFields", Map.of("key_fields", llmFields));
        }
    }

    /**
     * <p>Mirrors the mocked REST plugin call in Python's real workflow test.</p>
     */
    private static final class DeterministicToolComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<?, ?> inputMap = (Map<?, ?>) inputs;
            assertEquals(Boolean.TRUE, inputMap.get("validated"));
            Map<?, ?> fields = (Map<?, ?>) inputMap.get("userFields");
            assertNotNull(fields.get("location"));
            assertNotNull(fields.get("date"));
            return Map.of("result", FINAL_RESULT);
        }
    }

    /**
     * <p>Mirrors the stream-writer behavior under Python's LLM streaming workflow test.</p>
     */
    private static final class WriterLlmComponent extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            session.writeStream(new OutputSchema("output", 0, Map.of("joke", "短笑话")));
            return Map.of("userFields", Map.of("joke", "短笑话"));
        }
    }
}
