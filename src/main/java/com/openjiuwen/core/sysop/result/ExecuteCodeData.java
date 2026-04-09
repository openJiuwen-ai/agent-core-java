/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Code execution result data model.
 * <p>
 * Mirrors Python's {@code ExecuteCodeData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeData {

    /** Original code executed. */
    private String codeContent;

    /** Programming language of the original code. */
    private String language;

    /** Execution exit code. */
    private Integer exitCode;

    /** Standard output stream. */
    @Builder.Default
    private String stdout = "";

    /** Standard error stream. */
    @Builder.Default
    private String stderr = "";
}
