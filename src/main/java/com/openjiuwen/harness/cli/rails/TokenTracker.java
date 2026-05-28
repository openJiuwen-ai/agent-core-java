/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.rails;

/**
 * Token usage tracker for CLI mode.
 * <p>
 * Mirrors Python's {@code TokenTracker} in
 * {@code openjiuwen.harness.cli.rails.token_tracker}.
 */
public class TokenTracker {

    private long promptTokens = 0;
    private long completionTokens = 0;
    private long totalTokens = 0;

    public void addPromptTokens(long count) {
        promptTokens += count;
        totalTokens += count;
    }

    public void addCompletionTokens(long count) {
        completionTokens += count;
        totalTokens += count;
    }

    public long getPromptTokens() { return promptTokens; }
    public long getCompletionTokens() { return completionTokens; }
    public long getTotalTokens() { return totalTokens; }

    public void reset() {
        promptTokens = 0;
        completionTokens = 0;
        totalTokens = 0;
    }
}
