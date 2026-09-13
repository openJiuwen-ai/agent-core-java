/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of callback chain execution.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainResult {
    private ChainAction action;

    /** Final result value. */
    private Object result;

    /** The chain execution context. */
    private ChainContext context;

    /** Exception if chain failed. */
    private Exception error;
}
