/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code UploadFileResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class UploadFileResult extends BaseResult<UploadFileData> {

    public UploadFileResult() {
    }

    public UploadFileResult(int code, String message, UploadFileData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
