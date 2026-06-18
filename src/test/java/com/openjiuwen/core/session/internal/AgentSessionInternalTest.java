/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused tests for the internal single-agent session.
 *
 * <p>Mirrors Python's {@code AgentSession} in
 * {@code openjiuwen/core/session/internal/agent.py}.</p>
 */
class AgentSessionInternalTest {

    @AfterEach
    void resetCheckpointerFactory() {
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    void constructorRetainsExplicitCheckpointerAndCreatesDefaultCollaborators() {
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();

        AgentSession session = new AgentSession(
                "session-explicit",
                new Config(),
                checkpointer,
                new TestCard("card-agent"),
                null
        );

        assertSame(checkpointer, session.checkpointer());
        assertInstanceOf(AgentStateCollection.class, session.state());
        assertNotNull(session.streamWriterManager());
        assertNotNull(session.tracer());
        assertNotNull(session.span());
        assertEquals("session-explicit", session.sessionId());
    }

    @Test
    void constructorCachesFactoryCheckpointerAtCreationTime() {
        RecordingCheckpointer first = new RecordingCheckpointer();
        RecordingCheckpointer second = new RecordingCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(first);

        AgentSession session = new AgentSession("session-default", new Config(), new TestCard("card-agent"), null);
        CheckpointerFactory.setDefaultCheckpointer(second);

        assertSame(first, session.checkpointer());
    }

    @Test
    void agentIdPrefersConfigAgentIdThenFallsBackToCardId() {
        Config config = new Config();
        config.setAgentConfig(new AgentConfig("config-agent"));

        AgentSession configured = new AgentSession("session-config", config, new TestCard("card-agent"), null);
        AgentSession cardOnly = new AgentSession("session-card", new Config(), new TestCard("card-agent"), null);

        assertEquals("config-agent", configured.agentId());
        assertEquals("card-agent", cardOnly.agentId());
    }

    @Test
    void agentIdReturnsNullWhenAgentConfigExistsWithNullId() {
        Config config = new Config();
        config.setAgentConfig(new AgentConfig(null));

        AgentSession session = new AgentSession("session-null", config, new TestCard("card-agent"), null);

        assertNull(session.agentId());
    }

    @Test
    void createWorkflowSessionInheritsParentAndSharesGlobalState() {
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();
        Config config = new Config();
        AgentSession session = new AgentSession(
                "session-workflow",
                config,
                checkpointer,
                new TestCard("card-agent"),
                null
        );
        session.state().updateGlobal(Map.of("before", "agent-value"));

        WorkflowSession workflow = session.createWorkflowSession();
        WorkflowCommitState workflowState = assertInstanceOf(WorkflowCommitState.class, workflow.state());

        assertSame(session, workflow.parent());
        assertEquals("session-workflow", workflow.sessionId());
        assertSame(config, workflow.config());
        assertSame(session.tracer(), workflow.tracer());
        assertSame(checkpointer, workflow.checkpointer());
        assertEquals("agent-value", workflowState.getGlobal("before"));

        session.state().updateGlobal(Map.of("after", "agent-later"));
        assertEquals("agent-later", workflowState.getGlobal("after"));

        workflowState.updateGlobal(Map.of("fromWorkflow", "workflow-value"));
        workflowState.commit();
        assertEquals("workflow-value", session.state().getGlobal("fromWorkflow"));
    }

    private record AgentConfig(String id) {
    }

    private record TestCard(String id) {
        public String getId() {
            return id;
        }
    }

    private static final class RecordingCheckpointer extends Checkpointer {
        @Override
        public void preAgentExecute(BaseSession session, Object inputs) {
            // No-op test checkpointer.
        }
    }
}
