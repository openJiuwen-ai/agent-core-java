/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Focused tests for the public single-agent session facade.
 *
 * <p>Mirrors Python's {@code Session} in
 * {@code openjiuwen/core/session/agent.py}.</p>
 */
class AgentSessionTest {

    @AfterEach
    void resetGlobals() {
        CallbackUtils.resetFrameworkSupplier();
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    void preRunTriggersAgentSessionCreatedBeforeCheckpointAndIsIdempotent() {
        RecordingFramework framework = new RecordingFramework();
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();
        CallbackUtils.setCallbackFramework(framework);
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        AgentSession session = new AgentSession("session-pre", Map.of("env", "prod"), new TestCard("agent-1"));
        Map<String, Object> inputs = Map.of("prompt", "hello");

        session.preRun(Map.of("inputs", inputs));
        session.preRun(Map.of("inputs", Map.of("prompt", "ignored")));

        assertEquals(1, framework.calls.size());
        RecordedCall call = framework.calls.get(0);
        assertEquals(SessionEvents.AGENT_SESSION_CREATED, call.event());
        assertEquals("session-pre", call.kwargs().get("session_id"));
        assertSame(session, call.kwargs().get("session"));
        assertEquals("agent-1", ((TestCard) call.kwargs().get("card")).getId());
        assertEquals(1, checkpointer.preCalls);
        assertSame(inputs, checkpointer.lastInputs);
        assertEquals("session-pre", checkpointer.lastSessionId);
    }

    @Test
    void writeStreamTriggersCallbacksAndNormalizesMapPayloadWithSourceMetadata() {
        RecordingFramework framework = new RecordingFramework();
        CallbackUtils.setCallbackFramework(framework);
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        AgentSession session = new AgentSession(
                "session-stream",
                null,
                null,
                manager,
                true,
                Map.of("source", "agent")
        );
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message", "hello");

        session.writeStream(message);

        Object emitted = emitter.getStreamQueue().receive(100);
        assertNotNull(emitted);
        OutputSchema output = assertInstanceOf(OutputSchema.class, emitted);
        assertEquals("message", output.getType());
        assertEquals(0, output.getIndex());
        Map<?, ?> payload = assertInstanceOf(Map.class, output.getPayload());
        assertEquals("hello", payload.get("message"));
        assertEquals("agent", payload.get("source"));
        assertEquals("session-streamwrite_stream", framework.calls.get(0).event());
        assertSame(output, framework.calls.get(0).kwargs().get("data"));
    }

    @Test
    void writeStreamMergesSourceMetadataIntoOutputSchemaPayload() {
        RecordingFramework framework = new RecordingFramework();
        CallbackUtils.setCallbackFramework(framework);
        StreamEmitter emitter = new StreamEmitter();
        StreamWriterManager manager = new StreamWriterManager(emitter);
        AgentSession session = new AgentSession(
                "session-schema",
                null,
                null,
                manager,
                true,
                Map.of("source", "agent")
        );

        session.writeStream(new OutputSchema("delta", 2, "token"));

        Object emitted = emitter.getStreamQueue().receive(100);
        assertNotNull(emitted);
        OutputSchema output = assertInstanceOf(OutputSchema.class, emitted);
        assertEquals("delta", output.getType());
        assertEquals(2, output.getIndex());
        Map<?, ?> payload = assertInstanceOf(Map.class, output.getPayload());
        assertEquals("token", payload.get("value"));
        assertEquals("agent", payload.get("source"));
        assertSame(output, framework.calls.get(0).kwargs().get("data"));
    }

    @Test
    void closeStreamUnregistersSessionWriteStreamCallbacks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework(false, false);
        CallbackUtils.setCallbackFramework(framework);
        framework.registerSync(
                "session-closewrite_stream",
                kwargs -> null,
                0,
                false,
                "test",
                Set.of(),
                List.of(),
                null,
                null,
                0,
                0.0,
                null,
                ""
        );
        StreamEmitter emitter = new StreamEmitter();
        AgentSession session = new AgentSession(
                "session-close",
                null,
                null,
                new StreamWriterManager(emitter),
                true,
                null
        );

        session.closeStream();

        assertFalse(framework.getCallbacks().containsKey("session-closewrite_stream"));
        assertEquals(StreamEmitter.END_FRAME, emitter.getStreamQueue().receive(100));
    }

    @Test
    void commitPersistsStateWithoutClosingStream() {
        RecordingCheckpointer checkpointer = new RecordingCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        StreamEmitter emitter = new StreamEmitter();
        AgentSession session = new AgentSession(
                "session-commit",
                null,
                null,
                new StreamWriterManager(emitter),
                true,
                null
        );

        session.commit();

        assertEquals(1, checkpointer.postCalls);
        assertEquals("session-commit", checkpointer.lastSessionId);
        assertFalse(emitter.isClosed());
    }

    private record RecordedCall(String event, Object[] args, Map<String, Object> kwargs) {
    }

    /**
     * Mirrors Python's callback framework collaborator in
     * {@code openjiuwen/core/runner/callback/framework.py}.
     */
    private static final class RecordingFramework implements DecoratorFramework {
        private final List<RecordedCall> calls = new ArrayList<>();

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
            calls.add(new RecordedCall(event, args.clone(), new LinkedHashMap<>(kwargs)));
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            trigger(event, args, kwargs);
            return null;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }
    }

    private static final class RecordingCheckpointer extends Checkpointer {
        private int preCalls;
        private int postCalls;
        private Object lastInputs;
        private String lastSessionId;

        @Override
        public void preAgentExecute(BaseSession session, Object inputs) {
            preCalls++;
            lastInputs = inputs;
            lastSessionId = session.sessionId();
        }

        @Override
        public void postAgentExecute(BaseSession session) {
            postCalls++;
            lastSessionId = session.sessionId();
        }
    }

    /**
     * Mirrors the card object consumed by Python's {@code Session.get_agent_id()} in
     * {@code openjiuwen/core/session/agent.py}.
     */
    private static final class TestCard {
        private final String id;

        private TestCard(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
