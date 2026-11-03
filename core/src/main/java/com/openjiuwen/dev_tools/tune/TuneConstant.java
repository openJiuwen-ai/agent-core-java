/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

/**
 * Prompt tuning constants.
 *
 * <p>Mirrors Python's {@code TuneConstant} in
 * {@code openjiuwen/dev_tools/tune/base.py}.</p>
 */
public final class TuneConstant {
    public static final int DEFAULT_EXAMPLE_NUM = 1;
    public static final int DEFAULT_ITERATION_NUM = 3;
    public static final int DEFAULT_MAX_SAMPLED_EXAMPLE_NUM = 10;
    public static final int DEFAULT_PARALLEL_NUM = 1;
    public static final int DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES = 10;
    public static final double DEFAULT_EARLY_STOP_SCORE = 1.0d;

    public static final int MIN_ITERATION_NUM = 1;
    public static final int MAX_ITERATION_NUM = 20;
    public static final int MIN_PARALLEL_NUM = 1;
    public static final int MAX_PARALLEL_NUM = 20;
    public static final int MIN_EXAMPLE_NUM = 0;
    public static final int MAX_EXAMPLE_NUM = 20;

    private TuneConstant() {
    }
}
