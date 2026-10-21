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
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
        AgentCallbackContext ctx = contextWith(classifiedHttp(429));
        rail.onModelException(ctx);
        assertThat(ctx.consumeRetryRequest()).isNotNull();

        rail.onModelException(ctx);
        assertThat(ctx.consumeRetryRequest()).isNull();
    }

    @Test
    void onModelException_permanentCategories_neverRetried() {
        AgentCallbackContext quota = contextWith(classifiedHttp(402));
        rail.onModelException(quota);
        assertThat(quota.consumeRetryRequest()).isNull();

        AgentCallbackContext auth = contextWith(classifiedHttp(401));
        rail.onModelException(auth);
        assertThat(auth.consumeRetryRequest()).isNull();

        AgentCallbackContext tooLong = contextWith(classifiedBody(400, "prompt_too_long"));
        rail.onModelException(tooLong);
        assertThat(tooLong.consumeRetryRequest()).isNull();
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
        AgentCallbackContext ctx = contextWith(classifiedHttp(429));
        ctx.setAgent(agent);

        rail.onModelException(ctx);
        assertThat(ctx.consumeRetryRequest()).isNull();
    }

    @Test
    void onModelException_backupRailPresent_guardrailYields() {
        ReActAgent agent = newAgent();
        agent.registerInstanceRail(new ModelBackupRail(List.of())).toCompletableFuture().join();
        AgentCallbackContext ctx = contextWith(classifiedHttp(429));
        ctx.setAgent(agent);

        rail.onModelException(ctx);
        assertThat(ctx.consumeRetryRequest()).isNull();
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
