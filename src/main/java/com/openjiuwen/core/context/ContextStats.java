/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token-usage snapshot for any context container ({@link ModelContext} or {@link ContextWindow}).
 * <p>
 * Mirrors Python's {@code ContextStats} from {@code context_engine/base.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextStats {

    @Builder.Default
    private int totalMessages = 0;

    @Builder.Default
    private int totalTokens = 0;

    @Builder.Default
    private int totalDialogues = 0;

    // ---------- message counts ----------
    @Builder.Default
    private int systemMessages = 0;

    @Builder.Default
    private int userMessages = 0;

    @Builder.Default
    private int assistantMessages = 0;

    @Builder.Default
    private int toolMessages = 0;

    @Builder.Default
    private int tools = 0;

    // ---------- token counts ----------
    @Builder.Default
    private int systemMessageTokens = 0;

    @Builder.Default
    private int userMessageTokens = 0;

    @Builder.Default
    private int assistantMessageTokens = 0;

    @Builder.Default
    private int toolMessageTokens = 0;

    @Builder.Default
    private int toolTokens = 0;
}
