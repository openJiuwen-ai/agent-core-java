/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.deep_agent.DeepAgentSession;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Request environments must survive DeepAgent session construction and execution boundaries.
 */
@Timeout(30)
class DeepAgentSessionEnvironmentTest {
    private static final AgentCard CARD = new AgentCard("environment-test", "environment-test", "environment test");
    private static final String REQUEST_CONTEXT = "request-context";
    private static final String RESPONSE_COLLECTOR = "response-collector";

    @ParameterizedTest
    @ValueSource(strings = {"default", "stream-modes", "checkpointer"})
    void constructorsRetainEnvironmentsInTheFacadeAndKernel(String constructor) {
        Map<String, Object> envs = Map.of(REQUEST_CONTEXT, "caller");
        DeepAgentSession session = createSession(constructor, envs);

        assertThat(session.getEnvs()).containsEntry(REQUEST_CONTEXT, "caller");
        assertThat(session.getInner().config().getEnv(REQUEST_CONTEXT)).isEqualTo("caller");
    }

    @ParameterizedTest
    @CsvSource({"default,integer", "stream-modes,integer", "checkpointer,integer",
            "default,null", "stream-modes,null", "checkpointer,null"})
    void constructorsRejectInvalidEnvironmentKeys(String constructor, String keyType) {
        Map<Object, Object> envs = new LinkedHashMap<>();
        envs.put(REQUEST_CONTEXT, "caller");
        Object invalidKey = "integer".equals(keyType) ? Integer.valueOf(1) : null;
        envs.put(invalidKey, "invalid");

        assertThatIllegalArgumentException().isThrownBy(() -> createSession(constructor, envs))
                .withMessage("Environment keys must be strings");
    }

    private static DeepAgentSession createSession(String constructor, Object config) {
        String sessionId = UUID.randomUUID().toString();
        return switch (constructor) {
            case "default" -> new DeepAgentSession(sessionId, config, CARD);
            case "stream-modes" -> new DeepAgentSession(sessionId, config, CARD, List.of(StreamMode.OUTPUT));
            default -> new DeepAgentSession(sessionId, config, null, CARD);
        };
    }

    @Test
    void environmentUpdatesUseTheKernelAsTheSingleSource() {
        DeepAgentSession session = new DeepAgentSession(UUID.randomUUID().toString(),
                Map.of(REQUEST_CONTEXT, "initial", "retained", true), CARD);

        session.setEnvs(Map.of(REQUEST_CONTEXT, "updated"));
        assertThat(session.getInner().config().getEnv(REQUEST_CONTEXT)).isEqualTo("updated");
        assertThat(session.getEnvs()).containsEntry("retained", true);

        session.getInner().config().setEnvs(Map.of(REQUEST_CONTEXT, "kernel"));
        assertThat(session.getEnvs()).containsEntry(REQUEST_CONTEXT, "kernel");
        session.setEnvs(null);
        assertThat(session.getEnvs()).containsEntry(REQUEST_CONTEXT, "kernel");
    }

    @Test
    void environmentSnapshotsAndOtherSessionsRemainIsolated() {
        DeepAgentSession first = new DeepAgentSession(UUID.randomUUID().toString(),
                Map.of(REQUEST_CONTEXT, "first"), CARD);
        DeepAgentSession second = new DeepAgentSession(UUID.randomUUID().toString(),
                Map.of(REQUEST_CONTEXT, "second"), CARD);

        first.getEnvs().put(REQUEST_CONTEXT, "snapshot-only");
        assertThat(first.getEnvs()).containsEntry(REQUEST_CONTEXT, "first");
        first.setEnvs(Map.of(REQUEST_CONTEXT, "updated"));
        assertThat(second.getEnvs()).containsEntry(REQUEST_CONTEXT, "second");
    }

    @Test
    void nullAndLegacyConfigurationKeepDefaultEnvironments() {
        DeepAgentSession session = new DeepAgentSession(UUID.randomUUID().toString());
        DeepAgentSession legacy = new DeepAgentSession(UUID.randomUUID().toString(), new Object(), CARD);

        assertThat(session.getEnvs()).isNotEmpty();
        assertThat(legacy.getEnvs()).containsAllEntriesOf(session.getEnvs());
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "true,true,false",
            "false,false,true", "true,false,true", "false,true,true", "true,true,true"})
    void executionPassesCallerEnvironmentsToInnerSessions(boolean isStreaming, boolean isResuming,
            boolean isThroughRunner) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        AtomicInteger collector = new AtomicInteger();
        AgentSession caller = new AgentSession(sessionId,
                Map.of(REQUEST_CONTEXT, "caller", RESPONSE_COLLECTOR, collector), CARD);
        EnvironmentRecordingAgent inner = new EnvironmentRecordingAgent();
        DeepAgent agent = new DeepAgent(CARD, DeepAgentConfig.builder().enableTaskLoop(true).build(), null);
        agent.setReactAgent(inner, true);
        Object query = "check request context";
        if (isResuming) {
            InteractiveInput resume = new InteractiveInput();
            resume.update("confirmation", "continue");
            query = resume;
        }
        Map<String, Object> inputs = Map.of("query", query, "conversation_id", sessionId);
        try {
            if (isStreaming) {
                Iterator<Object> stream = isThroughRunner
                        ? Runner.runAgentStreaming(agent, inputs, caller, null, List.of(StreamMode.OUTPUT))
                        : agent.stream(inputs, caller, List.of(StreamMode.OUTPUT));
                try {
                    while (stream.hasNext()) {
                        stream.next();
                    }
                } finally {
                    if (stream instanceof AutoCloseable closeable) {
                        closeable.close();
                    }
                }
            } else {
                if (isThroughRunner) {
                    Runner.runAgent(agent, inputs, caller, null);
                } else {
                    agent.invoke(inputs, caller);
                }
            }

            assertThat(inner.observedEnvs).containsEntry(REQUEST_CONTEXT, "caller");
            assertThat(inner.observedKernelEnvs).containsEntry(REQUEST_CONTEXT, "caller");
            assertThat(caller.getEnvs()).containsEntry(REQUEST_CONTEXT, "caller");
            assertThat(inner.observedEnvs.get(RESPONSE_COLLECTOR)).isSameAs(collector);
            assertThat(inner.observedKernelEnvs.get(RESPONSE_COLLECTOR)).isSameAs(collector);
            assertThat(collector.get()).isEqualTo(1);
        } finally {
            agent.shutdown();
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    private static final class EnvironmentRecordingAgent extends ReActAgent {
        private volatile Map<String, Object> observedEnvs;
        private volatile Map<String, Object> observedKernelEnvs;

        private EnvironmentRecordingAgent() {
            super(CARD);
        }

        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            recordEnvironments(session);
            return Map.of("output", "done", "result_type", "answer");
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            recordEnvironments(session);
            return List.<Object>of(new OutputSchema("answer", 0,
                    Map.of("output", "done", "result_type", "answer"))).iterator();
        }

        private void recordEnvironments(AgentSessionApi session) {
            observedEnvs = session.getEnvs();
            if (session instanceof DeepAgentSession deep) {
                observedKernelEnvs = deep.getInner().config().getEnvs();
            } else if (session instanceof AgentSession facade) {
                observedKernelEnvs = facade.getInner().config().getEnvs();
            } else {
                throw new AssertionError("Expected a native agent session");
            }
            Object collector = observedEnvs.get(RESPONSE_COLLECTOR);
            if (collector instanceof AtomicInteger counter) {
                counter.incrementAndGet();
            }
        }
    }
}
