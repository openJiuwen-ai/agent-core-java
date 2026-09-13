/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.DownloadFileChunkData;
import com.openjiuwen.core.sysop.result.DownloadFileData;
import com.openjiuwen.core.sysop.result.DownloadFileResult;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.FileSystemData;
import com.openjiuwen.core.sysop.result.FileSystemItem;
import com.openjiuwen.core.sysop.result.ListDirsResult;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileChunkData;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.SearchFilesData;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.UploadFileChunkData;
import com.openjiuwen.core.sysop.result.UploadFileData;
import com.openjiuwen.core.sysop.result.UploadFileResult;
import com.openjiuwen.core.sysop.result.UploadFileStreamResult;
import com.openjiuwen.core.sysop.result.WriteFileData;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.providers.BaseFSProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JiuwenBox sandbox file system provider that extends BaseFSProvider
 * and uses JiuwenBoxProviderMixin for sandbox management.
 * 
 * @since 0.1.7
 */
public class JiuwenBoxFSProvider extends BaseFSProvider {
    private final JiuwenBoxProviderMixin mixin;

    /**
     * JiuwenBoxFSProvider.
     * 
     * @param endpoint endpoint
     * @param config config
     * @since 0.1.7
     */
    public JiuwenBoxFSProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.mixin = new JiuwenBoxProviderMixin(endpoint, config);
    }

    /**
     * readFile.
     * 
     * @param path path
     * @param mode mode
     * @param head head
     * @param tail tail
     * @param lineRange lineRange
     * @param encoding encoding
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange,
            String encoding, int chunkSize, Map<String, Object> options) {
        return mixin.executeWithSandboxRetry(sandboxId -> {
            byte[] raw = mixin.getClient().downloadBytes(sandboxId, path);
            Object content;
            if ("text".equals(mode)) {
                String text = new String(raw, StandardCharsets.UTF_8);
                String[] lines = text.split("\n", -1);
                List<String> selected = applyLineSelection(lines, head, tail, lineRange);
                content = String.join("\n", selected);
            } else {
                content = raw;
            }
            ReadFileData data = ReadFileData.builder().path(path).content(content).mode(mode).build();
            return new ReadFileResult(0, "success", data);
        });
    }

    /**
     * readFileStream.
     * 
     * @param path path
     * @param mode mode
     * @param head head
     * @param tail tail
     * @param lineRange lineRange
     * @param encoding encoding
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail,
            int[] lineRange, String encoding, int chunkSize, Map<String, Object> options) {
        ReadFileResult fullResult = readFile(path, mode, head, tail, lineRange, encoding, chunkSize, options);
        ReadFileData fullData = fullResult.getData();
        if ("text".equals(mode)) {
            String text = fullData.getContentAsString();
            String[] lines = text.split("\n", -1);
            int effectiveChunkSize = chunkSize > 0 ? chunkSize : 4096;
            int totalChunks = Math.max(1, (int) Math.ceil((double) lines.length / effectiveChunkSize));
            List<ReadFileStreamResult> chunks = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                int from = i * effectiveChunkSize;
                int to = Math.min(from + effectiveChunkSize, lines.length);
                String chunkText = String.join("\n", Arrays.copyOfRange(lines, from, to));
                ReadFileChunkData chunkData = ReadFileChunkData.builder().path(path).chunkContent(chunkText).mode(mode)
                        .chunkSize(effectiveChunkSize).chunkIndex(i).lastChunk(i == totalChunks - 1).build();
                chunks.add(new ReadFileStreamResult(0, "success", chunkData));
            }
            return chunks.iterator();
        } else {
            byte[] bytes = fullData.getContentAsBytes();
            int effectiveChunkSize = chunkSize > 0 ? chunkSize : 8192;
            int totalChunks = Math.max(1, (int) Math.ceil((double) bytes.length / effectiveChunkSize));
            List<ReadFileStreamResult> chunks = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                int from = i * effectiveChunkSize;
                int to = Math.min(from + effectiveChunkSize, bytes.length);
                byte[] chunkBytes = Arrays.copyOfRange(bytes, from, to);
                ReadFileChunkData chunkData = ReadFileChunkData.builder().path(path).chunkContent(chunkBytes).mode(mode)
                        .chunkSize(effectiveChunkSize).chunkIndex(i).lastChunk(i == totalChunks - 1).build();
                chunks.add(new ReadFileStreamResult(0, "success", chunkData));
            }
            return chunks.iterator();
        }
    }

    /**
     * writeFile.
     * 
     * @param path path
     * @param content content
     * @param mode mode
     * @param isPrependNewline isPrependNewline
     * @param isAppendNewline isAppendNewline
     * @param isCreateIfMissing isCreateIfMissing
     * @param permissions permissions
     * @param encoding encoding
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public WriteFileResult writeFile(String path, Object content, String mode, boolean isPrependNewline,
            boolean isAppendNewline, boolean isCreateIfMissing, String permissions, String encoding,
            Map<String, Object> options) {
        Object appendObj = options != null ? options.get("append") : null;
        boolean isAppend = appendObj instanceof Boolean isAppendBool && isAppendBool;
        return mixin.executeWithSandboxRetry(sandboxId -> {
            byte[] contentBytes;
            if (content instanceof byte[] b) {
                contentBytes = b;
            } else if (content instanceof String s) {
                if ("text".equals(mode)) {
                    contentBytes = s.getBytes(StandardCharsets.UTF_8);
                } else {
                    contentBytes = s.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                contentBytes = String.valueOf(content).getBytes(StandardCharsets.UTF_8);
            }
            byte[] finalBytes = contentBytes;
            if (isPrependNewline && isAppend) {
                finalBytes = concat("\n".getBytes(StandardCharsets.UTF_8), contentBytes);
            }
            if (isAppendNewline) {
                finalBytes = concat(finalBytes, "\n".getBytes(StandardCharsets.UTF_8));
            }
            if (isPrependNewline && !isAppend) {
                finalBytes = concat("\n".getBytes(StandardCharsets.UTF_8), finalBytes);
            }
            if (isAppend) {
                mixin.getClient().appendBytes(sandboxId, path, finalBytes);
            } else {
                mixin.getClient().uploadBytes(sandboxId, path, finalBytes);
            }
            WriteFileData data = WriteFileData.builder().path(path).size(finalBytes.length).mode(mode).build();
            return new WriteFileResult(0, "success", data);
        });
    }

    /**
     * uploadFile.
     * 
     * @param localPath localPath
     * @param targetPath targetPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public UploadFileResult uploadFile(String localPath, String targetPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        return mixin.executeWithSandboxRetry(sandboxId -> {
            try {
                boolean isExisting = mixin.getClient().pathExists(sandboxId, targetPath);
                byte[] content = Files.readAllBytes(Path.of(localPath));
                mixin.getClient().uploadBytes(sandboxId, targetPath, content);
                UploadFileData data =
                    UploadFileData.builder().localPath(localPath).targetPath(targetPath).size(content.length).build();
                return new UploadFileResult(0, "success", data);
            } catch (IOException e) {
                throw new SandboxOperationException("uploadFile IO error", e);
            }
        });
    }

    /**
     * uploadFileStream.
     * 
     * @param localPath localPath
     * @param targetPath targetPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        try {
            byte[] allBytes = Files.readAllBytes(Path.of(localPath));
            int effectiveChunkSize = chunkSize > 0 ? chunkSize : 8192;
            int totalChunks = Math.max(1, (int) Math.ceil((double) allBytes.length / effectiveChunkSize));
            List<UploadFileStreamResult> chunks = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                int from = i * effectiveChunkSize;
                int to = Math.min(from + effectiveChunkSize, allBytes.length);
                byte[] chunk = Arrays.copyOfRange(allBytes, from, to);
                boolean isFirst = i == 0;
                mixin.executeWithSandboxRetry(sandboxId -> {
                    if (isFirst) {
                        mixin.getClient().uploadBytes(sandboxId, targetPath, chunk);
                    } else {
                        mixin.getClient().appendBytes(sandboxId, targetPath, chunk);
                    }
                    return null;
                });
                UploadFileChunkData chunkData =
                    UploadFileChunkData.builder().localPath(localPath).targetPath(targetPath).chunkSize(chunk.length)
                            .chunkIndex(i).lastChunk(i == totalChunks - 1).build();
                chunks.add(new UploadFileStreamResult(0, "success", chunkData));
            }
            return chunks.iterator();
        } catch (SandboxOperationException e) {
            throw e;
        } catch (IOException | SandboxRecreateExhaustedException e) {
            throw new SandboxOperationException("uploadFileStream failed", e);
        }
    }

    /**
     * downloadFile.
     * 
     * @param sourcePath sourcePath
     * @param localPath localPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public DownloadFileResult downloadFile(String sourcePath, String localPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        return mixin.executeWithSandboxRetry(sandboxId -> {
            try {
                byte[] content = mixin.getClient().downloadBytes(sandboxId, sourcePath);
                Files.write(Path.of(localPath), content);
                DownloadFileData data =
                    DownloadFileData.builder().sourcePath(sourcePath).localPath(localPath).size(content.length).build();
                return new DownloadFileResult(0, "success", data);
            } catch (IOException e) {
                throw new SandboxOperationException("downloadFile IO error", e);
            }
        });
    }

    /**
     * downloadFileStream.
     * 
     * @param sourcePath sourcePath
     * @param localPath localPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
            boolean isOverwrite, boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize,
            Map<String, Object> options) {
        try {
            byte[] allBytes =
                mixin.executeWithSandboxRetry(sandboxId -> mixin.getClient().downloadBytes(sandboxId, sourcePath));
            int effectiveChunkSize = chunkSize > 0 ? chunkSize : 8192;
            int totalChunks = Math.max(1, (int) Math.ceil((double) allBytes.length / effectiveChunkSize));
            Path localFilePath = Path.of(localPath);
            Files.createDirectories(localFilePath.getParent());
            List<DownloadFileStreamResult> chunks = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                int from = i * effectiveChunkSize;
                int to = Math.min(from + effectiveChunkSize, allBytes.length);
                byte[] chunk = Arrays.copyOfRange(allBytes, from, to);
                if (i == 0) {
                    Files.write(localFilePath, chunk);
                } else {
                    Files.write(localFilePath, chunk, java.nio.file.StandardOpenOption.APPEND);
                }
                DownloadFileChunkData chunkData =
                    DownloadFileChunkData.builder().sourcePath(sourcePath).localPath(localPath).chunkSize(chunk.length)
                            .chunkIndex(i).lastChunk(i == totalChunks - 1).build();
                chunks.add(new DownloadFileStreamResult(0, "success", chunkData));
            }
            return chunks.iterator();
        } catch (SandboxOperationException e) {
            throw e;
        } catch (IOException | SandboxRecreateExhaustedException e) {
            throw new SandboxOperationException("downloadFileStream failed", e);
        }
    }

    /**
     * listFiles.
     * 
     * @param path path
     * @param isRecursive isRecursive
     * @param maxDepth maxDepth
     * @param sortBy sortBy
     * @param isSortDescending isSortDescending
     * @param fileTypes fileTypes
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ListFilesResult listFiles(String path, boolean isRecursive, Integer maxDepth, String sortBy,
            boolean isSortDescending, List<String> fileTypes, Map<String, Object> options) {
        return mixin.executeWithSandboxRetry(sandboxId -> {
            List<Map<String, Object>> items =
                mixin.getClient().listFiles(sandboxId, path, isRecursive, maxDepth, true, false);
            items.sort(Comparator.comparing(m -> String.valueOf(m.get("path"))));
            List<FileSystemItem> fsItems = mapToFileSystemItems(items);
            FileSystemData data = FileSystemData.builder().totalCount(fsItems.size()).listItems(fsItems).rootPath(path)
                    .recursive(isRecursive).maxDepth(maxDepth).build();
            return new ListFilesResult(0, "success", data);
        });
    }

    /**
     * listDirectories.
     * 
     * @param path path
     * @param isRecursive isRecursive
     * @param maxDepth maxDepth
     * @param sortBy sortBy
     * @param isSortDescending isSortDescending
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ListDirsResult listDirectories(String path, boolean isRecursive, Integer maxDepth, String sortBy,
            boolean isSortDescending, Map<String, Object> options) {
        return mixin.executeWithSandboxRetry(sandboxId -> {
            List<Map<String, Object>> items =
                mixin.getClient().listFiles(sandboxId, path, isRecursive, maxDepth, false, true);
            items.sort(Comparator.comparing(m -> String.valueOf(m.get("path"))));
            List<FileSystemItem> fsItems = mapToFileSystemItems(items);
            FileSystemData data = FileSystemData.builder().totalCount(fsItems.size()).listItems(fsItems).rootPath(path)
                    .recursive(isRecursive).maxDepth(maxDepth).build();
            return new ListDirsResult(0, "success", data);
        });
    }

    /**
     * searchFiles.
     * 
     * @param path path
     * @param pattern pattern
     * @param excludePatterns excludePatterns
     * @return the result
     * @since 0.1.7
     */
    @Override
    public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns) {
        return mixin.executeWithSandboxRetry(sandboxId -> {
            List<Map<String, Object>> items = mixin.getClient().searchFiles(sandboxId, path, pattern, excludePatterns);
            items.sort(Comparator.comparing(m -> String.valueOf(m.get("path"))));
            List<FileSystemItem> fsItems = mapToFileSystemItems(items);
            SearchFilesData data = SearchFilesData.builder().totalMatches(fsItems.size()).matchingFiles(fsItems)
                    .searchPath(path).searchPattern(pattern).excludePatterns(excludePatterns).build();
            return new SearchFilesResult(0, "success", data);
        });
    }

    /**
     * applyLineSelection.
     * 
     * @param lines lines
     * @param head head
     * @param tail tail
     * @param lineRange lineRange
     * @return the result
     * @since 0.1.7
     */
    private List<String> applyLineSelection(String[] lines, Integer head, Integer tail, int[] lineRange) {
        List<String> selected = new ArrayList<>(List.of(lines));
        if (head != null && head > 0) {
            selected = selected.subList(0, Math.min(head, selected.size()));
        } else if (tail != null && tail > 0) {
            int start = Math.max(0, selected.size() - tail);
            selected = selected.subList(start, selected.size());
        } else if (lineRange != null && lineRange.length >= 2) {
            int start = lineRange[0];
            int end = Math.min(lineRange[1] + 1, selected.size());
            if (start < selected.size()) {
                selected = selected.subList(start, end);
            } else {
                selected = new ArrayList<>();
            }
        }
        return selected;
    }

    /**
     * mapToFileSystemItems.
     * 
     * @param items items
     * @return the result
     * @since 0.1.7
     */
    private List<FileSystemItem> mapToFileSystemItems(List<Map<String, Object>> items) {
        List<FileSystemItem> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            FileSystemItem fsItem = FileSystemItem.builder().name(stringValue(item, "name").orElse(null))
                    .path(stringValue(item, "path").orElse(null)).size(longValue(item, "size"))
                    .modifiedTime(stringValue(item, "modified_time").orElse(null))
                    .directory(booleanValue(item, "is_dir")).type(stringValue(item, "type").orElse(null)).build();
            result.add(fsItem);
        }
        return result;
    }

    /**
     * concat.
     * 
     * @param a a
     * @param b b
     * @return the result
     * @since 0.1.7
     */
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * stringValue.
     * 
     * @param map map
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    private static Optional<String> stringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? Optional.of(String.valueOf(val)) : Optional.empty();
    }

    /**
     * longValue.
     * 
     * @param map map
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    private static long longValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    /**
     * booleanValue.
     * 
     * @param map map
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    private static boolean booleanValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean isBoolVal) {
            return isBoolVal;
        }
        return false;
    }
}
