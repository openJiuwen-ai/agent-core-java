// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for download file operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.DownloadFileResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class DownloadFileResult extends BaseResult<DownloadFileData> {

    public DownloadFileResult(int code, String message, DownloadFileData data) {
        super(code, message, data);
    }

    public static DownloadFileResult success(DownloadFileData data) {
        return new DownloadFileResult(0, "success", data);
    }

    public static DownloadFileResult failure(int code, String message) {
        return new DownloadFileResult(code, message, null);
    }
}

