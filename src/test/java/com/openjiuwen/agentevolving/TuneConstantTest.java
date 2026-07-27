/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TuneConstantTest {

    @Test
    void exposesExpectedDefaultsAndBounds() {
        assertEquals(1, TuneConstant.defaultExampleNum);
        assertEquals(3, TuneConstant.defaultIterationNum);
        assertEquals(10, TuneConstant.defaultMaxSampledExampleNum);
        assertEquals(1, TuneConstant.defaultParallelNum);
        assertEquals(10, TuneConstant.defaultMaxNumSampleErrorCases);
        assertEquals(1.0d, TuneConstant.defaultEarlyStopScore);
        assertEquals(1, TuneConstant.minIterationNum);
        assertEquals(20, TuneConstant.maxIterationNum);
        assertEquals(1, TuneConstant.minParallelNum);
        assertEquals(20, TuneConstant.maxParallelNum);
        assertEquals(0, TuneConstant.minExampleNum);
        assertEquals(20, TuneConstant.maxExampleNum);
    }
}
