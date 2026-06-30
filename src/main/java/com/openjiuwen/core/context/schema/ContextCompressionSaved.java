/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saved context size summary.
 * <p>
 * Mirrors Python's {@code ContextCompressionSaved}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextCompressionSaved {
    @Builder.Default
    private int messages = 0;

    @Builder.Default
    private int tokens = 0;

    @Builder.Default
    private float percent = 0.0f;
}
