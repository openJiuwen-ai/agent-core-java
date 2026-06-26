/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

/**
 * Mirrors Python's {@code InvokeData} in
 * {@code openjiuwen/core/sys_operation/local/utils.py}.
 */
public final class InvokeData {

    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final Exception exception;

    public InvokeData(String stdout, String stderr, int exitCode, Exception exception) {
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
        this.exitCode = exitCode;
        this.exception = exception;
    }

    public InvokeData(String stdout, String stderr, int exitCode) {
        this(stdout, stderr, exitCode, null);
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public Exception getException() {
        return exception;
    }
}
