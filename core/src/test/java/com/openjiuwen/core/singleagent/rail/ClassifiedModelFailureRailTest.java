/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.classification.ClassifiedModelErrors;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorFacts;
import com.openjiuwen.core.foundation.llm.classification.RuleBasedModelErrorClassifier;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guardrail rail: classification-driven disposition of model call failures in
 * the main loop retry ring.
 *
 * @since 2026-10-19
 */
class ClassifiedModelFailureRailTest {
    private final ClassifiedModelFailureRail rail = new ClassifiedModelFailureRail();

    @Test
    void onModelException_rateLimited_requestsRetryWithWait() {
        // Given a rate limited failure, When the guardrail hook runs,
        // Then a guarded retry with a positive wait is requested
        AgentCallbackContext ctx = contextWith(classifiedHttp(429));
        rail.onModelException(ctx);

        RetryRequest retry = ctx.consumeRetryRequest();
        assertThat(retry).isNotNull();
        assertThat(retry.getDelaySeconds()).isGreaterThan(0.0D);
    }

    @Test
    void onModelException_budgetExhausted_noFurtherRetry() {
        RecordingSession session = new RecordingSession(false);
        AgentCallbackContext ctx = streamingContextWith(classifiedHttp(429), agentWithMaxRetries(1), session);
        rail.onModelException(ctx);
        assertThat(ctx.consumeRetryRequest()).isNotNull();
        int writesBeforeExhaustion = session.modelRetryWriteAttempts;

        rail.onModelException(ctx);

        assertThat(ctx.consumeRetryRequest()).isNull();
        assertThat(session.modelRetryWriteAttempts).isEqualTo(writesBeforeExhaustion);
    }

    @Test
    void onModelException_permanentCategories_neverRetried() {
        RecordingSession session = new RecordingSession(false);
        ReActAgent agent = newAgent();
        AgentCallbackContext quota = streamingContextWith(classifiedHttp(402), agent, session);
        rail.onModelException(quota);
        assertThat(quota.consumeRetryRequest()).isNull();

        AgentCallbackContext auth = streamingContextWith(classifiedHttp(401), agent, session);
        rail.onModelException(auth);
        assertThat(auth.consumeRetryRequest()).isNull();

        AgentCallbackContext tooLong = streamingContextWith(classifiedBody(400, "prompt_too_long"), agent, session);
        rail.onModelException(tooLong);

        assertThat(tooLong.consumeRetryRequest()).isNull();
        assertThat(session.modelRetryWriteAttempts).isZero();
    }

    @Test
    void onModelException_unclassifiedException_notRetried() {
        AgentCallbackContext ctx = contextWith(new IllegalStateException("unrelated failure"));
        rail.onModelException(ctx);
        assertThat(ctx.consumeRetryRequest()).isNull();
    }

    @Test
    void onModelException_zeroMaxRetriesModel_guardrailStaysSilent() {
        // Given a model configured with maxRetries=0, When a retryable failure arrives,
        // Then the guardrail honors the zero and requests no retry
        ReActAgent agent = agentWithMaxRetries(0);
        RecordingSession session = new RecordingSession(false);
        AgentCallbackContext ctx = streamingContextWith(classifiedHttp(429), agent, session);

        rail.onModelException(ctx);

        assertThat(ctx.consumeRetryRequest()).isNull();
        assertThat(session.modelRetryWriteAttempts).isZero();
    }

    @Test
    void onModelException_backupRailPresent_guardrailYields() {
        ReActAgent agent = newAgent();
        agent.registerInstanceRail(new ModelBackupRail(List.of())).toCompletableFuture().join();
        RecordingSession session = new RecordingSession(false);
        AgentCallbackContext ctx = streamingContextWith(classifiedHttp(429), agent, session);

        rail.onModelException(ctx);

        assertThat(ctx.consumeRetryRequest()).isNull();
        assertThat(session.modelRetryWriteAttempts).isZero();
    }

