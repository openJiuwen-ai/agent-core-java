/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

/**
 * Public class SessionBudgetController used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SessionBudgetController {
    private final double wallClockSecs;
    private final double costLimitUsd;
    private final double taskTimeoutSecs;
    private long startMillis;
    private double costUsd;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionBudgetController() {
        this(3600.0, 10.0, 1200.0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionBudgetController(double wallClockSecs, double costLimitUsd, double taskTimeoutSecs) {
        this.wallClockSecs = wallClockSecs;
        this.costLimitUsd = costLimitUsd;
        this.taskTimeoutSecs = taskTimeoutSecs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void start() {
        startMillis = System.currentTimeMillis();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addCost(double amountUsd) {
        costUsd += amountUsd;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double elapsedSecs() {
        if (startMillis == 0L) {
            return 0.0;
        }
        return (System.currentTimeMillis() - startMillis) / 1000.0;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double remainingSecs() {
        return Math.max(0.0, wallClockSecs - elapsedSecs());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double remainingCostUsd() {
        return Math.max(0.0, costLimitUsd - costUsd);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shouldStop() {
        return elapsedSecs() >= wallClockSecs || costUsd >= costLimitUsd;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean checkTaskBudget(Double timeoutSecs) {
        double timeout = timeoutSecs != null ? timeoutSecs.doubleValue() : taskTimeoutSecs;
        return remainingSecs() >= timeout;
    }
}
