/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class SessionTaskRow used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTaskRow {
    private String taskId;
    private String subSessionId;
    private String description;
    private String status;
    @Builder.Default
    private String result = "";
    @Builder.Default
    private String error = "";
}
