/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code ExecuteCmdChunkData} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCmdChunkData {

    @Builder.Default
    private String text = "";

    private String type;
    private int chunkIndex;
    private Integer exitCode;
    private Map<String, Object> metadata;
}
