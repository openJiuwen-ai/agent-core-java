/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data structure for list files and list directories.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSystemData {
    private int totalCount;

    /** List of file/directory details. */
    private List<FileSystemItem> listItems;

    /** Original input directory path. */
    private String rootPath;

    /** Actual recursive status used. */
    private boolean recursive;

    /** Actual maximum recursion depth used. */
    private Integer maxDepth;
}
