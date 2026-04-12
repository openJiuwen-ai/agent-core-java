/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReActAgentSharedLoopStructureTest {

    @Test
    void invokeShouldBindConversationIdToRuntimeSessionWhenNoExplicitSession() throws Exception {
        ProbeReActAgent agent = new ProbeReActAgent(mockSuccessfulModel("会", "话"));
        AtomicReference<Session> capturedSession = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> capturedSession.set(ctx.getSession()), 10);

        agent.invoke(Map.of("query", "会话", "conversation_id", "phase15-invoke-conversation"), null);

        assertThat(capturedSession.get()).isNotNull();
        assertThat(capturedSession.get().getSessionId()).isEqualTo("phase15-invoke-conversation");
    }

    @Test
    void streamShouldBindConversationIdToRuntimeSessionWhenNoExplicitSession() throws Exception {
        ProbeReActAgent agent = new ProbeReActAgent(mockSuccessfulModel("会", "话"));
        AtomicReference<Session> capturedSession = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> capturedSession.set(ctx.getSession()), 10);

        collect(agent.stream(
                Map.of("query", "会话", "conversation_id", "phase15-stream-conversation"),
                null,
                List.of(StreamMode.OUTPUT)
        ));

        assertThat(capturedSession.get()).isNotNull();
        assertThat(capturedSession.get().getSessionId()).isEqualTo("phase15-stream-conversation");
    }

    @Test
    void streamShouldUseDedicatedStreamingPathWithoutCallingInvoke() throws Exception {
        StreamOnlyProbeAgent agent = new StreamOnlyProbeAgent(mockSuccessfulModel("直", "播"));
        AgentSessionApi session = AgentSessionApi.create("phase15-shared-loop-stream", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "直播"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("llm_output", "llm_output", "answer");
        assertThat(payload(outputs.get(0))).containsEntry("output", "直");
        assertThat(payload(outputs.get(1))).containsEntry("output", "播");
        assertThat(payload(outputs.get(2)))
                .containsEntry("result_type", "answer")
                .containsEntry("status", "completed");
    }

    @Test
    void invokeAndStreamShouldShareSuccessfulTerminalSemantics() throws Exception {
        ProbeReActAgent agent = new ProbeReActAgent(mockSuccessfulModel("共", "享"));

        @SuppressWarnings("unchecked")
        Map<String, Object> invokeResult = (Map<String, Object>) agent.invoke(Map.of("query", "共享"), null);
        AgentSessionApi streamSession = AgentSessionApi.create("phase15-shared-loop-success", null, agent.getCard());
        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "共享"), streamSession, List.of(StreamMode.OUTPUT)));

        assertThat(invokeResult)
                .containsEntry("output", "共享")
                .containsEntry("result_type", "answer");
        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("llm_output", "llm_output", "answer");
        assertThat(payload(outputs.get(2)))
                .containsEntry("result_type", "answer")
                .containsEntry("status", "completed");
    }

    @Test
    void invokeAndStreamShouldShareFailureTerminalSemantics() throws Exception {
        ProbeReActAgent agent = new ProbeReActAgent(mockFailureModel(new RuntimeException("shared loop boom")));

        @SuppressWarnings("unchecked")
        Map<String, Object> invokeResult = (Map<String, Object>) agent.invoke(Map.of("query", "失败"), null);
        AgentSessionApi streamSession = AgentSessionApi.create("phase15-shared-loop-failure", null, agent.getCard());
        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "失败"), streamSession, List.of(StreamMode.OUTPUT)));

        assertThat(invokeResult)
                .containsEntry("output", "shared loop boom")
                .containsEntry("result_type", "error");
        assertThat(outputs).singleElement().satisfies(output -> {
            assertThat(output.getType()).isEqualTo("final");
            assertThat(payload(output))
                    .containsEntry("error", true)
                    .containsEntry("message", "shared loop boom")
                    .containsEntry("status", "failed");
        });
    }

    private static List<OutputSchema> collect(Iterator<Object> iterator) {
        List<OutputSchema> results = new ArrayList<>();
        while (iterator.hasNext()) {
            Object next = iterator.next();
            assertThat(next).isInstanceOf(OutputSchema.class);
            results.add((OutputSchema) next);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema output) {
        return (Map<String, Object>) output.getPayload();
    }

    private static Model mockSuccessfulModel(String... chunks) throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content(String.join("", chunks)).build());
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenReturn(successfulChunkStream(chunks));
        return model;
    }

    private static Iterator<AssistantMessageChunk> successfulChunkStream(String... chunks) {
        List<AssistantMessageChunk> stream = new ArrayList<>();
        for (String chunk : chunks) {
            stream.add(AssistantMessageChunk.builder().content(chunk).build());
        }
        stream.add(AssistantMessageChunk.builder().content("").finishReason("stop").build());
        return stream.iterator();
    }

    private static Model mockFailureModel(RuntimeException failure) throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(failure);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenThrow(failure);
        return model;
    }

    private static class ProbeReActAgent extends ReActAgent {
        private final Model model;

        private ProbeReActAgent(Model model) {
            super(AgentCard.builder()
                    .id("shared-loop-probe-agent")
                    .name("shared-loop-probe-agent")
                    .description("shared loop probe agent")
                    .build());
            this.model = model;
        }

        @Override
        protected Model getLlm() {
            return model;
        }
    }

    private static final class StreamOnlyProbeAgent extends ProbeReActAgent {
        private StreamOnlyProbeAgent(Model model) {
            super(model);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            throw new AssertionError("stream() must not call invoke()");
        }
    }
}
