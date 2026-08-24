/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowConfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code TracerWorkflowUtils} in
 * {@code openjiuwen/core/session/tracer/workflow_tracer.py}.
 */
class TracerWorkflowUtilsTest {

    @Test
    void workflowStartReadsWorkflowCardMetadataFromSessionConfig() {
        Tracer tracer = initializedTracer();
        TestConfig config = new TestConfig();
        config.addWorkflowConfig("workflow-1", new WorkflowConfig(
                new WorkflowCard("card-1", "workflow name", "description", "v1", null)));
        TestSession session = new TestSession(tracer, config, new TestState())
                .workflowId("workflow-1");

        TracerWorkflowUtils.traceWorkflowStart(session, Map.of("input", "value"));

        TraceWorkflowSpan span = tracer.getWorkflowSpan("workflow-1", "");
        assertNotNull(span);
        assertEquals("workflow-1", span.getWorkflowId());
        assertEquals("v1", span.getWorkflowVersion());
        assertEquals("workflow name", span.getWorkflowName());
        assertEquals(Map.of("input", "value"), span.getInputs());
    }

    @Test
    void componentBeginIncludesLoopMetadataAndSourceIds() {
        Tracer tracer = initializedTracer();
        tracer.registerWorkflowSpanManager("parent-node");
        TestState state = new TestState();
        state.globals.put(Constant.LOOP_ID, "loop-node");
        state.globals.put("loop-node.index", 3);
        TestSession session = new TestSession(tracer, new TestConfig(), state)
                .workflowId("workflow-1")
                .node("component-node", "Tool")
                .parentId("parent-node")
                .executableId("parent-node.component-node");

        TracerWorkflowUtils.traceComponentBegin(session, List.of("source-1"));

        TraceWorkflowSpan span = tracer.getWorkflowSpan("parent-node.component-node", "parent-node");
        assertNotNull(span);
        assertEquals("component-node", span.getComponentId());
        assertEquals("component-node", span.getComponentName());
        assertEquals("Tool", span.getComponentType());
        assertEquals("workflow-1", span.getWorkflowId());
        assertEquals("loop-node", span.getLoopNodeId());
        assertEquals(3, span.getLoopIndex());
        assertEquals(List.of("source-1"), span.getSourceIds());
    }

    @Test
    void componentDonePopsWorkflowSpanOnlyWhenLoopIdExists() {
        Tracer loopTracer = initializedTracer();
        loopTracer.registerWorkflowSpanManager("parent-node");
        TestState loopState = new TestState();
        loopState.globals.put(Constant.LOOP_ID, "loop-node");
        TestSession loopSession = new TestSession(loopTracer, new TestConfig(), loopState)
                .workflowId("workflow-1")
                .node("component-node", "Tool")
                .parentId("parent-node")
                .executableId("parent-node.component-node");
        TracerWorkflowUtils.traceComponentBegin(loopSession, null);

        TracerWorkflowUtils.traceComponentDone(loopSession);

        assertNull(loopTracer.getWorkflowSpan("parent-node.component-node", "parent-node"));

        Tracer plainTracer = initializedTracer();
        plainTracer.registerWorkflowSpanManager("parent-node");
        TestSession plainSession = new TestSession(plainTracer, new TestConfig(), new TestState())
                .workflowId("workflow-1")
                .node("component-node", "Tool")
                .parentId("parent-node")
                .executableId("parent-node.component-node");
        TracerWorkflowUtils.traceComponentBegin(plainSession, null);

        TracerWorkflowUtils.traceComponentDone(plainSession);

        assertNotNull(plainTracer.getWorkflowSpan("parent-node.component-node", "parent-node"));
    }

    @Test
    void traceErrorRejectsNullErrorLikePythonSource() {
        TestSession session = new TestSession(initializedTracer(), new TestConfig(), new TestState())
                .executableId("component-node");

        BaseError error = assertThrows(BaseError.class, () -> TracerWorkflowUtils.traceError(session, null));

        assertSame(StatusCode.TRACER_WORKFLOW_TRACE_ERROR, error.getStatus());
        assertEquals("'trace_error''s error is None", error.getParams().get("reason"));
    }

    @Test
    void streamInputCopiesMappingChunksAndSkipsStrings() {
        Tracer tracer = initializedTracer();
        tracer.registerWorkflowSpanManager("parent-node");
        TestSession session = new TestSession(tracer, new TestConfig(), new TestState())
                .parentId("parent-node")
                .executableId("parent-node.component-node");
        Map<Object, Object> chunk = new LinkedHashMap<>();
        chunk.put(7, "seven");

        TracerWorkflowUtils.traceComponentStreamInput(session, chunk, true);
        TracerWorkflowUtils.traceComponentStreamInput(session, "plain text", true);

        TraceWorkflowSpan span = tracer.getWorkflowSpan("parent-node.component-node", "parent-node");
        assertNotNull(span);
        assertEquals(List.of(Map.of("7", "seven")), span.getStreamInputs());
    }

