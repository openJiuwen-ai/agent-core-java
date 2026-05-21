/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compression savings snapshot (messages/tokens saved and percentage).
 * <p>
 * Mirrors Python's {@code ContextCompressionSaved} from
 * {@code context_engine/schema/context_state.py}.
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
    private double percent = 0.0;
}
