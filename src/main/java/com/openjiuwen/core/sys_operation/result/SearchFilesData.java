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
 * Mirrors Python's {@code SearchFilesData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFilesData {

    private int totalMatches;
    private List<FileSystemItem> matchingFiles;
    private String searchPath;
    private String searchPattern;
    private List<String> excludePatterns;
}
