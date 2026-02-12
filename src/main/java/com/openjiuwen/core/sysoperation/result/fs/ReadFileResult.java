// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.BaseResult;

/**
 * Result wrapper for read file operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.ReadFileResult
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ReadFileResult extends BaseResult<ReadFileData> {

    public ReadFileResult(int code, String message, ReadFileData data) {
        super(code, message, data);
    }

    public static ReadFileResult success(ReadFileData data) {
        return new ReadFileResult(0, "success", data);
    }

    public static ReadFileResult failure(int code, String message) {
        return new ReadFileResult(code, message, null);
    }
}

