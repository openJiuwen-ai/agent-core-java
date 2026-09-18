/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code ExecuteCmdData} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCmdData {

    private String command;

    @Builder.Default
    private String cwd = ".";

    private Integer exitCode;

    @Builder.Default
    private String stdout = "";

    @Builder.Default
    private String stderr = "";

    private String shellType;
}
