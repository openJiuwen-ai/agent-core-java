/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Mirrors Python's {@code ExecuteCodeChunkData} in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeChunkData {

    @Builder.Default
    private String text = "";

    private String type;
    private int chunkIndex;
    private Integer exitCode;
    private Map<String, Object> metadata;
}
