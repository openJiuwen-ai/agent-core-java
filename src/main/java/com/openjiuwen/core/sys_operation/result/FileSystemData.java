/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.result;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code FileSystemData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSystemData {

    private int totalCount;
    private List<FileSystemItem> listItems;
    private String rootPath;
    private boolean recursive;
    private Integer maxDepth;
}
