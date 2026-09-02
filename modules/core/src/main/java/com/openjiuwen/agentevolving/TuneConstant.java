/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

/**
 * Hyperparameter defaults and validation bounds for self-evolving training.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.constant.TuneConstant}.
 * 
 * @since 0.1.7
 */
public final class TuneConstant {
    // Default values
    /**
     * DEFAULT_EXAMPLE_NUM.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_EXAMPLE_NUM = 1;

    /**
     * DEFAULT_ITERATION_NUM.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_ITERATION_NUM = 3;

    /**
     * DEFAULT_MAX_SAMPLED_EXAMPLE_NUM.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_MAX_SAMPLED_EXAMPLE_NUM = 10;

    /**
     * DEFAULT_PARALLEL_NUM.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_PARALLEL_NUM = 1;

    /**
     * DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES = 10;

    /**
     * DEFAULT_EARLY_STOP_SCORE.
     * 
     * @since 0.1.7
     */
    public static final float DEFAULT_EARLY_STOP_SCORE = 1.0f;

    // Valid ranges

    /**
     * MIN_ITERATION_NUM.
     * 
     * @since 0.1.7
     */
    public static final int MIN_ITERATION_NUM = 1;

    /**
     * MAX_ITERATION_NUM.
     * 
     * @since 0.1.7
     */
    public static final int MAX_ITERATION_NUM = 20;

    /**
     * MIN_PARALLEL_NUM.
     * 
     * @since 0.1.7
     */
    public static final int MIN_PARALLEL_NUM = 1;

    /**
     * MAX_PARALLEL_NUM.
     * 
     * @since 0.1.7
     */
    public static final int MAX_PARALLEL_NUM = 20;

    /**
     * MIN_EXAMPLE_NUM.
     * 
     * @since 0.1.7
     */
    public static final int MIN_EXAMPLE_NUM = 0;

    /**
     * MAX_EXAMPLE_NUM.
     * 
     * @since 0.1.7
     */
    public static final int MAX_EXAMPLE_NUM = 20;

    /**
     * TuneConstant.
     * 
     * @since 0.1.7
     */
    private TuneConstant() {
        // Utility class
    }
}
