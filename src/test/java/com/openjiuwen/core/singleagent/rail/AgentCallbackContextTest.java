// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.rail;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentCallbackContext}.
 */
class AgentCallbackContextTest {

    @Test
    void testBuilderDefaults() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        assertThat(ctx.getAgent()).isNull();
        assertThat(ctx.getEvent()).isNull();
        assertThat(ctx.getInputs()).isNull();
        assertThat(ctx.getConfig()).isNull();
        assertThat(ctx.getSession()).isNull();
        assertThat(ctx.getContext()).isNull();
        assertThat(ctx.getExtra()).isNotNull().isEmpty();
        assertThat(ctx.getException()).isNull();
        assertThat(ctx.getRetryAttempt()).isZero();
        assertThat(ctx.getRetryRequest()).isNull();
    }

    @Test
    void testRequestRetryCreatesRetryRequest() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.requestRetry(0.5);
        assertThat(ctx.getRetryRequest()).isNotNull();
        assertThat(ctx.getRetryRequest().getDelaySeconds()).isEqualTo(0.5);
    }

    @Test
    void testRequestRetryNegativeDelayClampedToZero() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.requestRetry(-1.0);
        assertThat(ctx.getRetryRequest()).isNotNull();
        assertThat(ctx.getRetryRequest().getDelaySeconds()).isEqualTo(0.0);
    }

    @Test
    void testConsumeRetryRequestReturnsAndClears() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.requestRetry(1.0);
        RetryRequest request = ctx.consumeRetryRequest();
        assertThat(request).isNotNull();
        assertThat(request.getDelaySeconds()).isEqualTo(1.0);
        // After consuming, should be null
        assertThat(ctx.getRetryRequest()).isNull();
        assertThat(ctx.consumeRetryRequest()).isNull();
    }

    @Test
    void testConsumeRetryRequestWhenNone() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        assertThat(ctx.consumeRetryRequest()).isNull();
    }

    @Test
    void testExtraCommunication() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.getExtra().put("key1", "value1");
        ctx.getExtra().put("key2", 42);
        assertThat(ctx.getExtra()).containsEntry("key1", "value1");
        assertThat(ctx.getExtra()).containsEntry("key2", 42);
    }

    @Test
    void testExtraSharedAcrossReferences() {
        Map<String, Object> sharedExtra = new HashMap<>();
        sharedExtra.put("initial", true);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .extra(sharedExtra)
                .build();

        ctx.getExtra().put("added", "later");
        assertThat(sharedExtra).containsEntry("added", "later");
    }

    @Test
    void testSetRetryAttempt() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.setRetryAttempt(3);
        assertThat(ctx.getRetryAttempt()).isEqualTo(3);
    }

    @Test
    void testSetException() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        Exception ex = new RuntimeException("test error");
        ctx.setException(ex);
        assertThat(ctx.getException()).isEqualTo(ex);
        assertThat(ctx.getException().getMessage()).isEqualTo("test error");
    }

    @Test
    void testFireDelegatesToAgentCallbackFirer() {
        // Create a simple firer that records events
        java.util.List<AgentCallbackEvent> firedEvents = new java.util.ArrayList<>();
        AgentCallbackFirer firer = (event, ctx) -> firedEvents.add(event);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(firer)
                .build();

        ctx.fire(AgentCallbackEvent.BEFORE_INVOKE);
        ctx.fire(AgentCallbackEvent.AFTER_MODEL_CALL);

        assertThat(firedEvents).containsExactly(
                AgentCallbackEvent.BEFORE_INVOKE,
                AgentCallbackEvent.AFTER_MODEL_CALL
        );
    }

    @Test
    void testFireSetsEventOnContext() {
        AgentCallbackFirer firer = (event, ctx) -> {};
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(firer)
                .build();

        ctx.fire(AgentCallbackEvent.ON_TOOL_EXCEPTION);
        assertThat(ctx.getEvent()).isEqualTo(AgentCallbackEvent.ON_TOOL_EXCEPTION);
    }

    @Test
    void testFireWithNonFirerAgentDoesNotThrow() {
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent("not a firer")
                .build();
        // Should not throw
        ctx.fire(AgentCallbackEvent.BEFORE_INVOKE);
    }

    @Test
    void testFireWithNullAgentDoesNotThrow() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        ctx.fire(AgentCallbackEvent.BEFORE_INVOKE);
    }

    @Test
    void testBuilderWithInputs() {
        InvokeInputs inputs = InvokeInputs.builder()
                .query("test query")
                .conversationId("conv1")
                .build();

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(inputs)
                .build();

        assertThat(ctx.getInputs()).isInstanceOf(InvokeInputs.class);
        assertThat(((InvokeInputs) ctx.getInputs()).getQuery()).isEqualTo("test query");
    }
}
