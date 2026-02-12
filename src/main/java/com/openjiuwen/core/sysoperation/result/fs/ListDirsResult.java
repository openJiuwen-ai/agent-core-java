// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for list directories operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.ListDirsResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ListDirsResult extends BaseResult<FileSystemData> {

    public ListDirsResult(int code, String message, FileSystemData data) {
        super(code, message, data);
    }

    public static ListDirsResult success(FileSystemData data) {
        return new ListDirsResult(0, "success", data);
    }

    public static ListDirsResult failure(int code, String message) {
        return new ListDirsResult(code, message, null);
    }
}

