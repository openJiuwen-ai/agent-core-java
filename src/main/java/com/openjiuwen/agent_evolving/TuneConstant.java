// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

/**
 * Hyperparameter defaults and validation bounds for self-evolving training.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.constant.TuneConstant}.
 */
public final class TuneConstant {

    // Default values
    public static final int DEFAULT_EXAMPLE_NUM = 1;
    public static final int DEFAULT_ITERATION_NUM = 3;
    public static final int DEFAULT_MAX_SAMPLED_EXAMPLE_NUM = 10;
    public static final int DEFAULT_PARALLEL_NUM = 1;
    public static final int DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES = 10;
    public static final float DEFAULT_EARLY_STOP_SCORE = 1.0f;

    // Valid ranges
    public static final int MIN_ITERATION_NUM = 1;
    public static final int MAX_ITERATION_NUM = 20;
    public static final int MIN_PARALLEL_NUM = 1;
    public static final int MAX_PARALLEL_NUM = 20;
    public static final int MIN_EXAMPLE_NUM = 0;
    public static final int MAX_EXAMPLE_NUM = 20;

    private TuneConstant() {
        // Utility class
    }
}