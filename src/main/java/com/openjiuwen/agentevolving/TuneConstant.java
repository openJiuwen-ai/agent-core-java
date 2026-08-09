/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

/**
 * Hyperparameter defaults and valid ranges for self-evolving training.
 * <p>
 * Mirrors Python's {@code TuneConstant} in
 * {@code openjiuwen/agent_evolving/constant.py}.
 */
public final class TuneConstant {

    public static final int defaultExampleNum = 1;
    public static final int defaultIterationNum = 3;
    public static final int defaultMaxSampledExampleNum = 10;
    public static final int defaultParallelNum = 1;
    public static final int defaultMaxNumSampleErrorCases = 10;
    public static final double defaultEarlyStopScore = 1.0d;

    public static final int minIterationNum = 1;
    public static final int maxIterationNum = 20;
    public static final int minParallelNum = 1;
    public static final int maxParallelNum = 20;
    public static final int minExampleNum = 0;
    public static final int maxExampleNum = 20;

    private TuneConstant() {
    }
}
