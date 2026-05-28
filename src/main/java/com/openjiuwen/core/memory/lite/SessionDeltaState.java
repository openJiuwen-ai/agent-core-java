/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks incremental changes to a session file.
 * <p>
 * Mirrors Python's {@code SessionDeltaState} dataclass from
 * {@code core/memory/lite/manager.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDeltaState {

    @Builder.Default
    private int lastSize = 0;

    @Builder.Default
    private int pendingBytes = 0;

    @Builder.Default
    private int pendingMessages = 0;
}
