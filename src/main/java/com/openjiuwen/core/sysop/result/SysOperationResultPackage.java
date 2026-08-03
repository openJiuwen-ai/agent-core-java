/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for the sysop result exports (backward-compatible).
 *
 * <p>Mirrors Python's {@code openjiuwen/core/sys_operation/result/__init__.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.result.SysOperationResultPackage}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public final class SysOperationResultPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/sys_operation/result/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
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
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private SysOperationResultPackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("BaseResult", BaseResult.class);
        exports.put("ExecuteCodeData", ExecuteCodeData.class);
        exports.put("ExecuteCodeChunkData", ExecuteCodeChunkData.class);
        exports.put("ExecuteCodeResult", ExecuteCodeResult.class);
        exports.put("ExecuteCodeStreamResult", ExecuteCodeStreamResult.class);
        exports.put("ReadFileData", ReadFileData.class);
        exports.put("ReadFileChunkData", ReadFileChunkData.class);
        exports.put("WriteFileData", WriteFileData.class);
        exports.put("UploadFileData", UploadFileData.class);
        exports.put("UploadFileChunkData", UploadFileChunkData.class);
        exports.put("DownloadFileData", DownloadFileData.class);
        exports.put("DownloadFileChunkData", DownloadFileChunkData.class);
        exports.put("FileSystemItem", FileSystemItem.class);
        exports.put("FileSystemData", FileSystemData.class);
        exports.put("SearchFilesData", SearchFilesData.class);
        exports.put("ReadFileResult", ReadFileResult.class);
        exports.put("ReadFileStreamResult", ReadFileStreamResult.class);
        exports.put("WriteFileResult", WriteFileResult.class);
        exports.put("UploadFileResult", UploadFileResult.class);
        exports.put("UploadFileStreamResult", UploadFileStreamResult.class);
        exports.put("DownloadFileResult", DownloadFileResult.class);
        exports.put("DownloadFileStreamResult", DownloadFileStreamResult.class);
        exports.put("ListFilesResult", ListFilesResult.class);
        exports.put("ListDirsResult", ListDirsResult.class);
        exports.put("SearchFilesResult", SearchFilesResult.class);
        exports.put("ExecuteCmdData", ExecuteCmdData.class);
        exports.put("ExecuteCmdChunkData", ExecuteCmdChunkData.class);
        exports.put("ExecuteCmdBackgroundData", ExecuteCmdBackgroundData.class);
        exports.put("ExecuteCmdResult", ExecuteCmdResult.class);
        exports.put("ExecuteCmdStreamResult", ExecuteCmdStreamResult.class);
        exports.put("ExecuteCmdBackgroundResult", ExecuteCmdBackgroundResult.class);
        return Map.copyOf(exports);
    }
}
