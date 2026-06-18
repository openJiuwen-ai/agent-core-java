/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused tests for internal workflow sessions.
 *
 * <p>Mirrors Python's {@code WorkflowSession}, {@code NodeSession}, and
 * {@code SubWorkflowSession} in
 * {@code openjiuwen/core/session/internal/workflow.py}.</p>
 */
class WorkflowSessionInternalTest {

    @Test
    void rootWorkflowSessionUsesPythonUuidHexShapeAndDefaults() {
        WorkflowSession session = new WorkflowSession();

        assertEquals(32, session.sessionId().length());
        assertFalse(session.sessionId().contains("-"));
        assertEquals("", session.workflowId());
        assertEquals("", session.mainWorkflowId());
        assertEquals(0, session.workflowNestingDepth());
        assertNull(session.parent());
        assertNull(session.tracer());
        assertInstanceOf(Config.class, session.config());
        assertInstanceOf(WorkflowCommitState.class, session.state());
    }

    @Test
    void parentWorkflowSessionInheritsParentSessionConfigTracerAndCheckpointer() {
        Config config = new Config();
        Tracer tracer = new Tracer();
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();
        ParentSession parent = new ParentSession("parent-session", config, tracer, checkpointer);

        WorkflowSession session = new WorkflowSession("workflow-a", parent);

        assertEquals("parent-session", session.sessionId());
        assertSame(parent, session.parent());
        assertSame(config, session.config());
        assertSame(tracer, session.tracer());
        assertSame(checkpointer, session.checkpointer());
        assertEquals("workflow-a", session.workflowId());
        assertEquals("workflow-a", session.mainWorkflowId());
    }

    @Test
    void streamWriterManagerIsSetOnlyOnceAndTracerCanBeReplaced() {
        WorkflowSession session = new WorkflowSession("workflow-a");
        StreamWriterManager first = new StreamWriterManager(new StreamEmitter());
        StreamWriterManager second = new StreamWriterManager(new StreamEmitter());
        Tracer tracer = new Tracer();

        session.setStreamWriterManager(first);
        session.setStreamWriterManager(second);
        session.setTracer(tracer);

        assertSame(first, session.streamWriterManager());
        assertSame(tracer, session.tracer());
    }

    @Test
    void nodeSessionBuildsExecutableIdsAndDelegatesSessionContext() {
        WorkflowSession root = new WorkflowSession("main-workflow", null, "session-a", null, null);

        NodeSession node = new NodeSession(root, "node-a", "llm");
        NodeSession child = new NodeSession(node, "node-b", "tool");

        assertEquals("", node.parentId());
        assertEquals("node-a", node.executableId());
        assertEquals("node-a", child.parentId());
        assertEquals("node-a.node-b", child.executableId());
        assertEquals("session-a", child.sessionId());
        assertEquals("main-workflow", child.workflowId());
        assertEquals("main-workflow", child.mainWorkflowId());
        assertEquals(0, child.workflowNestingDepth());
        assertSame(root.streamWriterManager(), child.streamWriterManager());
        assertSame(root.checkpointer(), child.checkpointer());
    }

    @Test
    void subWorkflowSessionMirrorsPythonNodeSessionConstructor() {
        WorkflowSession root = new WorkflowSession("main-workflow", null, "session-a", null, null);
        NodeSession node = new NodeSession(root, "node-a", "llm");

        SubWorkflowSession subWorkflow = new SubWorkflowSession(node, "sub-workflow");

        assertSame(root, subWorkflow.parent());
        assertEquals("node-a", subWorkflow.nodeId());
        assertEquals("llm", subWorkflow.nodeType());
        assertEquals("sub-workflow", subWorkflow.workflowId());
        assertEquals("main-workflow", subWorkflow.mainWorkflowId());
        assertEquals(1, subWorkflow.workflowNestingDepth());
        assertNull(subWorkflow.actorManager());
    }

    private static final class ParentSession extends BaseSession {
        private final String sessionId;
        private final Config config;
        private final Tracer tracer;
        private final Checkpointer checkpointer;

        private ParentSession(String sessionId, Config config, Tracer tracer, Checkpointer checkpointer) {
            this.sessionId = sessionId;
            this.config = config;
            this.tracer = tracer;
            this.checkpointer = checkpointer;
        }

        @Override
        public Config config() {
            return config;
        }

        @Override
        public Object tracer() {
            return tracer;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public Object checkpointer() {
            return checkpointer;
        }
    }

    private static final class RecordingCheckpointer extends Checkpointer {
    }
}
