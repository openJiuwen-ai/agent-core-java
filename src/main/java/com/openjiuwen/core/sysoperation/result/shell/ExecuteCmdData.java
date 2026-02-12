// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.shell;

/**
 * Data structure for execute command result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.shell_operation_result.ExecuteCmdData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ExecuteCmdData {

    /**
     * Original shell command executed.
     */
    private final String command;

    /**
     * Current working directory.
     */
    private final String cwd;

    /**
     * Command exit code.
     */
    private final int exitCode;

    /**
     * The command's standard output (stdout) stream.
     */
    private final String stdout;

    /**
     * The command's standard error (stderr) stream.
     */
    private final String stderr;

    public ExecuteCmdData(String command, String cwd, int exitCode,
                          String stdout, String stderr) {
        this.command = command;
        this.cwd = cwd != null ? cwd : ".";
        this.exitCode = exitCode;
        this.stdout = stdout != null ? stdout : "";
        this.stderr = stderr != null ? stderr : "";
    }

    public String getCommand() {
        return command;
    }

    public String getCwd() {
        return cwd;
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
        private String command;
        private String cwd = ".";
        private int exitCode = 0;
        private String stdout = "";
        private String stderr = "";

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder cwd(String cwd) {
            this.cwd = cwd;
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

        public ExecuteCmdData build() {
            return new ExecuteCmdData(command, cwd, exitCode, stdout, stderr);
        }
    }

    @Override
    public String toString() {
        return "ExecuteCmdData{" +
            "command='" + command + '\'' +
            ", cwd='" + cwd + '\'' +
            ", exitCode=" + exitCode +
            ", stdoutLength=" + stdout.length() +
            ", stderrLength=" + stderr.length() +
            '}';
    }
}
