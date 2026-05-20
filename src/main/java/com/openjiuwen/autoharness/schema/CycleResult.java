/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class CycleResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class CycleResult {
    @Builder.Default
    private boolean isSuccess = false;
    @Builder.Default
    private String summary = "";
    @Builder.Default
    private String prUrl = "";
    @Builder.Default
    private String error = "";
    @Builder.Default
    private boolean isReverted = false;
    @Builder.Default
    private String errorLog = "";

}
