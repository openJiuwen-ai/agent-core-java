/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.base.BaseOperation;
import com.openjiuwen.core.sysop.base.OperationMode;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.WriteFileData;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import com.openjiuwen.core.sysop.result.FileSystemData;
import com.openjiuwen.core.sysop.result.FileSystemItem;
import com.openjiuwen.core.sysop.result.ListFilesResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Local file system operation implementation.
 *
 * <p>Mirrors Python's {@code FsOperation} in
 * {@code openjiuwen.core.sys_operation.local.fs_operation}.</p>
 */
public class FsOperation extends BaseOperation implements BaseFsProtocal {

    /**
     * Create FsOperation.
     *
     * @param runConfig run configuration
     */
    public FsOperation(Object runConfig) {
        super("fs", OperationMode.LOCAL, "local file system operation", runConfig);
    }

    @Override
    public CompletableFuture<Object> readFile(
            String path,
            String mode,
            Integer head,
            Integer tail,
            String encoding,
            int chunkSize,
            Map<String, Object> options
    ) {
        try {
            Path filePath = Paths.get(path);
            String content = Files.readString(filePath);

            // Apply head/tail limits
            if (head != null && head > 0) {
                String[] lines = content.split("\n");
                content = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, Math.min(head, lines.length)));
            }
            if (tail != null && tail > 0) {
                String[] lines = content.split("\n");
                content = String.join("\n", java.util.Arrays.copyOfRange(lines, Math.max(0, lines.length - tail), lines.length));
            }

            ReadFileData data = ReadFileData.builder()
                    .path(path)
                    .content(content)
                    .mode(mode)
                    .build();

            return CompletableFuture.completedFuture(ReadFileResult.success(data));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(ReadFileResult.failure(e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<Object> writeFile(
            String path,
            Object content,
            String mode,
            String encoding,
            Map<String, Object> options
    ) {
        try {
            Path filePath = Paths.get(path);
            String contentStr = content instanceof byte[] bytes
                    ? new String((byte[]) content, encoding != null ? encoding : "UTF-8")
                    : String.valueOf(content);

            Files.writeString(filePath, contentStr);

            WriteFileData data = WriteFileData.builder()
                    .path(path)
                    .size(contentStr.length())
                    .mode(mode)
                    .build();

            return CompletableFuture.completedFuture(WriteFileResult.success(data));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(WriteFileResult.failure(e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<Object> listFiles(
            String path,
            String pattern,
            Map<String, Object> options
    ) {
        try {
            Path dirPath = Paths.get(path);
            List<String> files = Files.list(dirPath)
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .filter(name -> pattern == null || name.matches(pattern))
                    .collect(Collectors.toList());

            List<FileSystemItem> items = files.stream()
                    .map(f -> FileSystemItem.builder().name(f).type("file").build())
                    .collect(Collectors.toList());

            FileSystemData data = FileSystemData.builder()
                    .totalCount(items.size())
                    .listItems(items)
                    .rootPath(path)
                    .build();

            return CompletableFuture.completedFuture(ListFilesResult.success(data));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(ListFilesResult.failure(e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<Object> listDirectories(
            String path,
            Map<String, Object> options
    ) {
        try {
            Path dirPath = Paths.get(path);
            List<String> dirs = Files.list(dirPath)
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());

            List<FileSystemItem> items = dirs.stream()
                    .map(d -> FileSystemItem.builder().name(d).type("directory").build())
                    .collect(Collectors.toList());

            FileSystemData data = FileSystemData.builder()
                    .totalCount(items.size())
                    .listItems(items)
                    .rootPath(path)
                    .build();

            return CompletableFuture.completedFuture(ListFilesResult.success(data));
        } catch (IOException e) {
            return CompletableFuture.completedFuture(ListFilesResult.failure(e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<Object> searchFiles(
            String path,
            String pattern,
            Map<String, Object> options
    ) {
        // Search files recursively
        return listFiles(path, pattern, options);
    }

    @Override
    public List<ToolCard> listTools() {
        return List.of();
    }
}