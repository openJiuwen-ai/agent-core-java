package com.openjiuwen.agentevolving.trajectory;

import com.openjiuwen.core.session.tracer.TraceAgentSpan;
import com.openjiuwen.core.session.tracer.TraceWorkflowSpan;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracerTrajectoryExtractorTest {

    private final TracerTrajectoryExtractor extractor = new TracerTrajectoryExtractor();

    @Test
    void extractHandlesSessionWithoutTracer() {
        Trajectory result = extractor.extract(new FakeSession(null), execution("case_1", "exec_1"));

        assertEquals("case_1", result.getCaseId());
        assertEquals("exec_1", result.getExecutionId());
        assertNotNull(result.getSteps());
        assertTrue(result.getSteps().isEmpty());
        assertNull(result.getTraceId());
    }

    @Test
    void extractMapsAgentSpanKindsAndFallbackFields() {
        TraceAgentSpan llmSpan = agentSpan("inv_1", "llm");
        llmSpan.setMetaData(new LinkedHashMap<>(Map.of("node_id", "node_1", "agent_id", "agent_1", "role", "assistant")));
        llmSpan.setInputs(Map.of("inputs", Map.of("query", "hello")));
        llmSpan.setOutputs(Map.of("outputs", Map.of("response", "world")));
        llmSpan.setStartTime(LocalDateTime.of(2024, 1, 1, 12, 0, 0));
        llmSpan.setEndTime(LocalDateTime.of(2024, 1, 1, 12, 0, 1));
        llmSpan.setName("fallback_name");

        TraceAgentSpan toolSpan = agentSpan("inv_2", "plugin");
        toolSpan.setParentInvokeId("inv_1");
        toolSpan.setMetaData(new LinkedHashMap<>(Map.of("component_id", "tool_node")));

        FakeSpanManager manager = new FakeSpanManager();
        manager.add(llmSpan);
        manager.add(toolSpan);

        Trajectory result = extractor.extract(
                new FakeSession(new FakeTracer("trace_1", manager, Map.of())),
                execution("case_1", "exec_1")
        );

        assertEquals("trace_1", result.getTraceId());
        assertEquals(2, result.getSteps().size());

        TrajectoryStep llmStep = result.getSteps().get(0);
        assertEquals("llm", llmStep.getKind());
        assertEquals("fallback_name", llmStep.getOperatorId());
        assertEquals("agent_1", llmStep.getAgentId());
        assertEquals("assistant", llmStep.getRole());
        assertEquals("node_1", llmStep.getNodeId());
        assertEquals(Map.of("query", "hello"), llmStep.getInputs());
        assertEquals(Map.of("response", "world"), llmStep.getOutputs());
        assertEquals("inv_1", llmStep.getMeta().get("invoke_id"));

        TrajectoryStep toolStep = result.getSteps().get(1);
        assertEquals("tool", toolStep.getKind());
        assertEquals("tool_node", toolStep.getNodeId());
        assertEquals("inv_1", toolStep.getMeta().get("parent_invoke_id"));
        assertNotNull(result.getEdges());
        assertEquals(1, result.getEdges().size());
        assertEquals(0, result.getEdges().get(0)[0]);
        assertEquals(1, result.getEdges().get(0)[1]);
    }

    @Test
    void extractBuildsChildInvokeEdgesAndFallsBackToNameWhenOperatorIdMissing() {
        TraceAgentSpan parent = agentSpan("parent_1", "llm");
        parent.setMetaData(new LinkedHashMap<>());
        parent.setChildInvokesId(new ArrayList<>(List.of("child_1")));
        parent.setName("parent_name");

        TraceAgentSpan child = agentSpan("child_1", "chain");
        child.setMetaData(new LinkedHashMap<>(Map.of("operator_id", "meta_operator")));
        child.setParentInvokeId(null);
        child.setName("child_name");
        child.setInputs(Map.of("raw", "value"));
        child.setOutputs(Map.of("final", "answer"));

        FakeSpanManager manager = new FakeSpanManager();
        manager.add(parent);
        manager.add(child);

        Trajectory result = extractor.extract(
                new FakeSession(new FakeTracer("trace_2", manager, Map.of())),
                execution("case_2", "exec_2")
        );

        TrajectoryStep parentStep = result.getSteps().get(0);
        TrajectoryStep childStep = result.getSteps().get(1);

        assertEquals("parent_name", parentStep.getOperatorId());
        assertEquals("agent", childStep.getKind());
        assertEquals("meta_operator", childStep.getOperatorId());
        assertEquals(Map.of("raw", "value"), childStep.getInputs());
        assertEquals(Map.of("final", "answer"), childStep.getOutputs());
        assertNotNull(result.getEdges());
        assertEquals(1, result.getEdges().size());
        assertEquals(0, result.getEdges().get(0)[0]);
        assertEquals(1, result.getEdges().get(0)[1]);
    }

    @Test
    void extractBuildsWorkflowStepsWithSnakeCaseMetadata() {
        TraceWorkflowSpan workflowSpan = new TraceWorkflowSpan("trace_3", "wf_inv_1", null, "parent_node");
        workflowSpan.setWorkflowId("wf_1");
        workflowSpan.setWorkflowName("workflow_name");
        workflowSpan.setComponentId("component_1");
        workflowSpan.setComponentName("component_name");
        workflowSpan.setComponentType("action");
        workflowSpan.setLoopNodeId("loop_1");
        workflowSpan.setLoopIndex(2);
        workflowSpan.setParentNodeId("parent_node");
        workflowSpan.setInputs(Map.of("inputs", Map.of("x", 1)));
        workflowSpan.setOutputs(Map.of("outputs", Map.of("y", 2)));

        FakeSpanManager workflowManager = new FakeSpanManager();
        workflowManager.add(workflowSpan);

        Trajectory result = extractor.extract(
                new FakeSession(new FakeTracer("trace_3", new FakeSpanManager(), Map.of("wf", workflowManager))),
                execution("case_3", "exec_3")
        );

        assertEquals(1, result.getSteps().size());
        TrajectoryStep workflowStep = result.getSteps().get(0);
        assertEquals("workflow", workflowStep.getKind());
        assertEquals("component_1", workflowStep.getNodeId());
        assertEquals(Map.of("x", 1), workflowStep.getInputs());
        assertEquals(Map.of("y", 2), workflowStep.getOutputs());
        assertEquals("wf_1", workflowStep.getMeta().get("workflow_id"));
        assertEquals("workflow_name", workflowStep.getMeta().get("workflow_name"));
        assertEquals("component_1", workflowStep.getMeta().get("component_id"));
        assertEquals("component_name", workflowStep.getMeta().get("component_name"));
        assertEquals("action", workflowStep.getMeta().get("component_type"));
        assertEquals("loop_1", workflowStep.getMeta().get("loop_node_id"));
        assertEquals(2, workflowStep.getMeta().get("loop_index"));
        assertEquals("parent_node", workflowStep.getMeta().get("parent_node_id"));
    }

    @Test
    void extractUsesLlmCallIdWhenOperatorIdAndMetaOperatorAreMissing() {
        FlexibleAgentSpan span = new FlexibleAgentSpan("trace", "inv_llm_call", null);
        span.setInvokeId("inv_llm_call");
        span.setInvokeType("llm");
        span.setLlmCallId("llm_call_1");
        span.setMetaData(new LinkedHashMap<>(Map.of("node_id", "node_llm")));
        span.setName("fallback_name");

        FakeSpanManager manager = new FakeSpanManager();
        manager.add(span);

        Trajectory result = extractor.extract(
                new FakeSession(new FakeTracer("trace_4", manager, Map.of())),
                execution("case_4", "exec_4")
        );

        assertEquals(1, result.getSteps().size());
        assertEquals("llm_call_1", result.getSteps().get(0).getOperatorId());
        assertEquals("node_llm", result.getSteps().get(0).getNodeId());
    }

    @Test
    void extractSupportsPythonStyleFieldsAndPreservesNonMapErrors() {
        PythonStyleSpan span = new PythonStyleSpan();
        span.invoke_type = "plugin";
        span.invoke_id = "inv_py";
        span.llm_call_id = "llm_py";
        span.error = "boom";
        span.inputs = Map.of("inputs", Map.of("query", "python"));
        span.outputs = Map.of("outputs", Map.of("answer", "java"));
        span.start_time = LocalDateTime.of(2024, 1, 1, 8, 0, 0);
        span.end_time = LocalDateTime.of(2024, 1, 1, 8, 0, 2);
        span.meta_data = new LinkedHashMap<>(Map.of(
                "agent_id", "agent_py",
                "role", "assistant",
                "node_id", "node_py",
                "nested", new LinkedHashMap<>(Map.of("list", new ArrayList<>(List.of("a", "b"))))
        ));

        PythonStyleSpanManager manager = new PythonStyleSpanManager(List.of(span));
        PythonStyleTracer tracer = new PythonStyleTracer("trace_py", manager);

        Trajectory result = extractor.extract(new PythonStyleSession(tracer), new ExecutionSpec("case_py", "exec_py"));

        assertEquals("trace_py", result.getTraceId());
        assertEquals(1, result.getSteps().size());
        TrajectoryStep step = result.getSteps().get(0);
        assertEquals("tool", step.getKind());
        assertEquals("llm_py", step.getOperatorId());
        assertEquals("agent_py", step.getAgentId());
        assertEquals("assistant", step.getRole());
        assertEquals("node_py", step.getNodeId());
        assertEquals(Map.of("query", "python"), step.getInputs());
        assertEquals(Map.of("answer", "java"), step.getOutputs());
        assertEquals("boom", step.getError());

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) step.getMeta().get("nested");
        @SuppressWarnings("unchecked")
        List<String> nestedList = (List<String>) nested.get("list");
        nestedList.add("mutated");

        @SuppressWarnings("unchecked")
        Map<String, Object> sourceNested = (Map<String, Object>) span.meta_data.get("nested");
        @SuppressWarnings("unchecked")
        List<String> sourceList = (List<String>) sourceNested.get("list");
        assertEquals(List.of("a", "b"), sourceList);
    }

    private static TraceAgentSpan agentSpan(String invokeId, String invokeType) {
        TraceAgentSpan span = new TraceAgentSpan("trace", invokeId, null);
        span.setInvokeId(invokeId);
        span.setInvokeType(invokeType);
        span.setMetaData(new LinkedHashMap<>(Map.of("operator_id", "op_" + invokeId)));
        return span;
    }

    private static ExecutionSpec execution(String caseId, String executionId) {
        return ExecutionSpec.builder().caseId(caseId).executionId(executionId).build();
    }

    private static final class FakeSession {
        private final Object tracer;

        private FakeSession(Object tracer) {
            this.tracer = tracer;
        }

        public Object tracer() {
            return tracer;
        }
    }

    private static final class FakeTracer {
        private final String traceId;
        private final Object tracerAgentSpanManager;
        private final Map<String, Object> tracerWorkflowSpanManagerDict;

        private FakeTracer(String traceId, Object tracerAgentSpanManager, Map<String, Object> tracerWorkflowSpanManagerDict) {
            this.traceId = traceId;
            this.tracerAgentSpanManager = tracerAgentSpanManager;
            this.tracerWorkflowSpanManagerDict = tracerWorkflowSpanManagerDict;
        }

        public String getTraceId() {
            return traceId;
        }

        public Object getTracerAgentSpanManager() {
            return tracerAgentSpanManager;
        }

        public Map<String, Object> getTracerWorkflowSpanManagerDict() {
            return tracerWorkflowSpanManagerDict;
        }
    }

    private static final class FakeSpanManager {
        private final List<String> order = new ArrayList<>();
        private final Map<String, Object> sessionSpans = new LinkedHashMap<>();

        private void add(Object span) {
            String invokeId;
            if (span instanceof TraceAgentSpan agentSpan) {
                invokeId = agentSpan.getInvokeId();
            } else if (span instanceof TraceWorkflowSpan workflowSpan) {
                invokeId = workflowSpan.getInvokeId();
            } else {
                throw new IllegalArgumentException("Unsupported span type");
            }
            order.add(invokeId);
            sessionSpans.put(invokeId, span);
        }
    }

    private static final class FlexibleAgentSpan extends TraceAgentSpan {
        private String llmCallId;

        private FlexibleAgentSpan(String traceId, String invokeId, String parentInvokeId) {
            super(traceId, invokeId, parentInvokeId);
        }

        public String getLlmCallId() {
            return llmCallId;
        }

        public void setLlmCallId(String llmCallId) {
            this.llmCallId = llmCallId;
        }

        @Override
        public FlexibleAgentSpan snapshot() {
            FlexibleAgentSpan copy = new FlexibleAgentSpan(getTraceId(), getInvokeId(), getParentInvokeId());
            copy.setStartTime(getStartTime());
            copy.setEndTime(getEndTime());
            copy.setInputs(getInputs());
            copy.setOutputs(getOutputs());
            copy.setError(getError());
            copy.setInvokeId(getInvokeId());
            copy.setParentInvokeId(getParentInvokeId());
            copy.setChildInvokesId(getChildInvokesId() == null ? null : new ArrayList<>(getChildInvokesId()));
            copy.setStatus(getStatus());
            copy.setOnInvokeData(getOnInvokeData());
            copy.setInvokeType(getInvokeType());
            copy.setName(getName());
            copy.setElapsedTime(getElapsedTime());
            copy.setMetaData(getMetaData() == null ? null : new LinkedHashMap<>(getMetaData()));
            copy.setLlmCallId(llmCallId);
            return copy;
        }
    }

    private static final class PythonStyleSession {
        private final Object tracer;

        private PythonStyleSession(Object tracer) {
            this.tracer = tracer;
        }
    }

    private static final class PythonStyleTracer {
        private final String _trace_id;
        private final Object tracer_agent_span_manager;
        private final Map<String, Object> tracer_workflow_span_manager_dict = Map.of();

        private PythonStyleTracer(String traceId, Object spanManager) {
            this._trace_id = traceId;
            this.tracer_agent_span_manager = spanManager;
        }
    }

    private static final class PythonStyleSpanManager {
        private final List<Object> spans;

        private PythonStyleSpanManager(List<Object> spans) {
            this.spans = spans;
        }

        public Collection<Object> getAllSpans() {
            return spans;
        }
    }

    private static final class PythonStyleSpan {
        private String invoke_type;
        private String invoke_id;
        private Object inputs;
        private Object outputs;
        private Object error;
        private LocalDateTime start_time;
        private LocalDateTime end_time;
        private Map<String, Object> meta_data;
        private String llm_call_id;
    }
}
