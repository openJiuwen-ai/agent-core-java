// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for write file operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.WriteFileResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class WriteFileResult extends BaseResult<WriteFileData> {

    public WriteFileResult(int code, String message, WriteFileData data) {
        super(code, message, data);
    }

    public static WriteFileResult success(WriteFileData data) {
        return new WriteFileResult(0, "success", data);
    }

    public static WriteFileResult failure(int code, String message) {
        return new WriteFileResult(code, message, null);
    }
}

