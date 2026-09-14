/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Browser run guardrail limits.
 *
 * <p>Mirrors Python's {@code BrowserRunGuardrails} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/config.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrowserRunGuardrails {

    private int maxSteps = 20;
    private int maxFailures = 2;
    private int timeoutSeconds = 180;
    private boolean retryOnce = true;
    private boolean resumeOnMaxIterations;

    public BrowserRunGuardrails() {
    }

    public BrowserRunGuardrails(
            int maxSteps,
            int maxFailures,
            int timeoutSeconds,
            boolean retryOnce,
            boolean resumeOnMaxIterations
    ) {
        setMaxSteps(maxSteps);
        setMaxFailures(maxFailures);
        setTimeoutSeconds(timeoutSeconds);
        this.retryOnce = retryOnce;
        this.resumeOnMaxIterations = resumeOnMaxIterations;
    }

    @JsonProperty("max_steps")
    public int getMaxSteps() {
        return maxSteps;
    }

    @JsonProperty("max_steps")
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = Math.max(1, maxSteps);
    }

    @JsonProperty("max_failures")
    public int getMaxFailures() {
        return maxFailures;
    }

    @JsonProperty("max_failures")
    public void setMaxFailures(int maxFailures) {
        this.maxFailures = Math.max(0, maxFailures);
    }

    @JsonProperty("timeout_s")
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @JsonProperty("timeout_s")
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    @JsonProperty("retry_once")
    public boolean isRetryOnce() {
        return retryOnce;
    }

    @JsonProperty("retry_once")
    public void setRetryOnce(boolean retryOnce) {
        this.retryOnce = retryOnce;
    }

    @JsonProperty("resume_on_max_iterations")
    public boolean isResumeOnMaxIterations() {
        return resumeOnMaxIterations;
    }

    @JsonProperty("resume_on_max_iterations")
    public void setResumeOnMaxIterations(boolean resumeOnMaxIterations) {
        this.resumeOnMaxIterations = resumeOnMaxIterations;
    }
}
