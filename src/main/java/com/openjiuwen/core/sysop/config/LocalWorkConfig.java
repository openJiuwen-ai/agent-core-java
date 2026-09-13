/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * Local working configuration.
 * <p>
 * Mirrors Python's {@code LocalWorkConfig} in {@code sys_operation/config.py}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalWorkConfig {
    @Builder.Default
    private List<String> shellAllowlist = Arrays.asList("echo", "ls", "dir", "cd", "pwd", "python", "python3", "pip",
            "pip3", "npm", "node", "git", "cat", "type", "mkdir", "md", "rm", "rd", "cp", "copy", "mv", "move", "grep",
            "find", "curl", "wget", "ps", "df", "ping");

    /**
     * Security boundary roots for file operations and shell working-directory resolution.
     */
    private List<String> sandboxRoot;

    /**
     * Whether sandbox roots should be enforced.
     */
    @Builder.Default
    private boolean isRestrictToSandbox = false;

    /**
     * Regex patterns for dangerous commands to block.
     */
    private List<String> dangerousPatterns;

    /** Local working directory path. */
    private String workDir;

    /**
     * LocalWorkConfigBuilder.
     * 
     * @since 0.1.7
     */
    public static class LocalWorkConfigBuilder {
        /**
         * restrictToSandbox.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public LocalWorkConfigBuilder restrictToSandbox(boolean value) {
            return this.isRestrictToSandbox(value);
        }
    }
}
