package com.openjiuwen.auto_harness.infra;

import java.util.logging.Logger;

/**
 * Mirrors Python's {@code SessionBudgetController} in
 * {@code openjiuwen/auto_harness/infra/session_budget.py}.
 */
public class SessionBudgetController {

    private static final Logger logger = Logger.getLogger(SessionBudgetController.class.getName());
    private static final double WARN_THRESHOLD = 0.8;

    private final double wallClockSecs;
    private final double costLimitUsd;
    private final double taskTimeoutSecs;
    private Double start;
    private double costUsd;
    private boolean timeWarned;
    private boolean costWarned;

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
        logger.info(String.format(
                "Session budget started: %ss wall-clock, $%.2f cost",
                wallClockSecs,
                costLimitUsd));
    }

    public void addCost(double amountUsd) {
        this.costUsd += amountUsd;
        double ratio = costUsd / costLimitUsd;
        if (ratio >= WARN_THRESHOLD && !costWarned) {
            costWarned = true;
            logger.warning(String.format(
                    "Cost budget %.0f%% used ($%.4f / $%.2f)",
                    ratio * 100,
                    costUsd,
                    costLimitUsd));
        }
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
            warnTimeBudgetIfNeeded(getElapsedSecs());
            return true;
        }
        if (start != null) {
            warnTimeBudgetIfNeeded(getElapsedSecs());
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

    private void warnTimeBudgetIfNeeded(double elapsed) {
        double ratio = elapsed / wallClockSecs;
        if (ratio >= WARN_THRESHOLD && !timeWarned) {
            timeWarned = true;
            logger.warning(String.format(
                    "Wall-clock budget %.0f%% used (%.0fs / %.0fs)",
                    ratio * 100,
                    elapsed,
                    wallClockSecs));
        }
    }

    private double monotonicSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}
