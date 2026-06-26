/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sys_operation.result.DownloadFileChunkData;
import com.openjiuwen.core.sys_operation.result.DownloadFileData;
import com.openjiuwen.core.sys_operation.result.DownloadFileResult;
import com.openjiuwen.core.sys_operation.result.DownloadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.FileSystemData;
import com.openjiuwen.core.sys_operation.result.FileSystemItem;
import com.openjiuwen.core.sys_operation.result.ListDirsResult;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileChunkData;
import com.openjiuwen.core.sys_operation.result.ReadFileData;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.core.sys_operation.result.ReadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesData;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.result.UploadFileChunkData;
import com.openjiuwen.core.sys_operation.result.UploadFileData;
import com.openjiuwen.core.sys_operation.result.UploadFileResult;
import com.openjiuwen.core.sys_operation.result.UploadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.WriteFileData;
import com.openjiuwen.core.sys_operation.result.WriteFileResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.providers.BaseFsProvider;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Mirrors Python's {@code JiuwenBoxFSProvider} in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/jiuwenbox.py}.
 */
public class JiuwenBoxFsProvider extends BaseFsProvider {

    private final JiuwenBoxProviderSupport.ProviderState state;

    public JiuwenBoxFsProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.state = new JiuwenBoxProviderSupport.ProviderState(endpoint, config);
    }

    @Override
    public CompletableFuture<ReadFileResult> readFile(
            String path,
            String mode,
            Integer head,
            Integer tail,
            LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            String effectiveMode = mode == null || mode.isBlank() ? MODE_TEXT : mode;
            AioProviderSupport.NormalizedReadParams params =
                    AioProviderSupport.normalizeReadParams(head, tail, lineRange);
            Optional<String> validationError = AioProviderSupport.validateReadParams(
                    effectiveMode,
                    params.head(),
                    params.tail(),
                    params.lineRange());
            if (validationError.isPresent()) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "read_file",
                        validationError.get(),
                        ReadFileResult.class);
            }
            try {
                byte[] raw = state.executeWithSandboxRetry(
                        sandboxId -> state.getClient().downloadBytes(sandboxId, path));
                Object content;
                if (MODE_BYTES.equals(effectiveMode)) {
                    content = raw;
                } else {
                    String text = new String(raw, Charset.forName(resolveEncoding(encoding)));
                    AioProviderSupport.SelectedText selectedText = AioProviderSupport.selectTextLines(
                            text,
                            params.head(),
                            params.tail(),
                            params.lineRange());
                    content = AioProviderSupport.joinLines(selectedText.lines());
                }
                ReadFileResult result = new ReadFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(ReadFileData.builder()
                        .path(path)
                        .content(content)
                        .mode(effectiveMode)
                        .build());
                return result;
            } catch (Exception exception) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "read_file",
                        exception.getMessage(),
                        ReadFileResult.class);
            }
        });
    }

    @Override
    public Flow.Publisher<ReadFileStreamResult> readFileStream(
            String path,
            String mode,
            Integer head,
            Integer tail,
            LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options) {
        return AioProviderSupport.asyncPublisher(publisher -> {
            ReadFileResult result = readFile(path, mode, head, tail, lineRange, encoding, chunkSize, options).join();
            if (result.getCode() != StatusCode.SUCCESS.getCode()) {
                ReadFileStreamResult streamResult = new ReadFileStreamResult();
                streamResult.setCode(result.getCode());
                streamResult.setMessage(result.getMessage());
                publisher.submit(streamResult);
                return;
            }
            String effectiveMode = mode == null || mode.isBlank() ? MODE_TEXT : mode;
            Charset charset = Charset.forName(resolveEncoding(encoding));
            Object content = result.getData() == null ? null : result.getData().getContent();
            if (MODE_BYTES.equals(effectiveMode)) {
                byte[] bytes = content instanceof byte[] raw
                        ? raw
                        : String.valueOf(content == null ? "" : content).getBytes(charset);
                int effectiveChunkSize = chunkSize > 0 ? chunkSize : DEFAULT_READ_STREAM_CHUNK_SIZE;
                if (bytes.length == 0) {
                    return;
                }
                for (int index = 0, start = 0; start < bytes.length; index++, start += effectiveChunkSize) {
                    int end = Math.min(bytes.length, start + effectiveChunkSize);
                    byte[] piece = java.util.Arrays.copyOfRange(bytes, start, end);
                    ReadFileStreamResult streamResult = new ReadFileStreamResult();
                    streamResult.setCode(StatusCode.SUCCESS.getCode());
                    streamResult.setMessage(StatusCode.SUCCESS.getErrmsg());
                    streamResult.setData(ReadFileChunkData.builder()
                            .path(path)
                            .chunkContent(piece)
                            .mode(MODE_BYTES)
                            .chunkSize(piece.length)
                            .chunkIndex(index)
                            .isLastChunk(end >= bytes.length)
                            .build());
                    publisher.submit(streamResult);
                }
                return;
            }

            String text = content == null ? "" : String.valueOf(content);
            List<String> lines = splitLines(text);
            if (lines.isEmpty() && shouldEmitEmptyTextChunk(head, tail, lineRange)) {
                ReadFileStreamResult streamResult = new ReadFileStreamResult();
                streamResult.setCode(StatusCode.SUCCESS.getCode());
                streamResult.setMessage(StatusCode.SUCCESS.getErrmsg());
                streamResult.setData(ReadFileChunkData.builder()
                        .path(path)
                        .chunkContent("")
                        .mode(MODE_TEXT)
                        .chunkSize(0)
                        .chunkIndex(0)
                        .isLastChunk(true)
                        .build());
                publisher.submit(streamResult);
                return;
            }
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                ReadFileStreamResult streamResult = new ReadFileStreamResult();
                streamResult.setCode(StatusCode.SUCCESS.getCode());
                streamResult.setMessage(StatusCode.SUCCESS.getErrmsg());
                streamResult.setData(ReadFileChunkData.builder()
                        .path(path)
                        .chunkContent(line)
                        .mode(MODE_TEXT)
                        .chunkSize(line.getBytes(charset).length)
                        .chunkIndex(index)
                        .isLastChunk(index == lines.size() - 1)
                        .build());
                publisher.submit(streamResult);
            }
        });
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(
            String path,
            String content,
            String mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options) {
        String effectiveMode = mode == null || mode.isBlank() ? MODE_TEXT : mode;
        return writeInternal(
                path,
                normalizeTextContent(content, prependNewline, appendNewline)
                        .getBytes(Charset.forName(resolveEncoding(encoding))),
                effectiveMode,
                append);
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(
            String path,
            byte[] content,
            String mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options) {
        byte[] raw;
        String effectiveMode = mode == null || mode.isBlank() ? MODE_TEXT : mode;
        if (MODE_BYTES.equals(effectiveMode)) {
            raw = content == null ? new byte[0] : content;
        } else {
            raw = normalizeTextContent(
                    new String(content == null ? new byte[0] : content, Charset.forName(resolveEncoding(encoding))),
                    prependNewline,
                    appendNewline).getBytes(Charset.forName(resolveEncoding(encoding)));
        }
        return writeInternal(path, raw, effectiveMode, append);
    }

    @Override
    public CompletableFuture<ListFilesResult> listFiles(
            String path,
            boolean recursive,
            Integer maxDepth,
            String sortBy,
            boolean sortDescending,
            List<String> fileTypes,
            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> rawItems = state.executeWithSandboxRetry(
                        sandboxId -> state.getClient().listFiles(
                                sandboxId,
                                path,
                                recursive,
                                maxDepth,
                                true,
                                false));
                List<FileSystemItem> items = rawItems.stream()
                        .map(JiuwenBoxProviderSupport::itemFromPayload)
                        .filter(item -> fileTypes == null || fileTypes.isEmpty() || fileTypes.contains(item.getType()))
                        .sorted(comparator(sortBy, sortDescending))
                        .toList();
                ListFilesResult result = new ListFilesResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(FileSystemData.builder()
                        .totalCount(items.size())
                        .listItems(items)
                        .rootPath(path)
                        .recursive(recursive)
                        .maxDepth(maxDepth)
                        .build());
                return result;
            } catch (Exception exception) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "list_files",
                        exception.getMessage(),
                        ListFilesResult.class);
            }
        });
    }

    @Override
    public CompletableFuture<ListDirsResult> listDirectories(
            String path,
            boolean recursive,
            Integer maxDepth,
            String sortBy,
            boolean sortDescending,
            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> rawItems = state.executeWithSandboxRetry(
                        sandboxId -> state.getClient().listFiles(
                                sandboxId,
                                path,
                                recursive,
                                maxDepth,
                                false,
                                true));
                List<FileSystemItem> items = rawItems.stream()
                        .map(JiuwenBoxProviderSupport::itemFromPayload)
                        .sorted(comparator(sortBy, sortDescending))
                        .toList();
                ListDirsResult result = new ListDirsResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(FileSystemData.builder()
                        .totalCount(items.size())
                        .listItems(items)
                        .rootPath(path)
                        .recursive(recursive)
                        .maxDepth(maxDepth)
                        .build());
                return result;
            } catch (Exception exception) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "list_directories",
                        exception.getMessage(),
                        ListDirsResult.class);
            }
        });
    }

    @Override
    public CompletableFuture<UploadFileResult> uploadFile(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!overwrite && state.executeWithSandboxRetry(
                        sandboxId -> state.getClient().pathExists(sandboxId, targetPath))) {
                    throw new IOException("File already exists: " + targetPath);
                }
                byte[] content = Files.readAllBytes(Path.of(localPath));
                state.executeWithSandboxRetry(sandboxId -> {
                    state.getClient().uploadBytes(sandboxId, targetPath, content);
                    return null;
                });
                UploadFileResult result = new UploadFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(UploadFileData.builder()
                        .localPath(localPath)
                        .targetPath(targetPath)
                        .size(content.length)
                        .build());
                return result;
            } catch (Exception exception) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "upload_file",
                        exception.getMessage(),
                        UploadFileResult.class);
            }
        });
    }

    @Override
    public Flow.Publisher<UploadFileStreamResult> uploadFileStream(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return AioProviderSupport.asyncPublisher(publisher -> {
            UploadFileResult result = uploadFile(
                    localPath,
                    targetPath,
                    overwrite,
                    createParentDirs,
                    preservePermissions,
                    chunkSize,
                    options).join();
            if (result.getCode() != StatusCode.SUCCESS.getCode()) {
                UploadFileStreamResult streamResult = new UploadFileStreamResult();
                streamResult.setCode(result.getCode());
                streamResult.setMessage(result.getMessage());
                publisher.submit(streamResult);
                return;
            }
            UploadFileData data = result.getData();
            UploadFileStreamResult streamResult = new UploadFileStreamResult();
            streamResult.setCode(StatusCode.SUCCESS.getCode());
            streamResult.setMessage(StatusCode.SUCCESS.getErrmsg());
            streamResult.setData(UploadFileChunkData.builder()
                    .localPath(localPath)
                    .targetPath(targetPath)
                    .chunkSize(data == null ? 0 : data.getSize())
                    .chunkIndex(0)
                    .isLastChunk(true)
                    .build());
            publisher.submit(streamResult);
        });
    }

    @Override
    public CompletableFuture<DownloadFileResult> downloadFile(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path target = Path.of(localPath);
                if (createParentDirs && target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                if (!overwrite && Files.exists(target)) {
                    throw new IOException("File already exists: " + localPath);
                }
                byte[] content = state.executeWithSandboxRetry(
                        sandboxId -> state.getClient().downloadBytes(sandboxId, sourcePath));
                Files.write(target, content);
                DownloadFileResult result = new DownloadFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(DownloadFileData.builder()
                        .sourcePath(sourcePath)
                        .localPath(localPath)
                        .size(content.length)
                        .build());
                return result;
            } catch (Exception exception) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "download_file",
                        exception.getMessage(),
                        DownloadFileResult.class);
            }
        });
    }

    @Override
    public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return AioProviderSupport.asyncPublisher(publisher -> {
            DownloadFileResult result = downloadFile(
                    sourcePath,
                    localPath,
                    overwrite,
                    createParentDirs,
                    preservePermissions,
                    chunkSize,
                    options).join();
            if (result.getCode() != StatusCode.SUCCESS.getCode()) {
                DownloadFileStreamResult streamResult = new DownloadFileStreamResult();
                streamResult.setCode(result.getCode());
                streamResult.setMessage(result.getMessage());
                publisher.submit(streamResult);
                return;
            }
            DownloadFileData data = result.getData();
            DownloadFileStreamResult streamResult = new DownloadFileStreamResult();
            streamResult.setCode(StatusCode.SUCCESS.getCode());
            streamResult.setMessage(StatusCode.SUCCESS.getErrmsg());
            streamResult.setData(DownloadFileChunkData.builder()
                    .sourcePath(sourcePath)
                    .localPath(localPath)
                    .chunkSize(data == null ? 0 : data.getSize())
                    .chunkIndex(0)
                    .isLastChunk(true)
                    .build());
            publisher.submit(streamResult);
        });
    }

    @Override
    public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern, List<String> excludePatterns) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<FileSystemItem> items = state.executeWithSandboxRetry(
                                sandboxId -> state.getClient().searchFiles(sandboxId, path, pattern, excludePatterns))
                        .stream()
                        .map(JiuwenBoxProviderSupport::itemFromPayload)
                        .sorted(comparator(SORT_BY_NAME, false))
                        .toList();
                SearchFilesResult result = new SearchFilesResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(SearchFilesData.builder()
                        .totalMatches(items.size())
                        .matchingFiles(items)
                        .searchPath(path)
                        .searchPattern(pattern)
                        .excludePatterns(excludePatterns)
                        .build());
                return result;
            } catch (Exception exception) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "search_files",
                        exception.getMessage(),
                        SearchFilesResult.class);
            }
        });
    }

    private CompletableFuture<WriteFileResult> writeInternal(
            String path,
            byte[] raw,
            String mode,
            boolean append) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                state.executeWithSandboxRetry(sandboxId -> {
                    if (append) {
                        state.getClient().appendBytes(sandboxId, path, raw);
                    } else {
                        state.getClient().uploadBytes(sandboxId, path, raw);
                    }
                    return null;
                });
                WriteFileResult result = new WriteFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(WriteFileData.builder()
                        .path(path)
                        .size(raw.length)
                        .mode(mode)
                        .build());
                return result;
            } catch (Exception exception) {
                return JiuwenBoxProviderSupport.buildFsErrorResult(
                        "write_file",
                        exception.getMessage(),
                        WriteFileResult.class);
            }
        });
    }

    private Comparator<FileSystemItem> comparator(String sortBy, boolean sortDescending) {
        return JiuwenBoxProviderSupportComparator.sort(sortBy, sortDescending);
    }

    private String resolveEncoding(String encoding) {
        return encoding == null || encoding.isBlank() ? DEFAULT_ENCODING : encoding;
    }

    private String normalizeTextContent(String content, boolean prependNewline, boolean appendNewline) {
        String value = content == null ? "" : content;
        if (prependNewline) {
            value = "\n" + value;
        }
        if (appendNewline) {
            value = value + "\n";
        }
        return value;
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lines.add(text.substring(start, index + 1));
                start = index + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    private boolean shouldEmitEmptyTextChunk(Integer head, Integer tail, BaseFsProtocal.LineRange lineRange) {
        if (head != null && head < 0) {
            return true;
        }
        if (tail != null && tail < 0) {
            return true;
        }
        return lineRange != null
                && (lineRange.startLine() <= 0 || lineRange.endLine() <= 0 || lineRange.startLine() > lineRange.endLine());
    }

    private static final class JiuwenBoxProviderSupportComparator {
        private static Comparator<FileSystemItem> sort(String sortBy, boolean sortDescending) {
            Comparator<FileSystemItem> comparator;
            if (SORT_BY_MODIFIED_TIME.equals(sortBy)) {
                comparator = Comparator.comparing(item -> item.getModifiedTime() == null ? "" : item.getModifiedTime());
            } else if (SORT_BY_SIZE.equals(sortBy)) {
                comparator = Comparator.comparingInt(FileSystemItem::getSize);
            } else {
                comparator = Comparator.comparing(item -> item.getName() == null ? "" : item.getName());
            }
            return sortDescending ? comparator.reversed() : comparator;
        }
    }
}
