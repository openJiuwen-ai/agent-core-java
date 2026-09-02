/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class FixLoopResult used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixLoopResult {
    @Builder.Default
    private boolean isSuccess = false;
    @Builder.Default
    private int attempts = 0;
    @Builder.Default
    private int phase = 1;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> errorLog = new ArrayList<>();
}
