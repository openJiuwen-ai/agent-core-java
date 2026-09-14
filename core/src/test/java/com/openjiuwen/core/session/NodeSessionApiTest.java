/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.workflow.NodeSpec;
import com.openjiuwen.core.workflow.WorkflowConfig;
import com.openjiuwen.core.workflow.WorkflowSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Focused tests for the public node session facade.
 *
 * <p>Mirrors Python's {@code Session} in
 * {@code openjiuwen/core/session/node.py}.</p>
 */
class NodeSessionApiTest {

    @Test
    void exposesWorkflowComponentMetadataAndNodeConfig() {
        WorkflowSession workflow = new WorkflowSession("workflow-a");
        NodeSpec nodeSpec = new NodeSpec();
        nodeSpec.setMaxRetries(2);
        WorkflowSpec workflowSpec = new WorkflowSpec();
        workflowSpec.setCompConfigs(Map.of("node-a", nodeSpec));
        WorkflowConfig workflowConfig = new WorkflowConfig();
        workflowConfig.setSpec(workflowSpec);
        workflow.config().addWorkflowConfig("workflow-a", workflowConfig);

        NodeSessionApi session = new NodeSessionApi(new NodeSession(workflow, "node-a", "llm"));

        assertEquals("workflow-a", session.getWorkflowId());
        assertEquals("node-a", session.getComponentId());
        assertEquals("llm", session.getComponentType());
        assertEquals("[wf_id=workflow-a,comp_id=node-a]", session.getComponentDescrip());
        assertEquals("node-a", session.getExecutableId());
        assertSame(nodeSpec, session.getNodeConfig());
    }

    @Test
    void delegatesStateEnvAndStreamWriters() {
        TestSession inner = new TestSession();
        inner.config.setEnvs(Map.of("region", "cn"));
        NodeSessionApi session = new NodeSessionApi(inner);

        session.updateState(Map.of("local", "value"));
        session.updateGlobalState(Map.of("global", "value"));
        session.writeStream(Map.of("type", "message", "index", 3, "payload", "hello"));

        assertEquals("value", session.getState("local"));
        assertEquals("value", session.getGlobalState("global"));
        assertEquals("cn", session.getEnv("region"));
        assertSame(inner, session.getInner());

        Object emitted = inner.streamWriterManager.streamEmitter().getStreamQueue().receive(100);
        OutputSchema output = assertInstanceOf(OutputSchema.class, emitted);
        assertEquals("message", output.getType());
        assertEquals(3, output.getIndex());
        assertEquals("hello", output.getPayload());
    }

    @Test
    void interactRaisesPythonStatusErrorWhenStreaming() {
        NodeSession inner = new NodeSession(new WorkflowSession("workflow-a"), "node-a", "llm");
        NodeSessionApi session = new NodeSessionApi(inner, true);

        BaseError error = assertThrows(BaseError.class, () -> session.interact("question"));

        assertEquals(StatusCode.COMP_SESSION_INTERACT_ERROR, error.getStatus());
        assertEquals("node-a", error.getParams().get("comp_id"));
        assertEquals("workflow-a", error.getParams().get("workflow"));
        assertEquals("interact when streaming process(transform or collect) is not supported",
                error.getParams().get("reason"));
    }

    private static final class TestSession extends BaseSession {
        private final Config config = new Config();
        private final AgentStateCollection state = new AgentStateCollection();
        private final StreamWriterManager streamWriterManager = new StreamWriterManager(new StreamEmitter());

        @Override
        public Config config() {
            return config;
        }

        @Override
        public AgentStateCollection state() {
            return state;
        }

        @Override
        public StreamWriterManager streamWriterManager() {
            return streamWriterManager;
        }

        @Override
        public String sessionId() {
            return "session-a";
        }

        public String workflowId() {
            return "workflow-a";
        }

        public String nodeId() {
            return "node-a";
        }

        public String nodeType() {
            return "llm";
        }

        public String executableId() {
            return "node-a";
        }

        public boolean skipTrace() {
            return false;
        }
    }
}