    @Test
    void streamOutputAndInteractiveInputsUpdateWorkflowSpan() {
        Tracer tracer = initializedTracer();
        tracer.registerWorkflowSpanManager("parent-node");
        TestSession session = new TestSession(tracer, new TestConfig(), new TestState())
                .workflowId("workflow-1")
                .node("component-node", "Message")
                .parentId("parent-node")
                .executableId("parent-node.component-node");
        Map<Object, Object> chunk = new LinkedHashMap<>();
        chunk.put("token", "value");

        TracerWorkflowUtils.traceComponentStreamOutput(session, chunk);
        TracerWorkflowUtils.traceComponentInteractiveInputs(session, Map.of("prompt", "hello"), false);

        TraceWorkflowSpan span = tracer.getWorkflowSpan("parent-node.component-node", "parent-node");
        assertNotNull(span);
        assertEquals(List.of(Map.of("token", "value")), span.getStreamOutputs());
        assertEquals(Map.of("prompt", "hello"), span.getInteractiveInputs());
        assertEquals("component-node", span.getComponentId());
        assertEquals("Message", span.getComponentType());
    }

    @Test
    void missingTracerKeepsAllHelpersNoop() {
        TestSession session = new TestSession(null, new TestConfig(), new TestState());

        TracerWorkflowUtils.traceWorkflowStart(session, Map.of("input", "value"));
        TracerWorkflowUtils.traceComponentBegin(session);
        TracerWorkflowUtils.traceComponentInputs(session, Map.of("input", "value"), true);
        TracerWorkflowUtils.traceComponentOutputs(session, Map.of("output", "value"));
        TracerWorkflowUtils.traceComponentDone(session);
        TracerWorkflowUtils.trace(session, Map.of("event", "value"));
        TracerWorkflowUtils.traceComponentInteractiveInputs(session, Map.of("prompt", "hello"), true);

        assertNull(session.tracer());
        assertFalse(session.state().dump().containsKey("unexpected"));
    }

    private static Tracer initializedTracer() {
        Tracer tracer = new Tracer();
        tracer.init(null);
        return tracer;
    }

    static final class TestSession extends BaseSession {
        private final Tracer tracer;
        private final TestConfig config;
        private final TestState state;
        private String workflowId = "";
        private String nodeId = "";
        private String nodeType = "";
        private String parentId = "";
        private String executableId = "";

        TestSession(Tracer tracer, TestConfig config, TestState state) {
            this.tracer = tracer;
            this.config = config;
            this.state = state;
        }

        TestSession workflowId(String value) {
            this.workflowId = value;
            return this;
        }

        TestSession node(String id, String type) {
            this.nodeId = id;
            this.nodeType = type;
            return this;
        }

        TestSession parentId(String value) {
            this.parentId = value;
            return this;
        }

        TestSession executableId(String value) {
            this.executableId = value;
            return this;
        }

        @Override
        public SessionConfigAccess config() {
            return config;
        }

        @Override
        public SessionStateAccess state() {
            return state;
        }

        @Override
        public Object tracer() {
            return tracer;
        }

        public String workflowId() {
            return workflowId;
        }

        public String nodeId() {
            return nodeId;
        }

        public String nodeType() {
            return nodeType;
        }

        public String parentId() {
            return parentId;
        }

        public String executableId() {
            return executableId;
        }
    }

    static final class TestConfig implements SessionConfigAccess {
        private final Map<String, Object> workflowConfigs = new LinkedHashMap<>();

        @Override
        public Object getEnv(String key) {
            return null;
        }

        @Override
        public Object getWorkflowConfig(String workflowId) {
            return workflowConfigs.get(workflowId);
        }

        @Override
        public void addWorkflowConfig(String workflowId, Object workflowConfig) {
            workflowConfigs.put(workflowId, workflowConfig);
        }
    }

    static final class TestState implements SessionStateAccess {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, Object> globals = new LinkedHashMap<>();

        @Override
        public Object get(Object key) {
            return values.get(String.valueOf(key));
        }

        @Override
        public void update(Map<String, Object> data) {
            values.putAll(data);
        }

        @Override
        public Object getGlobal(Object key) {
            return globals.get(String.valueOf(key));
        }

        @Override
        public void updateGlobal(Map<String, Object> data) {
            globals.putAll(data);
        }

        @Override
        public Map<String, Object> dump() {
            return new LinkedHashMap<>(values);
        }
    }
}
