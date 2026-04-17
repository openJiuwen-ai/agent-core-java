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
 * Data structure for search files.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFilesData {

    /** Total number of matching files. */
    private int totalMatches;

    /** List of matching files. */
    private List<FileSystemItem> matchingFiles;

    /** Original base path used for the search. */
    private String searchPath;

    /** Original search pattern used. */
    private String searchPattern;

    /** Original exclude patterns used. */
    private List<String> excludePatterns;
}
