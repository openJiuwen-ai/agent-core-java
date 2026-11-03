/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

/**
 * Mirrors Python's {@code DownloadFileResult} in
 * {@code openjiuwen/core/sys_operation/result/fs_operation_result.py}.
 */
public class DownloadFileResult extends BaseResult<DownloadFileData> {

    public DownloadFileResult() {
    }

    public DownloadFileResult(int code, String message, DownloadFileData data) {
        setCode(code);
        setMessage(message);
        setData(data);
    }
}
