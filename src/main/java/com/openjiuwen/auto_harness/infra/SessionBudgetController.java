package com.openjiuwen.auto_harness.infra;

/**
 * Mirrors Python's {@code SessionBudgetController} in {@code openjiuwen.auto_harness.infra.session_budget}.
 */
public class SessionBudgetController {

    private final double wallClockSecs;
    private final double costLimitUsd;
    private final double taskTimeoutSecs;
    private Double start;
    private double costUsd;

    public SessionBudgetController() {
        this(3600.0, 10.0, 1200.0);
    }

    public SessionBudgetController(double wallClockSecs, double costLimitUsd, double taskTimeoutSecs) {
        this.wallClockSecs = wallClockSecs;
        this.costLimitUsd = costLimitUsd;
        this.taskTimeoutSecs = taskTimeoutSecs;
    }

    public void start() {
        this.start = monotonicSeconds();
    }

    public void addCost(double amountUsd) {
        this.costUsd += amountUsd;
    }

    public double getElapsedSecs() {
        if (start == null) {
            return 0.0;
        }
        return monotonicSeconds() - start;
    }

    public double getRemainingSecs() {
        return Math.max(0.0, wallClockSecs - getElapsedSecs());
    }

    public double getRemainingCostUsd() {
        return Math.max(0.0, costLimitUsd - costUsd);
    }

    public boolean isShouldStop() {
        if (start != null && getElapsedSecs() >= wallClockSecs) {
            return true;
        }
        return costUsd >= costLimitUsd;
    }

    public boolean checkTaskBudget() {
        return checkTaskBudget(null);
    }

    public boolean checkTaskBudget(Double timeoutSecs) {
        double timeout = timeoutSecs != null ? timeoutSecs : taskTimeoutSecs;
        return getRemainingSecs() >= timeout;
    }

    void setStartForTest(double start) {
        this.start = start;
    }

    private double monotonicSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}
