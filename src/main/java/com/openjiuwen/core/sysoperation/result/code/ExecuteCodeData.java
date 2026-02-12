// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.code;

/**
 * Code Execution Result Data Model.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.code_operation_result.ExecuteCodeData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ExecuteCodeData {

    /**
     * Original code executed.
     */
    private final String codeContent;

    /**
     * Programming language of the original code.
     */
    private final String language;

    /**
     * Execution exit code.
     */
    private final int exitCode;

    /**
     * The code's standard output (stdout) stream.
     */
    private final String stdout;

    /**
     * The code's standard error (stderr) stream.
     */
    private final String stderr;

    public ExecuteCodeData(String codeContent, String language, int exitCode,
                           String stdout, String stderr) {
        this.codeContent = codeContent;
        this.language = language;
        this.exitCode = exitCode;
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
    }

    public String getCodeContent() {
        return codeContent;
    }

    public String getLanguage() {
        return language;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String codeContent;
        private String language;
        private int exitCode = 0;
        private String stdout = "";
        private String stderr = "";

        public Builder codeContent(String codeContent) {
            this.codeContent = codeContent;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public Builder stdout(String stdout) {
            this.stdout = stdout;
            return this;
        }

        public Builder stderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public ExecuteCodeData build() {
            return new ExecuteCodeData(codeContent, language, exitCode, stdout, stderr);
        }
    }

    @Override
    public String toString() {
        return "ExecuteCodeData{" +
            "language='" + language + '\'' +
            ", exitCode=" + exitCode +
            ", codeLength=" + (codeContent != null ? codeContent.length() : 0) +
            ", stdoutLength=" + stdout.length() +
            ", stderrLength=" + stderr.length() +
            '}';
    }
}
