// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Local working configuration.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.config.LocalWorkConfig
 * 
 * <p>Configuration for local mode system operations, including:
 * <ul>
 *   <li>{@code shellAllowlist} - List of allowed command prefixes for shell operations</li>
 *   <li>{@code workDir} - Local working directory path</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class LocalWorkConfig {
    
    /**
     * Default shell allowlist containing safe commands.
     */
    private static final List<String> DEFAULT_SHELL_ALLOWLIST = Collections.unmodifiableList(Arrays.asList(
        "echo", "ls", "dir", "cd", "pwd", "python", "python3", "pip", "pip3", 
        "npm", "node", "git", "cat", "type", "mkdir", "md", "rm", "rd", 
        "cp", "copy", "mv", "move", "grep", "find", "curl", "wget", "ps", "df", "ping"
    ));

    /**
     * List of allowed command prefixes. If null, all commands are allowed (warning: insecure).
     */
    private List<String> shellAllowlist;

    /**
     * Local working directory path.
     */
    private String workDir;

    /**
     * Default constructor with default values.
     */
    public LocalWorkConfig() {
        this.shellAllowlist = new ArrayList<>(DEFAULT_SHELL_ALLOWLIST);
        this.workDir = null;
    }

    /**
     * Constructor with all parameters.
     * 
     * @param shellAllowlist list of allowed shell commands (null means allow all)
     * @param workDir local working directory path
     */
    public LocalWorkConfig(List<String> shellAllowlist, String workDir) {
        this.shellAllowlist = shellAllowlist != null ? new ArrayList<>(shellAllowlist) : null;
        this.workDir = workDir;
    }

    /**
     * Gets the shell allowlist.
     * 
     * @return list of allowed shell commands, or null if all commands are allowed
     */
    public List<String> getShellAllowlist() {
        return shellAllowlist;
    }

    /**
     * Sets the shell allowlist.
     * 
     * @param shellAllowlist list of allowed shell commands
     */
    public void setShellAllowlist(List<String> shellAllowlist) {
        this.shellAllowlist = shellAllowlist != null ? new ArrayList<>(shellAllowlist) : null;
    }

    /**
     * Gets the working directory.
     * 
     * @return the working directory path, or null if not set
     */
    public String getWorkDir() {
        return workDir;
    }

    /**
     * Sets the working directory.
     * 
     * @param workDir the working directory path
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    /**
     * Gets the default shell allowlist.
     * 
     * @return unmodifiable list of default allowed commands
     */
    public static List<String> getDefaultShellAllowlist() {
        return DEFAULT_SHELL_ALLOWLIST;
    }

    /**
     * Creates a new Builder instance.
     * 
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for LocalWorkConfig.
     */
    public static class Builder {
        private List<String> shellAllowlist = new ArrayList<>(DEFAULT_SHELL_ALLOWLIST);
        private String workDir;

        /**
         * Sets the shell allowlist.
         * 
         * @param shellAllowlist list of allowed shell commands
         * @return this builder
         */
        public Builder shellAllowlist(List<String> shellAllowlist) {
            this.shellAllowlist = shellAllowlist != null ? new ArrayList<>(shellAllowlist) : null;
            return this;
        }

        /**
         * Sets the working directory.
         * 
         * @param workDir the working directory path
         * @return this builder
         */
        public Builder workDir(String workDir) {
            this.workDir = workDir;
            return this;
        }

        /**
         * Builds the LocalWorkConfig instance.
         * 
         * @return the built LocalWorkConfig
         */
        public LocalWorkConfig build() {
            return new LocalWorkConfig(shellAllowlist, workDir);
        }
    }

    @Override
    public String toString() {
        return "LocalWorkConfig{" +
            "shellAllowlist=" + shellAllowlist +
            ", workDir='" + workDir + '\'' +
            '}';
    }
}

