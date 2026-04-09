/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Memory search result with relevance score.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemResult {
    private MemInfo memInfo;
    @Builder.Default
    private double score = 0.0;
}
