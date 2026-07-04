/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Backward-compatible execute-code payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code ExecuteCodeData} in
 * {@code openjiuwen/core/sys_operation/result/code_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ExecuteCodeData}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ExecuteCodeData {

    private String codeContent;
    private String language;
    private Integer exitCode;
    private String stdout = "";
    private String stderr = "";

    public String getCodeContent() {
        return codeContent;
    }

    public void setCodeContent(String codeContent) {
        this.codeContent = codeContent;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
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
}
