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
 * Data structure for list files.
 *
 * <p>Mirrors Python's {@code ListFilesResult} in
 * {@code openjiuwen.core.sys_operation.result.fs_operation_result}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListFilesData {

    /** Directory path. */
    private String path;

    /** List of file names. */
    private List<String> files;
}