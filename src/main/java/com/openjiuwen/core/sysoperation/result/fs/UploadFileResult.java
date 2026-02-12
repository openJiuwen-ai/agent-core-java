// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for upload file operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.UploadFileResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class UploadFileResult extends BaseResult<UploadFileData> {

    public UploadFileResult(int code, String message, UploadFileData data) {
        super(code, message, data);
    }

    public static UploadFileResult success(UploadFileData data) {
        return new UploadFileResult(0, "success", data);
    }

    public static UploadFileResult failure(int code, String message) {
        return new UploadFileResult(code, message, null);
    }
}

