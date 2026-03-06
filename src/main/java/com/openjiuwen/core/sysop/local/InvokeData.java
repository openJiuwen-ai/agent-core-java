/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.local;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured return model for one-time subprocess execution via {@code invoke()} method.
 * <p>
 * Mirrors Python's {@code InvokeData} in {@code local/utils.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeData {

    /** Complete standard output string captured from the subprocess execution. */
    private String stdout;

    /** Complete standard error string captured from the subprocess execution. */
    private String stderr;

    /** Exit code returned by the subprocess (0 for success, non-zero for errors). */
    private int exitCode;

    /** Exception captured during subprocess execution, if any. */
    private Exception exception;
}
