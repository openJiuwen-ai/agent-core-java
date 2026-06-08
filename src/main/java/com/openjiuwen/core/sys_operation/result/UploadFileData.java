/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code UploadFileData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileData {

    private String localPath;
    private String targetPath;
    private int size;
}
