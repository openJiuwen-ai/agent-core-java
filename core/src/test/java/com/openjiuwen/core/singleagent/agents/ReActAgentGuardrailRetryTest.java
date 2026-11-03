/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.classification.ClassifiedModelErrors;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorFacts;
import com.openjiuwen.core.foundation.llm.classification.RuleBasedModelErrorClassifier;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UT-R10: a guardrail-driven retry re-runs the model call within the same main
 * loop iteration, so it does not consume the turn budget.
 *
 * @since 2026-10-19
 */
class ReActAgentGuardrailRetryTest {
    @Test
    void invoke_temporaryFailureOnceThenSuccess_retryDoesNotConsumeRound() {
        // Given maxIterations=1 and a model that fails once with 503 then succeeds,
        // When the task runs, Then the guardrail retry recovers within the single
        // round instead of exhausting the iteration budget
        FlakyModelClient client = new FlakyModelClient();
        ReActAgent agent = new ReActAgent(new AgentCard(
            "guardrail-round-agent-" + System.nanoTime(),
            "guardrail-round-agent",
            "test agent"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureMaxIterations(1);
        agent.configure(config);
        agent.setLlm(new Model(client));

        Object result = agent.invoke(Map.of("query", "hello"), new MinimalSession("guardrail-round"));

        assertThat(client.calls).hasValue(2);
        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("output")).isEqualTo("ok");
    }

    private static final class MinimalSession
        implements AgentSessionApi, ContextEngine.SessionPort {
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

    private static final class FlakyModelClient implements Model.ModelClient {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages,
            ModelInvokeOptions options) {
            if (calls.incrementAndGet() == 1) {
                throw temporaryFailure();
            }
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages,
            ModelInvokeOptions options) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        private static BaseError temporaryFailure() {
            ModelErrorFacts facts = ModelErrorFacts.Builder.create()
                .httpStatus(503)
                .stream(false)
                .build();
            ModelCallClassification classification =
                new RuleBasedModelErrorClassifier().classify(facts);
            return ClassifiedModelErrors.toError(classification, facts, "HTTP 503", null);
        }
    }
}
