/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data structure for chunked code execution output.
 * <p>
 * Mirrors Python's {@code ExecuteCodeChunkData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeChunkData {

    /** Raw content of the output chunk. */
    @Builder.Default
    private String text = "";

    /** Type of the output chunk: "stdout" or "stderr". */
    private String type;

    /** Index of current chunk (starting from 0). */
    private int chunkIndex;

    /** Execution exit code. */
    private Integer exitCode;

    /** Data for execution. */
    private Map<String, Object> metadata;
}
