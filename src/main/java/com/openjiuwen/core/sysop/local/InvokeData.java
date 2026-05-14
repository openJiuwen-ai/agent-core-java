/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    public static InvokeDataBuilder builder() {
        return new InvokeDataBuilder();
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public static final class InvokeDataBuilder {
        private String stdout;
        private String stderr;
        private int exitCode;
        private Exception exception;

        public InvokeDataBuilder stdout(String stdout) {
            this.stdout = stdout;
            return this;
        }

        public InvokeDataBuilder stderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public InvokeDataBuilder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public InvokeDataBuilder exception(Exception exception) {
            this.exception = exception;
            return this;
        }

        public InvokeData build() {
            InvokeData data = new InvokeData();
            data.setStdout(stdout);
            data.setStderr(stderr);
            data.setExitCode(exitCode);
            data.setException(exception);
            return data;
        }
    }
}
