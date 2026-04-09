  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure for write file operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WriteFileData {

    /** File path of the write file. */
    private String path;

    /** File content size in bytes. */
    private int size;

    /** File write mode: "text" or "bytes". */
    private String mode;
}
