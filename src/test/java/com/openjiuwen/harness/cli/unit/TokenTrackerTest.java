/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import com.openjiuwen.harness.cli.rails.TokenTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for token usage tracking.
 * <p>
 * Mirrors Python's {@code test_token_tracker} in
 * {@code tests.cli.unit.test_token_tracker}.
 */
class TokenTrackerTest {

    @Test
    void tracksUsage() {
        TokenTracker tracker = new TokenTracker();
        tracker.addPromptTokens(100);
        tracker.addCompletionTokens(50);
        assertEquals(100, tracker.getPromptTokens());
        assertEquals(50, tracker.getCompletionTokens());
        assertEquals(150, tracker.getTotalTokens());
    }

    @Test
    void accumulatesAcrossCalls() {
        TokenTracker tracker = new TokenTracker();
        tracker.addPromptTokens(100);
        tracker.addCompletionTokens(50);
        tracker.addPromptTokens(200);
        tracker.addCompletionTokens(80);
        assertEquals(300, tracker.getPromptTokens());
        assertEquals(130, tracker.getCompletionTokens());
        assertEquals(430, tracker.getTotalTokens());
    }

    @Test
    void handlesMissingUsage() {
        TokenTracker tracker = new TokenTracker();
        assertEquals(0, tracker.getPromptTokens());
        assertEquals(0, tracker.getCompletionTokens());
        assertEquals(0, tracker.getTotalTokens());
    }

    @Test
    void handlesZeroTokens() {
        TokenTracker tracker = new TokenTracker();
        tracker.addPromptTokens(0);
        tracker.addCompletionTokens(0);
        assertEquals(0, tracker.getPromptTokens());
        assertEquals(0, tracker.getCompletionTokens());
    }

    @Test
    void getSummaryInitial() {
        TokenTracker tracker = new TokenTracker();
        assertEquals(0, tracker.getPromptTokens());
        assertEquals(0, tracker.getCompletionTokens());
        assertEquals(0, tracker.getTotalTokens());
    }

    @Test
    void resetClearsAll() {
        TokenTracker tracker = new TokenTracker();
        tracker.addPromptTokens(100);
        tracker.addCompletionTokens(50);
        tracker.reset();
        assertEquals(0, tracker.getPromptTokens());
        assertEquals(0, tracker.getCompletionTokens());
        assertEquals(0, tracker.getTotalTokens());
    }
}
