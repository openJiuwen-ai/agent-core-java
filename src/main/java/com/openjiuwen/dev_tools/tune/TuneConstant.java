/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

/**
 * Prompt tuning constants.
 *
 * <p>Mirrors Python's {@code TuneConstant} in {@code openjiuwen.dev_tools.tune.base}.
 */
public final class TuneConstant {

    // ========== optimizer parameters default value constant ==========

    /**
     * Default number of examples.
     */
    public static final int DEFAULT_EXAMPLE_NUM = 1;

    /**
     * Default number of iterations.
     */
    public static final int DEFAULT_ITERATION_NUM = 3;

    /**
     * Default maximum number of sampled examples.
     */
    public static final int DEFAULT_MAX_SAMPLED_EXAMPLE_NUM = 10;

    /**
     * Default number of parallel executions.
     */
    public static final int DEFAULT_PARALLEL_NUM = 1;

    /**
     * Default maximum number of sample error cases.
     */
    public static final int DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES = 10;

    /**
     * Default early stop score.
     */
    public static final double DEFAULT_EARLY_STOP_SCORE = 1.0;

    // ========== optimizer parameters threshold constant ==========

    /**
     * Minimum number of iterations.
     */
    public static final int MIN_ITERATION_NUM = 1;

    /**
     * Maximum number of iterations.
     */
    public static final int MAX_ITERATION_NUM = 20;

    /**
     * Minimum number of parallel executions.
     */
    public static final int MIN_PARALLEL_NUM = 1;

    /**
     * Maximum number of parallel executions.
     */
    public static final int MAX_PARALLEL_NUM = 20;

    /**
     * Minimum number of examples.
     */
    public static final int MIN_EXAMPLE_NUM = 0;

    /**
     * Maximum number of examples.
     */
    public static final int MAX_EXAMPLE_NUM = 20;

    /**
     * Private constructor to prevent instantiation.
     */
    private TuneConstant() {
        throw new UnsupportedOperationException("Constant class cannot be instantiated");
    }
}