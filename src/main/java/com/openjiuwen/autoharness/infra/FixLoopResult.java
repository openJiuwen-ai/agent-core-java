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

@Data
@Builder
@NoArgsConstructor
/**
 * Public class FixLoopResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class FixLoopResult {
    @Builder.Default
    private boolean isSuccess = false;
    @Builder.Default
    private int attempts = 0;
    @Builder.Default
    private int phase = 1;
    @Builder.Default
    private List<String> errorLog = new ArrayList<>();
}
