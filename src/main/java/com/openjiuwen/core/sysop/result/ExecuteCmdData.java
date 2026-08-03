/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Backward-compatible shell command data for the legacy sysop package.
 *
 * <p>Mirrors Python's {@code ExecuteCmdData} in
 * {@code openjiuwen/core/sys_operation/result/shell_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.ExecuteCmdData}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ExecuteCmdData {

    private String command;

    private String cwd = ".";

    private Integer exitCode;

    private String stdout = "";

    private String stderr = "";

    private String shellType;

    public ExecuteCmdData() {
    }

    public ExecuteCmdData(String command, String cwd, Integer exitCode, String stdout, String stderr,
                          String shellType) {
        this.command = command;
        this.cwd = cwd;
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.shellType = shellType;
    }

    static ExecuteCmdData fromNewData(com.openjiuwen.core.sys_operation.result.ExecuteCmdData data) {
        if (data == null) {
            return null;
        }
        return ExecuteCmdData.builder()
                .command(data.getCommand())
                .cwd(data.getCwd())
                .exitCode(data.getExitCode())
                .stdout(data.getStdout())
                .stderr(data.getStderr())
                .build();
    }

    public static ExecuteCmdDataBuilder builder() {
        return new ExecuteCmdDataBuilder();
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
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

    public String getShellType() {
        return shellType;
    }

    public void setShellType(String shellType) {
        this.shellType = shellType;
    }

    public static final class ExecuteCmdDataBuilder {
        private String command;
        private String cwd = ".";
        private Integer exitCode;
        private String stdout = "";
        private String stderr = "";
        private String shellType;

        private ExecuteCmdDataBuilder() {
        }

        public ExecuteCmdDataBuilder command(String command) {
            this.command = command;
            return this;
        }

        public ExecuteCmdDataBuilder cwd(String cwd) {
            this.cwd = cwd;
            return this;
        }

        public ExecuteCmdDataBuilder exitCode(Integer exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public ExecuteCmdDataBuilder stdout(String stdout) {
            this.stdout = stdout;
            return this;
        }

        public ExecuteCmdDataBuilder stderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public ExecuteCmdDataBuilder shellType(String shellType) {
            this.shellType = shellType;
            return this;
        }

        public ExecuteCmdData build() {
            return new ExecuteCmdData(command, cwd, exitCode, stdout, stderr, shellType);
        }
    }
}
