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
 * Backward-compatible search-files data payload for moved sys-operation results.
 *
 * <p>Mirrors Python's {@code SearchFilesData} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.SearchFilesData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(since = "0.1.14", forRemoval = false)
public class SearchFilesData {

    private int totalMatches;
    private List<FileSystemItem> matchingFiles;
    private String searchPath;
    private String searchPattern;
    private List<String> excludePatterns;
}