    @Test
    void railWriteFailureDoesNotCancelRetryOrReplaceModelFailure() {
        ReActAgent agent = agentWithMaxRetries(1);
        useZeroDelayGuardrail(agent);
        RecordingSession session = new RecordingSession(true);
        AgentCallbackContext ctx = new AgentCallbackContext(agent);
        ctx.setSession(session);
        ctx.getExtra().put("_streaming", true);
        RuntimeException firstFailure = classifiedHttp(500);
        RuntimeException finalFailure = classifiedHttp(500);
        AtomicInteger modelCalls = new AtomicInteger();

        assertThatThrownBy(() -> Rails.run(
                ctx,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    throw modelCalls.incrementAndGet() == 1 ? firstFailure : finalFailure;
                }
        )).isSameAs(finalFailure);

        assertThat(modelCalls).hasValue(2);
        assertThat(session.railWriteAttempts).isEqualTo(1);
        assertThat(session.modelRetryWriteAttempts).isEqualTo(1);
    }

    @Test
    void init_backupRailAlreadyRegistered_failsFast() {
        ReActAgent agent = newAgent();
        agent.registerInstanceRail(new ModelBackupRail(List.of())).toCompletableFuture().join();

        assertThatThrownBy(() -> new ClassifiedModelFailureRail().init(agent))
            .isInstanceOf(IllegalStateException.class);
    }

    private static AgentCallbackContext contextWith(RuntimeException exception) {
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setException(exception);
        return ctx;
    }

    private static AgentCallbackContext streamingContextWith(RuntimeException exception, ReActAgent agent,
            RecordingSession session) {
        AgentCallbackContext ctx = new AgentCallbackContext(agent);
        ctx.setException(exception);
        ctx.setSession(session);
        ctx.getExtra().put("_streaming", true);
        return ctx;
    }

    private static void useZeroDelayGuardrail(ReActAgent agent) {
        agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        agent.registerInstanceRail(new ClassifiedModelFailureRail(1, 0.0D)).toCompletableFuture().join();
    }

    private static RuntimeException classifiedHttp(int status) {
        return classifiedBody(status, null);
    }

    private static RuntimeException classifiedBody(int status, String errorCode) {
        ModelErrorFacts.Builder builder = ModelErrorFacts.Builder.create()
            .httpStatus(status)
            .stream(false);
        if (errorCode != null) {
            builder.errorCode(errorCode).errorMessage(errorCode);
        }
        ModelErrorFacts facts = builder.build();
        ModelCallClassification classification =
            new RuleBasedModelErrorClassifier().classify(facts);
        return ClassifiedModelErrors.toError(classification, facts, "HTTP " + status, null);
    }

    private static ReActAgent newAgent() {
        return new ReActAgent(new AgentCard(
            "guardrail-agent-" + System.nanoTime(),
            "guardrail-agent",
            "test agent"));
    }

    private static ReActAgent agentWithMaxRetries(int maxRetries) {
        ReActAgent agent = newAgent();
        Model model = new Model(new StubModelClient(),
            ModelClientConfig.builder().maxRetries(maxRetries).build(),
            ModelRequestConfig.builder().modelName("stub").build());
        agent.setLlm(model);
        return agent;
    }

    private static final class RecordingSession implements AgentSessionApi {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();
        private final boolean failRailWrites;
        private int modelRetryWriteAttempts;
        private int railWriteAttempts;

        private RecordingSession(boolean failRailWrites) {
            this.failRailWrites = failRailWrites;
        }

        @Override
        public String getSessionId() {
            return "guardrail-session";
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
            if (!(data instanceof OutputSchema output) || !"model_retry".equals(output.getType())) {
                stream.add(data);
                return;
            }
            modelRetryWriteAttempts++;
            if (output.getPayload() instanceof Map<?, ?> payload
                && "rail".equals(payload.get("retry_tier"))) {
                railWriteAttempts++;
                if (failRailWrites) {
                    throw new IllegalStateException("stream write unavailable");
                }
            }
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }

    private static final class StubModelClient implements Model.ModelClient {
        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages,
            ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages,
            ModelInvokeOptions options) {
            return List.<AssistantMessageChunk>of().iterator();
        }
    }
}
