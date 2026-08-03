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
 * Backward-compatible shell command chunk payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code ExecuteCmdChunkData} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ExecuteCmdChunkData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class ExecuteCmdChunkData {

    @Builder.Default
    private String text = "";

    private String type;
    private int chunkIndex;
    private Integer exitCode;
    private Map<String, Object> metadata;
}
