/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Local working configuration.
 * <p>
 * Mirrors Python's {@code LocalWorkConfig} in
 * {@code openjiuwen/core/sys_operation/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocalWorkConfig {

    @Builder.Default
    @JsonProperty("shell_allowlist")
    private List<String> shellAllowlist = List.of(
            "echo", "rg", "ls", "dir", "cd", "pwd", "python", "python3", "pip", "pip3", "npm", "node", "git",
            "cat", "type", "mkdir", "md", "rm", "rd", "cp", "copy", "mv", "move", "grep", "find", "curl", "wget",
            "ps", "df", "ping"
    );

    @JsonProperty("sandbox_root")
    private List<String> sandboxRoot;

    @JsonProperty("work_dir")
    private String workDir;

    @Builder.Default
    @JsonProperty("restrict_to_sandbox")
    private boolean restrictToSandbox = false;

    @JsonProperty("dangerous_patterns")
    private List<String> dangerousPatterns;

    public LocalWorkConfig(List<String> shellAllowlist, List<String> sandboxRoot,
                           boolean restrictToSandbox, List<String> dangerousPatterns) {
        this.shellAllowlist = shellAllowlist;
        this.sandboxRoot = sandboxRoot;
        this.restrictToSandbox = restrictToSandbox;
        this.dangerousPatterns = dangerousPatterns;
    }

    /**
     * Legacy 5-arg constructor used by SandboxOperationSupport
     * (allowlist, sandboxRoot, restrict, dangerous, workDir).
     */
    public LocalWorkConfig(List<String> shellAllowlist, List<String> sandboxRoot,
                           boolean restrictToSandbox, List<String> dangerousPatterns,
                           String workDir) {
        this.shellAllowlist = shellAllowlist;
        this.sandboxRoot = sandboxRoot;
        this.restrictToSandbox = restrictToSandbox;
        this.dangerousPatterns = dangerousPatterns;
        this.workDir = workDir;
    }
}
