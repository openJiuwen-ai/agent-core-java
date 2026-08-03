/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sys_operation.result.DownloadFileResult;
import com.openjiuwen.core.sys_operation.result.DownloadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.ListDirsResult;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.core.sys_operation.result.ReadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.result.UploadFileResult;
import com.openjiuwen.core.sys_operation.result.UploadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.WriteFileResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;

/**
 * Base file-system operation contract.
 *
 * <p>Mirrors Python's {@code BaseFsOperation} in
 * {@code openjiuwen/core/sys_operation/fs.py}.</p>
 */
public abstract class BaseFsOperation extends BaseOperation {

    public static final Pattern SAFE_PATH_PATTERN = Pattern.compile("[^\\w.-]");
    public static final int DEFAULT_READ_CHUNK_SIZE = 0;
    public static final int DEFAULT_UPLOAD_CHUNK_SIZE = 0;
    public static final int DEFAULT_DOWNLOAD_CHUNK_SIZE = 0;
    public static final int DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;
    public static final int DEFAULT_UPLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;
    public static final int DEFAULT_READ_STREAM_CHUNK_SIZE = 8192;
    public static final int TAIL_CHUNK_SIZE = 1024;

    protected BaseFsOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public List<ToolCard> listTools() {
        return generateToolCards(List.of(
                "read_file",
                "read_file_stream",
                "write_file",
                "upload_file",
                "upload_file_stream",
                "download_file",
                "download_file_stream",
                "list_files",
                "list_directories",
                "search_files"
        ));
    }

    public abstract CompletableFuture<ReadFileResult> readFile(
            String path,
            FileMode mode,
            Integer head,
            Integer tail,
            BaseFsProtocal.LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options);

    public abstract Flow.Publisher<ReadFileStreamResult> readFileStream(
            String path,
            FileMode mode,
            Integer head,
            Integer tail,
            BaseFsProtocal.LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options);

    public abstract CompletableFuture<WriteFileResult> writeFile(
            String path,
            String content,
            FileMode mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options);

    public abstract CompletableFuture<WriteFileResult> writeFile(
            String path,
            byte[] content,
            FileMode mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options);

    public abstract CompletableFuture<UploadFileResult> uploadFile(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract Flow.Publisher<UploadFileStreamResult> uploadFileStream(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract CompletableFuture<DownloadFileResult> downloadFile(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract Flow.Publisher<DownloadFileStreamResult> downloadFileStream(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract CompletableFuture<ListFilesResult> listFiles(
            String path,
            boolean recursive,
            Integer maxDepth,
            SortBy sortBy,
            boolean sortDescending,
            List<String> fileTypes,
            Map<String, Object> options);

    public abstract CompletableFuture<ListDirsResult> listDirectories(
            String path,
            boolean recursive,
            Integer maxDepth,
            SortBy sortBy,
            boolean sortDescending,
            Map<String, Object> options);

    public abstract CompletableFuture<SearchFilesResult> searchFiles(
            String path,
            String pattern,
            List<String> excludePatterns);

    /**
     * Mirrors Python's file mode literals in
     * {@code openjiuwen/core/sys_operation/fs.py}.
     */
    public enum FileMode {
        TEXT("text"),
        BYTES("bytes");

        private final String value;

        FileMode(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static FileMode fromValue(String value) {
            if (value == null) {
                return TEXT;
            }
            for (FileMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
            return TEXT;
        }
    }

    /**
     * Mirrors Python's sort literals in
     * {@code openjiuwen/core/sys_operation/fs.py}.
     */
    public enum SortBy {
        NAME("name"),
        MODIFIED_TIME("modified_time"),
        SIZE("size");

        private final String value;

        SortBy(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static SortBy fromValue(String value) {
            if (value == null) {
                return NAME;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (SortBy sortBy : values()) {
                if (sortBy.value.equals(normalized)) {
                    return sortBy;
                }
            }
            return NAME;
        }
    }
}
