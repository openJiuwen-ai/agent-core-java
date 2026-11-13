/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests that {@link ReActAgent} records the actually used model name and the
 * framework model-pool key into {@link ModelCallInputs} before
 * BEFORE_MODEL_CALL fires, for both the non-streaming and streaming paths.
 *
 * @since 0.1.16
 */
@DisplayName("ReActAgent ModelCallInputs model reporting")
class ReActAgentModelCallInputsTest {

    private static final class TestSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        private TestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    private static Model mockModel(String modelName) throws Exception {
        Model model = mock(Model.class);
        when(model.getModelConfig()).thenReturn(ModelRequestConfig.builder().modelName(modelName).build());
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssistantMessage("ok"));
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(AssistantMessageChunk.builder().content("ok").build()).iterator());
        return model;
    }

    private static ReActAgent newAgent(Model model) {
        AgentCard card = AgentCard.builder()
                .id("mci-agent-" + UUID.randomUUID())
                .name("mci-agent")
                .description("ModelCallInputs reporting test agent")
                .build();
        return new ReActAgent(card) {
            @Override
            protected Model getLlm(AgentCallbackContext ctx) {
                return model;
            }
        };
    }

    private static AtomicReference<ModelCallInputs> captureModelInputs(ReActAgent agent) {
        AtomicReference<ModelCallInputs> captured = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL,
                callbackCtx -> captured.set((ModelCallInputs) callbackCtx.getInputs()), 100);
        return captured;
    }

    @Test
    @DisplayName("invoke reports the dynamic model name and pool key")
    void invokeReportsDynamicModel() throws Exception {
        ReActAgent agent = newAgent(mockModel("model-b"));
        AtomicReference<ModelCallInputs> captured = captureModelInputs(agent);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                callbackCtx -> callbackCtx.setDynamicModelId("pkg-b"), 100);

        agent.invoke(Map.of("query", "hi"), new TestSession("mci-invoke-dynamic"));

        ModelCallInputs inputs = captured.get();
        assertThat(inputs).isNotNull();
        assertThat(inputs.getModelName()).isEqualTo("model-b");
        assertThat(inputs.getModelId()).isEqualTo("pkg-b");
        agent.getAgentCallbackManager().clear(null);
    }

    @Test
    @DisplayName("invoke without a dynamic model reports the default model and a null pool key")
    void invokeReportsDefaultModel() throws Exception {
        ReActAgent agent = newAgent(mockModel("model-a"));
        AtomicReference<ModelCallInputs> captured = captureModelInputs(agent);

        agent.invoke(Map.of("query", "hi"), new TestSession("mci-invoke-default"));

        ModelCallInputs inputs = captured.get();
        assertThat(inputs).isNotNull();
        assertThat(inputs.getModelName()).isEqualTo("model-a");
        assertThat(inputs.getModelId()).isNull();
        agent.getAgentCallbackManager().clear(null);
    }

    @Test
    @DisplayName("stream reports the dynamic model name and pool key")
    void streamReportsDynamicModel() throws Exception {
        ReActAgent agent = newAgent(mockModel("model-b"));
        AtomicReference<ModelCallInputs> captured = captureModelInputs(agent);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                callbackCtx -> callbackCtx.setDynamicModelId("pkg-b"), 100);
        AgentSessionApi session = new AgentSessionApi("mci-stream-dynamic", null, agent.getCard(),
                List.of(StreamMode.OUTPUT));

        agent.stream(Map.of("query", "hi"), session, List.of(StreamMode.OUTPUT)).forEachRemaining(ignored -> {
        });

        ModelCallInputs inputs = captured.get();
        assertThat(inputs).isNotNull();
        assertThat(inputs.getModelName()).isEqualTo("model-b");
        assertThat(inputs.getModelId()).isEqualTo("pkg-b");
        agent.getAgentCallbackManager().clear(null);
    }
}
