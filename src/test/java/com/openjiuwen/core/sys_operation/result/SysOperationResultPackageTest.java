/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's package surface in
 * {@code openjiuwen/core/sys_operation/result/__init__.py}.
 */
class SysOperationResultPackageTest {

    @Test
    void exposesPythonPackageBridge() {
        assertEquals(
                "openjiuwen/core/sys_operation/result/__init__.py",
                SysOperationResultPackage.PYTHON_MODULE
        );
        assertIterableEquals(
                List.of(
                        "BaseResult",
                        "ExecuteCodeData",
                        "ExecuteCodeChunkData",
                        "ExecuteCodeResult",
                        "ExecuteCodeStreamResult",
                        "ReadFileData",
                        "ReadFileChunkData",
                        "WriteFileData",
                        "UploadFileData",
                        "UploadFileChunkData",
                        "DownloadFileData",
                        "DownloadFileChunkData",
                        "FileSystemItem",
                        "FileSystemData",
                        "SearchFilesData",
                        "ReadFileResult",
                        "ReadFileStreamResult",
                        "WriteFileResult",
                        "UploadFileResult",
                        "UploadFileStreamResult",
                        "DownloadFileResult",
                        "DownloadFileStreamResult",
                        "ListFilesResult",
                        "ListDirsResult",
                        "SearchFilesResult",
                        "ExecuteCmdData",
                        "ExecuteCmdChunkData",
                        "ExecuteCmdBackgroundData",
                        "ExecuteCmdResult",
                        "ExecuteCmdStreamResult",
                        "ExecuteCmdBackgroundResult"
                ),
                SysOperationResultPackage.EXPORTED_SYMBOLS
        );
        assertSame(BaseResult.class, SysOperationResultPackage.EXPORTED_TYPES.get("BaseResult"));
        assertSame(ReadFileData.class, SysOperationResultPackage.EXPORTED_TYPES.get("ReadFileData"));
        assertSame(SearchFilesResult.class, SysOperationResultPackage.EXPORTED_TYPES.get("SearchFilesResult"));
        assertSame(ExecuteCmdBackgroundResult.class,
                SysOperationResultPackage.EXPORTED_TYPES.get("ExecuteCmdBackgroundResult"));
    }
}
