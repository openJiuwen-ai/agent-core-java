/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code ListDirsResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class ListDirsResult extends BaseResult<FileSystemData> {

    public ListDirsResult() {
    }

    public ListDirsResult(int code, String message, FileSystemData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
