/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.cli.rails.TokenTrackingRail;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code TestTokenTrackingRail} coverage for {@code TokenTrackingRail} in
 * {@code openjiuwen/harness/cli/rails/token_tracker.py}.
 *
 * <p>The Python test source is {@code tests/cli/unit/test_token_tracker.py}.</p>
 */
class TokenTrackingRailTest {

    @Test
    void tracksUsage() {
        TokenTrackingRail tracker = new TokenTrackingRail();

        tracker.afterModelCall(contextWithUsage(usage(100, 50))).toCompletableFuture().join();

        assertEquals(100L, tracker.getTotalInputTokens());
        assertEquals(50L, tracker.getTotalOutputTokens());
        assertEquals(1L, tracker.getCallCount());
    }

    @Test
    void accumulatesAcrossCalls() {
        TokenTrackingRail tracker = new TokenTrackingRail();

        tracker.afterModelCall(contextWithUsage(usage(100, 50))).toCompletableFuture().join();
        tracker.afterModelCall(contextWithUsage(usage(200, 80))).toCompletableFuture().join();

        Map<String, Long> summary = tracker.getSummary();
        assertEquals(300L, summary.get("input_tokens"));
        assertEquals(130L, summary.get("output_tokens"));
        assertEquals(430L, summary.get("total_tokens"));
        assertEquals(2L, summary.get("model_calls"));
    }

    @Test
    void handlesMissingUsage() {
        TokenTrackingRail tracker = new TokenTrackingRail();

        tracker.afterModelCall(contextWithResponse(new LinkedHashMap<>())).toCompletableFuture().join();

        assertEquals(0L, tracker.getTotalInputTokens());
        assertEquals(0L, tracker.getTotalOutputTokens());
        assertEquals(1L, tracker.getCallCount());
    }

    @Test
    void handlesMissingResponse() {
        TokenTrackingRail tracker = new TokenTrackingRail();
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(Map.of());

        tracker.afterModelCall(context).toCompletableFuture().join();

        assertEquals(1L, tracker.getCallCount());
        assertEquals(0L, tracker.getTotalInputTokens());
    }

    @Test
    void getSummaryInitial() {
        Map<String, Long> summary = new TokenTrackingRail().getSummary();

        assertEquals(0L, summary.get("input_tokens"));
        assertEquals(0L, summary.get("output_tokens"));
        assertEquals(0L, summary.get("total_tokens"));
        assertEquals(0L, summary.get("model_calls"));
    }

    @Test
    void fallsBackToUsageMetadataNames() {
        TokenTrackingRail tracker = new TokenTrackingRail();
        Map<String, Object> response = Map.of(
                "usage_metadata",
                Map.of("input_tokens", 12, "output_tokens", 7)
        );

        tracker.afterModelCall(contextWithResponse(response)).toCompletableFuture().join();

        assertEquals(12L, tracker.getTotalInputTokens());
        assertEquals(7L, tracker.getTotalOutputTokens());
        assertEquals(19L, tracker.getSummary().get("total_tokens"));
    }

    private static AgentCallbackContext contextWithUsage(Object usage) {
        return contextWithResponse(Map.of("usage", usage));
    }

    private static AgentCallbackContext contextWithResponse(Object response) {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setResponse(response);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        return context;
    }

    private static Map<String, Object> usage(int promptTokens, int completionTokens) {
        return Map.of(
                "prompt_tokens", promptTokens,
                "completion_tokens", completionTokens
        );
    }
}
