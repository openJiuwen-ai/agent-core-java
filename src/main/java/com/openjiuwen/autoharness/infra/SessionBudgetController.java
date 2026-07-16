/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

/**
 * Public class SessionBudgetController used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SessionBudgetController {
    private final double wallClockSecs;
    private final double costLimitUsd;
    private final double taskTimeoutSecs;
    private long startMillis;
    private double costUsd;

    /**
     * SessionBudgetController.
     * 
     * @since 0.1.7
     */
    public SessionBudgetController() {
        this(3600.0, 10.0, 1200.0);
    }

    /**
     * SessionBudgetController.
     * 
     * @param wallClockSecs wallClockSecs
     * @param costLimitUsd costLimitUsd
     * @param taskTimeoutSecs taskTimeoutSecs
     * @since 0.1.7
     */
    public SessionBudgetController(double wallClockSecs, double costLimitUsd, double taskTimeoutSecs) {
        this.wallClockSecs = wallClockSecs;
        this.costLimitUsd = costLimitUsd;
        this.taskTimeoutSecs = taskTimeoutSecs;
    }

    /**
     * start.
     * 
     * @since 0.1.7
     */
    public void start() {
        startMillis = System.currentTimeMillis();
    }

    /**
     * addCost.
     * 
     * @param amountUsd amountUsd
     * @since 0.1.7
     */
    public void addCost(double amountUsd) {
        costUsd += amountUsd;
    }

    /**
     * elapsedSecs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double elapsedSecs() {
        if (startMillis == 0L) {
            return 0.0;
        }
        return (System.currentTimeMillis() - startMillis) / 1000.0;
    }

    /**
     * remainingSecs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double remainingSecs() {
        return Math.max(0.0, wallClockSecs - elapsedSecs());
    }

    /**
     * remainingCostUsd.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double remainingCostUsd() {
        return Math.max(0.0, costLimitUsd - costUsd);
    }

    /**
     * shouldStop.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean shouldStop() {
        return elapsedSecs() >= wallClockSecs || costUsd >= costLimitUsd;
    }

    /**
     * checkTaskBudget.
     * 
     * @param timeoutSecs timeoutSecs
     * @return the result
     * @since 0.1.7
     */
    public boolean checkTaskBudget(Double timeoutSecs) {
        double timeout = timeoutSecs != null ? timeoutSecs.doubleValue() : taskTimeoutSecs;
        return remainingSecs() >= timeout;
    }
}
