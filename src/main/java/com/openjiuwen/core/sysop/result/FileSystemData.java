/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Backward-compatible file-system data payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code FileSystemData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.FileSystemData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class FileSystemData {

    private int totalCount;
    private List<FileSystemItem> listItems;
    private String rootPath;
    private boolean recursive;
    private Integer maxDepth;
}
