/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static Model newModel(String modelName) {
        StubModelClient client = new StubModelClient();
        return new Model(client, null, ModelRequestConfig.builder().modelName(modelName).build());
    }

    private static ReActAgent newAgent(Model model) {
        ReActAgent agent = new ReActAgent(new AgentCard(
                "mci-agent-" + UUID.randomUUID(), "mci-agent", "ModelCallInputs reporting test agent"));
        agent.setLlm(model);
        return agent;
    }

    private static AtomicReference<ModelCallInputs> captureModelInputs(ReActAgent agent) {
        AtomicReference<ModelCallInputs> captured = new AtomicReference<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL,
                callbackCtx -> {
                    captured.set((ModelCallInputs) callbackCtx.getInputs());
                    return CompletableFuture.completedFuture(null);
                }, 100).toCompletableFuture().join();
        return captured;
    }

    @Test
    @DisplayName("invoke reports the dynamic model name and pool key")
    void invokeReportsDynamicModel() {
        ReActAgent agent = newAgent(newModel("model-b"));
        AtomicReference<ModelCallInputs> captured = captureModelInputs(agent);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                callbackCtx -> {
                    callbackCtx.setDynamicModelId("pkg-b");
                    return CompletableFuture.completedFuture(null);
                }, 100).toCompletableFuture().join();

        agent.invoke(Map.of("query", "hi"), new MinimalSession("mci-invoke-dynamic"));

        ModelCallInputs inputs = captured.get();
        assertThat(inputs).isNotNull();
        assertThat(inputs.getModelName()).isEqualTo("model-b");
        assertThat(inputs.getModelId()).isEqualTo("pkg-b");
    }

    @Test
    @DisplayName("invoke without a dynamic model reports the default model and a null pool key")
    void invokeReportsDefaultModel() {
        ReActAgent agent = newAgent(newModel("model-a"));
        AtomicReference<ModelCallInputs> captured = captureModelInputs(agent);

        agent.invoke(Map.of("query", "hi"), new MinimalSession("mci-invoke-default"));

        ModelCallInputs inputs = captured.get();
        assertThat(inputs).isNotNull();
        assertThat(inputs.getModelName()).isEqualTo("model-a");
        assertThat(inputs.getModelId()).isNull();
    }

    @Test
    @DisplayName("stream reports the dynamic model name and pool key")
    void streamReportsDynamicModel() {
        ReActAgent agent = newAgent(newModel("model-b"));
        AtomicReference<ModelCallInputs> captured = captureModelInputs(agent);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                callbackCtx -> {
                    callbackCtx.setDynamicModelId("pkg-b");
                    return CompletableFuture.completedFuture(null);
                }, 100).toCompletableFuture().join();
        MinimalSession session = new MinimalSession("mci-stream-dynamic");

        agent.stream(Map.of("query", "hi"), session, List.of(StreamMode.OUTPUT)).forEachRemaining(ignored -> {
        });

        ModelCallInputs inputs = captured.get();
        assertThat(inputs).isNotNull();
        assertThat(inputs.getModelName()).isEqualTo("model-b");
        assertThat(inputs.getModelId()).isEqualTo("pkg-b");
    }

    @Test
    @DisplayName("a pool supplier failure does not bypass the BEFORE_MODEL_CALL rail channel")
    void poolSupplierFailureDoesNotBypassBeforeModelCallRails() {
        String brokenPoolId = "mci-broken-pool-" + UUID.randomUUID();
        Runner.resourceMgr().updateModel(brokenPoolId, () -> {
            throw new BaseError(StatusCode.ERROR);
        });
        try {
            ReActAgent agent = newAgent(newModel("model-a"));
            AtomicBoolean railInvoked = new AtomicBoolean(false);
            agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL,
                    callbackCtx -> {
                        railInvoked.set(true);
                        return CompletableFuture.completedFuture(null);
                    }, 100).toCompletableFuture().join();
            agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                    callbackCtx -> {
                        callbackCtx.setDynamicModelId(brokenPoolId);
                        return CompletableFuture.completedFuture(null);
                    }, 100).toCompletableFuture().join();

            // The model call itself still fails: the real resolution happens inside the
            // railed operation and reaches the ON_MODEL_EXCEPTION channel.
            assertThatThrownBy(() -> agent.invoke(Map.of("query", "hi"), new MinimalSession("mci-broken-pool")))
                    .isInstanceOf(BaseError.class);
            // The pre-model-call snapshot resolution is reporting only and must not
            // raise before the BEFORE_MODEL_CALL rails get their chance to run.
            assertThat(railInvoked).isTrue();
        } finally {
            Runner.resourceMgr().removeModelForce(brokenPoolId, true);
        }
    }

    private static final class MinimalSession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MinimalSession(String sessionId) {
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
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            // stream output is not observed by this test
        }

        @Override
        public Iterator<Object> streamIterator() {
            return state.values().iterator();
        }
    }

    private static final class StubModelClient implements Model.ModelClient {
        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages,
                com.openjiuwen.core.foundation.llm.ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages,
                com.openjiuwen.core.foundation.llm.ModelInvokeOptions options) {
            return List.of(AssistantMessageChunk.builder().content("ok").build()).iterator();
        }
    }
}
