/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure for background shell command execution.
 * <p>
 * Mirrors Python's {@code ExecuteCmdBackgroundData} from
 * <code>openjiuwen/core/sys_operation/result.py</code>.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCmdBackgroundData {

    /** Original shell command executed. */
    private String command;

    /** Current working directory. */
    @Builder.Default
    private String cwd = ".";

    /** Process ID of the background process. */
    private Long pid;
}
