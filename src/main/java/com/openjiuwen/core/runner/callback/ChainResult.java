/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.runner.callback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of callback chain execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainResult {

    /** Final action taken by the chain. */
    private ChainAction action;

    /** Final result value. */
    private Object result;

    /** The chain execution context. */
    private ChainContext context;

    /** Exception if chain failed. */
    private Exception error;
}
