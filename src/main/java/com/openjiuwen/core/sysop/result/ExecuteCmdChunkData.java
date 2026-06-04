/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data structure for chunked shell command output.
 * <p>
 * Mirrors Python's {@code ExecuteCmdChunkData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCmdChunkData {

    /** Raw content of the output chunk. */
    @Builder.Default
    private String text = "";

    /** Type of the output chunk: "stdout" or "stderr". */
    private String type;

    /** Index of current chunk (starting from 0). */
    private int chunkIndex;

    /** Command exit code. */
    private Integer exitCode;

    /** Data for command. */
    private Map<String, Object> metadata;

    public String getStdout() {
        return "stdout".equals(type) ? text : "";
    }

    public String getStderr() {
        return "stderr".equals(type) ? text : "";
    }
}
