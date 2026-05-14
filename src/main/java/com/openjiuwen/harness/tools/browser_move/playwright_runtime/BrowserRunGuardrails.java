package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

/**
 * Mirrors Python's {@code BrowserRunGuardrails} in browser_move config.
 */
public class BrowserRunGuardrails {

    private int maxSteps = 20;
    private int maxFailures = 2;
    private int timeoutS = 180;
    private boolean retryOnce = true;
    private boolean resumeOnMaxIterations;

    public BrowserRunGuardrails() {
    }

    public BrowserRunGuardrails(int maxSteps, int maxFailures, int timeoutS, boolean retryOnce) {
        this.maxSteps = maxSteps;
        this.maxFailures = maxFailures;
        this.timeoutS = timeoutS;
        this.retryOnce = retryOnce;
    }

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
    public int getMaxFailures() { return maxFailures; }
    public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
    public int getTimeoutS() { return timeoutS; }
    public void setTimeoutS(int timeoutS) { this.timeoutS = timeoutS; }
    public boolean isRetryOnce() { return retryOnce; }
    public void setRetryOnce(boolean retryOnce) { this.retryOnce = retryOnce; }
    public boolean isResumeOnMaxIterations() { return resumeOnMaxIterations; }
    public void setResumeOnMaxIterations(boolean resumeOnMaxIterations) { this.resumeOnMaxIterations = resumeOnMaxIterations; }
}
