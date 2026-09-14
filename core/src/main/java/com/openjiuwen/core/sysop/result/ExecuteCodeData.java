/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code ExecuteCodeData} in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeData {

    private String codeContent;
    private String language;
    private Integer exitCode;

    @Builder.Default
    private String stdout = "";

    @Builder.Default
    private String stderr = "";
}
