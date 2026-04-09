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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalWorkConfig {

    /**
     * List of allowed command prefixes.
     * If null, all commands are allowed (warning: insecure).
     */
    @Builder.Default
    private List<String> shellAllowlist = Arrays.asList(
            "echo", "ls", "dir", "cd", "pwd", "python", "python3", "pip", "pip3",
            "npm", "node", "git", "cat", "type", "mkdir", "md", "rm", "rd",
            "cp", "copy", "mv", "move", "grep", "find", "curl", "wget", "ps", "df", "ping"
    );

    /** Local working directory path. */
    private String workDir;
}
