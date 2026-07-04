/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import java.util.Arrays;
import java.util.List;

/**
 * Backward-compatible local work configuration for the moved sys-operation package.
 *
 * <p>Mirrors Python's {@code LocalWorkConfig} in
 * {@code openjiuwen/core/sys_operation/config.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.config.LocalWorkConfig}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class LocalWorkConfig extends com.openjiuwen.core.sys_operation.config.LocalWorkConfig {

    private String workDir;

    public LocalWorkConfig() {
        super();
        if (getShellAllowlist() == null || getShellAllowlist().isEmpty()) {
            setShellAllowlist(Arrays.asList(
                    "echo", "ls", "dir", "cd", "pwd", "python", "python3", "pip", "pip3",
                    "npm", "node", "git", "cat", "type", "mkdir", "md", "rm", "rd",
                    "cp", "copy", "mv", "move", "grep", "find", "curl", "wget", "ps", "df", "ping"
            ));
        }
    }

    public LocalWorkConfig(List<String> shellAllowlist,
                           List<String> sandboxRoot,
                           boolean restrictToSandbox,
                           List<String> dangerousPatterns,
                           String workDir) {
        setShellAllowlist(shellAllowlist);
        setSandboxRoot(sandboxRoot);
        setRestrictToSandbox(restrictToSandbox);
        setDangerousPatterns(dangerousPatterns);
        this.workDir = workDir;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public boolean isIsRestrictToSandbox() {
        return isRestrictToSandbox();
    }

    public boolean getIsRestrictToSandbox() {
        return isRestrictToSandbox();
    }

    public void setIsRestrictToSandbox(boolean restrictToSandbox) {
        setRestrictToSandbox(restrictToSandbox);
    }
}
