/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class StopEvaluationContext used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class StopEvaluationContext {
    private int iteration;
    private int tokenUsage;
    private double elapsedSeconds;
    private Map<String, Object> lastResult;
}
