/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm.react;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused parity tests for the ReAct workflow executable.
 *
 * <p>Mirrors Python's {@code ReActAgentCompExecutable} in
 * {@code openjiuwen/core/workflow/components/llm/react/react_executable.py}.</p>
 */
class ReActAgentCompExecutableTest {

    @Test
    void constructorCreatesWorkflowSpecificAgentAndConfiguresIt() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();
        ReActAgentCompExecutable executable = new ReActAgentCompExecutable(config);

        AgentCard card = executable.getReactAgent().getCard();
        assertSame(config, executable.getConfig());
        assertSame(config, executable.getReactAgent().getConfig());
        assertEquals("react_agent_workflow_executable", card.getId());
        assertEquals("ReAct Agent Workflow Executable", card.getName());
        assertEquals("ReAct agent for workflow execution", card.getDescription());
        assertSame(executable.getReactAgent().getAbilityManager(), executable.getAbilityManager());
    }

    @Test
    void invokeReturnsAgentResultAndAdaptsWorkflowSession() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();
        FakeReActAgent agent = new FakeReActAgent();
        Map<String, Object> expected = new LinkedHashMap<>(Map.of("output", "done"));
        agent.invokeResult = expected;
        ReActAgentCompExecutable executable = new ReActAgentCompExecutable(config, agent);

        Object result = executable.invoke("question", new TestSession("workflow-1"), null);

        assertSame(expected, result);
        assertSame(config, agent.configuredConfig);
        assertEquals("question", agent.invokeInputs);
        assertEquals("workflow-1", agent.invokeSession.getSessionId());
    }

    @Test
    void invokeConvertsAgentExceptionToPythonErrorShape() {
        FakeReActAgent agent = new FakeReActAgent();
        agent.invokeException = new IllegalStateException("boom");
        ReActAgentCompExecutable executable = new ReActAgentCompExecutable(new ReActAgentCompConfig(), agent);

        Object result = executable.invoke("question", new TestSession("workflow-2"), null);

        assertEquals(Map.of(
                "output", "Error in ReAct execution: boom",
                "result_type", "error"
        ), result);
    }

    @Test
    void streamNormalizesOutputSchemaChunksAndLeavesRawChunksUnchanged() {
        FakeReActAgent agent = new FakeReActAgent();
        agent.streamChunks = List.of(
                new OutputSchema("llm_output", 0, Map.of("content", "token", "result_type", "answer")),
                new OutputSchema("custom", 0, Map.of("value", 7)),
                "raw"
        );
        ReActAgentCompExecutable executable = new ReActAgentCompExecutable(new ReActAgentCompConfig(), agent);

        Iterator<Object> iterator = executable.stream("prompt", new TestSession("workflow-stream"), null);

        assertEquals(Map.of("output", "token"), iterator.next());
        assertEquals(Map.of("value", 7), iterator.next());
        assertEquals("raw", iterator.next());
        assertFalse(iterator.hasNext());
        assertEquals("prompt", agent.streamInputs);
        assertEquals("workflow-stream", agent.streamSession.getSessionId());
        assertEquals(List.of(StreamMode.OUTPUT), agent.streamModes);
    }

    @Test
    void streamConvertsStreamExceptionToPythonErrorShape() {
        FakeReActAgent agent = new FakeReActAgent();
        agent.streamException = new IllegalStateException("stream boom");
        ReActAgentCompExecutable executable = new ReActAgentCompExecutable(new ReActAgentCompConfig(), agent);

        Object result = executable.stream("prompt", new TestSession("workflow-stream"), null).next();

        assertEquals(Map.of(
                "type", "error",
                "payload", Map.of(
                        "output", "Error in ReAct streaming: stream boom",
                        "result_type", "error"
                )
        ), result);
    }

    private static final class FakeReActAgent extends ReActAgent {
        private ReActAgentConfig configuredConfig;
        private Object invokeInputs;
        private AgentSessionApi invokeSession;
        private Object invokeResult;
        private RuntimeException invokeException;
        private Object streamInputs;
        private AgentSessionApi streamSession;
        private List<StreamMode> streamModes;
        private List<Object> streamChunks = List.of();
        private RuntimeException streamException;

        private FakeReActAgent() {
            super(new AgentCard("fake", "Fake", "Fake agent"));
        }

        @Override
        public ReActAgent configure(ReActAgentConfig newConfig) {
            configuredConfig = newConfig;
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            invokeInputs = inputs;
            invokeSession = session;
            if (invokeException != null) {
                return CompletableFuture.failedFuture(invokeException);
            }
            return CompletableFuture.completedFuture(invokeResult);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            streamInputs = inputs;
            streamSession = session;
            this.streamModes = new ArrayList<>(streamModes);
            if (streamException != null) {
                throw streamException;
            }
            return streamChunks.iterator();
        }
    }

    private static final class TestSession extends BaseSession {
        private final String sessionId;

        private TestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }
    }
}
