/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure for shell command execution.
 * <p>
 * Mirrors Python's {@code ExecuteCmdData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCmdData {

    /** Original shell command executed. */
    private String command;

    /** Current working directory. */
    @Builder.Default
    private String cwd = ".";

    /** Command exit code. */
    private Integer exitCode;

    /** Standard output stream. */
    @Builder.Default
    private String stdout = "";

    /** Standard error stream. */
    @Builder.Default
    private String stderr = "";
}
