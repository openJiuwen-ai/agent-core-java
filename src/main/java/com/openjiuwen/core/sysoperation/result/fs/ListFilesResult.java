// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for list files operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.ListFilesResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ListFilesResult extends BaseResult<FileSystemData> {

    public ListFilesResult(int code, String message, FileSystemData data) {
        super(code, message, data);
    }

    public static ListFilesResult success(FileSystemData data) {
        return new ListFilesResult(0, "success", data);
    }

    public static ListFilesResult failure(int code, String message) {
        return new ListFilesResult(code, message, null);
    }
}

