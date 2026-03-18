// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.rail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Retry directive produced by on_exception rails.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryRequest {
    @Builder.Default
    private double delaySeconds = 0.0;
}
