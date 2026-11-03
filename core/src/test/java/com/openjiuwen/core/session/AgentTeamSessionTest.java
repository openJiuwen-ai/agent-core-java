/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Focused tests for the public agent-team session facade.
 *
 * <p>Mirrors Python's {@code Session} in
 * {@code openjiuwen/core/session/agent_team.py}.</p>
 */
class AgentTeamSessionTest {

    @AfterEach
    void resetGlobals() {
        CallbackUtils.resetFrameworkSupplier();
        CheckpointerFactory.releaseDefaultCheckpointer();
    }

    @Test
    void writeStreamNormalizesMapPayloadAndTagsTeamSource() {
        AgentTeamSession session = new AgentTeamSession("team-session", null, "team-a");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", "team");

        session.writeStream(data);

        Object emitted = session.getInner().streamWriterManager().streamEmitter().getStreamQueue().receive(100);
        assertNotNull(emitted);
        OutputSchema output = assertInstanceOf(OutputSchema.class, emitted);
        assertEquals("message", output.getType());
        assertEquals(0, output.getIndex());
        Map<?, ?> payload = assertInstanceOf(Map.class, output.getPayload());
        assertEquals("team", payload.get("kind"));
        assertEquals("team-a", payload.get("source_team_id"));
    }

    @Test
    void writeStreamMergesTeamSourceIntoOutputSchemaPayload() {
        AgentTeamSession session = new AgentTeamSession("team-session", null, "team-a");

        session.writeStream(new OutputSchema("delta", 4, "token"));

        Object emitted = session.getInner().streamWriterManager().streamEmitter().getStreamQueue().receive(100);
        assertNotNull(emitted);
        OutputSchema output = assertInstanceOf(OutputSchema.class, emitted);
        assertEquals("delta", output.getType());
        assertEquals(4, output.getIndex());
        Map<?, ?> payload = assertInstanceOf(Map.class, output.getPayload());
        assertEquals("token", payload.get("value"));
        assertEquals("team-a", payload.get("source_team_id"));
    }

    @Test
    void createAgentSessionSharesStreamWriterByDefaultAndCanOptOut() {
        CallbackUtils.setCallbackFramework(new RecordingFramework());
        AgentTeamSession teamSession = new AgentTeamSession("team-session", Map.of("env", "prod"), "team-a");
        AgentSession shared = teamSession.createAgentSession(new TestCard("agent-a"), null);
        AgentSession isolated = teamSession.createAgentSession(new TestCard("agent-b"), null, false);

        shared.writeStream(Map.of("kind", "agent"));
        isolated.writeStream(Map.of("kind", "isolated"));

        Object sharedEmitted = teamSession.getInner().streamWriterManager().streamEmitter().getStreamQueue()
                .receive(100);
        assertNotNull(sharedEmitted);
        OutputSchema output = assertInstanceOf(OutputSchema.class, sharedEmitted);
        Map<?, ?> payload = assertInstanceOf(Map.class, output.getPayload());
        assertEquals("agent", payload.get("kind"));
        assertEquals("agent-a", payload.get("source_agent_id"));
        assertEquals("team-a", payload.get("source_team_id"));

        assertNull(teamSession.getInner().streamWriterManager().streamEmitter().getStreamQueue().receive(100));
        assertEquals("prod", shared.getEnv("env"));
        assertEquals("prod", isolated.getEnv("env"));
    }

    @Test
    void preRunIsIdempotentAndCommitFlushPersistWithoutClosingStream() {
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();
        installDefaultCheckpointer("unit-agent-team-session-commit", checkpointer);
        AgentTeamSession session = new AgentTeamSession("team-session", null, "team-a");
        Map<String, Object> inputs = Map.of("query", "hello");

        session.preRun(Map.of("inputs", inputs));
        session.preRun(Map.of("inputs", Map.of("ignored", true)));
        session.commit();
        session.flushCheckpoint();

        assertEquals(1, checkpointer.preTeamCalls);
        assertEquals(2, checkpointer.postTeamCalls);
        assertEquals(inputs, checkpointer.lastInputs);
        assertEquals("team-session", checkpointer.lastSessionId);
        assertFalse(session.getInner().streamWriterManager().streamEmitter().isClosed());
    }

    private static void installDefaultCheckpointer(String name, Checkpointer checkpointer) {
        CheckpointerFactory.register(name, conf -> checkpointer);
        CheckpointerFactory.installDefaultCheckpointer(new CheckpointerConfig(name, Map.of()));
    }

    /**
     * Mirrors Python's callback framework collaborator needed by child
     * {@code AgentSession.write_stream()} in {@code openjiuwen/core/session/agent.py}.
     */
    private static final class RecordingFramework implements DecoratorFramework {
        @Override
        public CallbackInfo registerSync(String event,
                                         Function<Map<String, Object>, Object> callback,
                                         int priority,
                                         boolean once,
                                         String namespace,
                                         Set<String> tags,
                                         List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler,
                                         int maxRetries,
                                         double retryDelay,
                                         Double timeout,
                                         String callbackType) {
            return CallbackInfo.builder()
                    .callback(callback)
                    .priority(priority)
                    .once(once)
                    .namespace(namespace)
                    .tags(tags)
                    .maxRetries(maxRetries)
                    .retryDelay(retryDelay)
                    .timeout(timeout)
                    .callbackType(callbackType)
                    .build();
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }
    }

    private static final class RecordingCheckpointer extends Checkpointer {
        private int preTeamCalls;
        private int postTeamCalls;
        private Object lastInputs;
        private String lastSessionId;

        @Override
        public void preAgentTeamExecute(BaseSession session, Object inputs) {
            preTeamCalls++;
            lastInputs = inputs;
            lastSessionId = session.sessionId();
        }

        @Override
        public void postAgentTeamExecute(BaseSession session) {
            postTeamCalls++;
            lastSessionId = session.sessionId();
        }
    }

    /**
     * Mirrors Python's {@code AgentCard} fields used by
     * {@code openjiuwen/core/session/agent_team.py}.
     */
    private static final class TestCard {
        private final String id;

        private TestCard(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return id;
        }
    }
}
