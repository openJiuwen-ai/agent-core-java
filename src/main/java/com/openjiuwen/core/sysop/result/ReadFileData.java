/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure for read file operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadFileData {

    /** File path of the read file. */
    private String path;

    /** File content (text string or binary bytes represented as string). */
    private String content;

    /** File read mode: "text" or "bytes". */
    private String mode;
}
