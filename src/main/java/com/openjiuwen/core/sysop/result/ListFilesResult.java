/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ListFilesResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class ListFilesResult extends BaseResult<FileSystemData> {

    public ListFilesResult() {
    }

    public ListFilesResult(int code, String message, FileSystemData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
