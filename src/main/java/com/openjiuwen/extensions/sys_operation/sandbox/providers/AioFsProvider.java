/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
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
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.providers.BaseFsProvider;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Mirrors Python's {@code AIOFSProvider} in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/aio.py}.
 */
public class AioFsProvider extends BaseFsProvider {

    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("aio-fs-provider-io");

    private final AioProviderSupport.AioHttpClient client;
    private final int timeoutSeconds;

    public AioFsProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.timeoutSeconds = AioProviderSupport.resolveProviderTimeoutSeconds(config);
        this.client = new AioProviderSupport.AioHttpClient(endpoint, config, timeoutSeconds);
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
            AioProviderSupport.NormalizedReadParams params = AioProviderSupport.normalizeReadParams(head, tail, lineRange);
            Optional<String> validationError = AioProviderSupport.validateReadParams(
                    effectiveMode,
                    params.head(),
                    params.tail(),
                    params.lineRange());
            if (validationError.isPresent()) {
                return AioProviderSupport.buildFsErrorResult("read_file", validationError.get(), ReadFileResult.class);
            }
            try {
                if (params.head() != null || params.tail() != null || params.lineRange() != null) {
                    return readFileViaLocalSlice(path, effectiveMode, params.head(), params.tail(), params.lineRange());
                }
                if (MODE_BYTES.equals(effectiveMode)) {
                    return readFileBytes(path, chunkSize);
                }
                JsonNode response = AioProviderSupport.withRetry(timeoutSeconds, () -> client.postJson(
                        "v1/file/read",
                        Map.of("file", path)));
                ReadFileResult result = new ReadFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(ReadFileData.builder()
                        .path(path)
                        .content(response.path("content").asText(""))
                        .mode(effectiveMode)
                        .build());
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildFsErrorResult("read_file", exception.getMessage(), ReadFileResult.class);
            }
        }, IO_EXECUTOR);
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
            String effectiveMode = mode == null || mode.isBlank() ? MODE_TEXT : mode;
            AioProviderSupport.NormalizedReadParams params = AioProviderSupport.normalizeReadParams(head, tail, lineRange);
            Optional<String> validationError = AioProviderSupport.validateReadParams(
                    effectiveMode,
                    params.head(),
                    params.tail(),
                    params.lineRange());
            if (validationError.isPresent()) {
                publisher.submit(AioProviderSupport.buildFsErrorResult(
                        "read_file_stream",
                        validationError.get(),
                        ReadFileStreamResult.class));
                return;
            }

            ReadFileResult result = readFile(path, effectiveMode, params.head(), params.tail(), params.lineRange(),
                    encoding, chunkSize, options).join();
            if (result.getCode() != StatusCode.SUCCESS.getCode()) {
                ReadFileStreamResult streamResult = new ReadFileStreamResult();
                streamResult.setCode(result.getCode());
                streamResult.setMessage(result.getMessage());
                publisher.submit(streamResult);
                return;
            }

            Charset charset = Charset.forName(encoding == null || encoding.isBlank() ? DEFAULT_ENCODING : encoding);
            Object content = result.getData() == null ? null : result.getData().getContent();
            if (MODE_BYTES.equals(effectiveMode)) {
                byte[] bytes = content instanceof byte[] raw
                        ? raw
                        : String.valueOf(content == null ? "" : content).getBytes(charset);
                int effectiveChunkSize = chunkSize > 0 ? chunkSize : DEFAULT_READ_STREAM_CHUNK_SIZE;
                if (bytes.length == 0) {
                    return;
                }
                int chunkIndex = 0;
                for (int start = 0; start < bytes.length; start += effectiveChunkSize) {
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
                            .chunkIndex(chunkIndex)
                            .isLastChunk(end >= bytes.length)
                            .build());
                    publisher.submit(streamResult);
                    chunkIndex++;
                }
                return;
            }

            String text = content instanceof byte[] raw ? new String(raw, charset) : String.valueOf(content == null ? "" : content);
            List<String> lines = text.isEmpty() ? List.of() : text.lines().map(line -> line + "\n").toList();
            if (!text.endsWith("\n") && !lines.isEmpty()) {
                List<String> mutable = new ArrayList<>(lines);
                int lastIndex = mutable.size() - 1;
                mutable.set(lastIndex, mutable.get(lastIndex).replaceAll("\\n$", ""));
                lines = mutable;
            }

            boolean emitEmptyChunk = (params.head() != null && params.head() < 0)
                    || (params.tail() != null && params.tail() < 0)
                    || (params.lineRange() != null
                    && (params.lineRange().startLine() <= 0
                    || params.lineRange().endLine() <= 0
                    || params.lineRange().startLine() > params.lineRange().endLine()));
            if (lines.isEmpty()) {
                if (emitEmptyChunk) {
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
                }
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
        return writeText(path, content, mode, prependNewline, appendNewline, append);
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
        if (MODE_BYTES.equals(mode)) {
            return writeBytes(path, content == null ? new byte[0] : content, append);
        }
        Charset charset = Charset.forName(encoding == null || encoding.isBlank() ? DEFAULT_ENCODING : encoding);
        return writeText(path, new String(content == null ? new byte[0] : content, charset),
                mode, prependNewline, appendNewline, append);
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
                JsonNode response = AioProviderSupport.withRetry(timeoutSeconds, () -> client.postJson(
                        "v1/file/list",
                        listPayload(path, recursive, maxDepth, true)));
                List<FileSystemItem> items = new ArrayList<>();
                for (JsonNode node : response.path("files")) {
                    if (node.path("is_directory").asBoolean(false)) {
                        continue;
                    }
                    FileSystemItem item = FileSystemItem.builder()
                            .name(node.path("name").asText(""))
                            .path(node.path("path").asText(""))
                            .size(node.path("size").asInt(0))
                            .isDirectory(false)
                            .modifiedTime(node.path("modified_time").asText("0"))
                            .type(AioProviderSupport.extensionOf(node.path("name").asText("")))
                            .build();
                    if (fileTypes == null || fileTypes.isEmpty() || fileTypes.contains(item.getType())) {
                        items.add(item);
                    }
                }
                List<FileSystemItem> sorted = AioProviderSupport.sortFsItems(items, sortBy, sortDescending);
                ListFilesResult result = new ListFilesResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(FileSystemData.builder()
                        .totalCount(sorted.size())
                        .listItems(sorted)
                        .rootPath(path)
                        .recursive(recursive)
                        .maxDepth(maxDepth)
                        .build());
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildFsErrorResult("list_files", exception.getMessage(), ListFilesResult.class);
            }
        }, IO_EXECUTOR);
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
                JsonNode response = AioProviderSupport.withRetry(timeoutSeconds, () -> client.postJson(
                        "v1/file/list",
                        listPayload(path, recursive, maxDepth, false)));
                List<FileSystemItem> items = new ArrayList<>();
                for (JsonNode node : response.path("files")) {
                    if (!node.path("is_directory").asBoolean(false)) {
                        continue;
                    }
                    items.add(FileSystemItem.builder()
                            .name(node.path("name").asText(""))
                            .path(node.path("path").asText(""))
                            .size(0)
                            .isDirectory(true)
                            .modifiedTime(node.path("modified_time").asText("0"))
                            .build());
                }
                List<FileSystemItem> sorted = AioProviderSupport.sortFsItems(items, sortBy, sortDescending);
                ListDirsResult result = new ListDirsResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(FileSystemData.builder()
                        .totalCount(sorted.size())
                        .listItems(sorted)
                        .rootPath(path)
                        .recursive(recursive)
                        .maxDepth(maxDepth)
                        .build());
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildFsErrorResult("list_directories", exception.getMessage(), ListDirsResult.class);
            }
        }, IO_EXECUTOR);
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
                JsonNode response = AioProviderSupport.withRetry(timeoutSeconds, () -> client.uploadFile(targetPath, Path.of(localPath)));
                UploadFileResult result = new UploadFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(UploadFileData.builder()
                        .localPath(localPath)
                        .targetPath(targetPath)
                        .size(response.path("file_size").asInt((int) Files.size(Path.of(localPath))))
                        .build());
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildFsErrorResult("upload_file", exception.getMessage(), UploadFileResult.class);
            }
        }, IO_EXECUTOR);
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
            int effectiveChunkSize = chunkSize > 0 ? chunkSize : DEFAULT_UPLOAD_STREAM_CHUNK_SIZE;
            Path localFile = Path.of(localPath);
            long fileSize;
            try {
                fileSize = Files.size(localFile);
            } catch (IOException exception) {
                publisher.submit(AioProviderSupport.buildFsErrorResult(
                        "upload_file_stream",
                        exception.getMessage(),
                        UploadFileStreamResult.class));
                return;
            }

            try {
                byte[] content = Files.readAllBytes(localFile);
                int chunkIndex = 0;
                for (int start = 0; start < content.length; start += effectiveChunkSize) {
                    int end = Math.min(content.length, start + effectiveChunkSize);
                    byte[] piece = java.util.Arrays.copyOfRange(content, start, end);
                    boolean first = chunkIndex == 0;
                    writeBytesInternal(targetPath, piece, !first).join();
                    UploadFileStreamResult streamResult = new UploadFileStreamResult();
                    streamResult.setCode(StatusCode.SUCCESS.getCode());
                    streamResult.setMessage(StatusCode.SUCCESS.getErrmsg());
                    streamResult.setData(UploadFileChunkData.builder()
                            .localPath(localPath)
                            .targetPath(targetPath)
                            .chunkSize(piece.length)
                            .chunkIndex(chunkIndex)
                            .isLastChunk(end >= fileSize)
                            .build());
                    publisher.submit(streamResult);
                    chunkIndex++;
                }
            } catch (Exception exception) {
                publisher.submit(AioProviderSupport.buildFsErrorResult(
                        "upload_file_stream",
                        exception.getMessage(),
                        UploadFileStreamResult.class));
            }
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
                byte[] content = AioProviderSupport.withRetry(timeoutSeconds, () -> client.getBytes(
                        "v1/file/download",
                        Map.of("path", sourcePath)));
                Path target = Paths.get(localPath);
                if (createParentDirs && target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                if (!overwrite && Files.exists(target)) {
                    throw new IOException("File already exists: " + localPath);
                }
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
                return AioProviderSupport.buildFsErrorResult("download_file", exception.getMessage(), DownloadFileResult.class);
            }
        }, IO_EXECUTOR);
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
            int effectiveChunkSize = chunkSize > 0 ? chunkSize : DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE;
            try {
                byte[] content = AioProviderSupport.withRetry(timeoutSeconds, () -> client.getBytes(
                        "v1/file/download",
                        Map.of("path", sourcePath)));
                Path target = Paths.get(localPath);
                if (createParentDirs && target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                if (!overwrite && Files.exists(target)) {
                    throw new IOException("File already exists: " + localPath);
                }
                int chunkIndex = 0;
                try (java.io.OutputStream outputStream = Files.newOutputStream(target)) {
                    for (int start = 0; start < content.length; start += effectiveChunkSize) {
                        int end = Math.min(content.length, start + effectiveChunkSize);
                        byte[] piece = java.util.Arrays.copyOfRange(content, start, end);
                        outputStream.write(piece);
                        DownloadFileStreamResult streamResult = new DownloadFileStreamResult();
                        streamResult.setCode(StatusCode.SUCCESS.getCode());
                        streamResult.setMessage(StatusCode.SUCCESS.getErrmsg());
                        streamResult.setData(DownloadFileChunkData.builder()
                                .sourcePath(sourcePath)
                                .localPath(localPath)
                                .chunkSize(piece.length)
                                .chunkIndex(chunkIndex)
                                .isLastChunk(end >= content.length)
                                .build());
                        publisher.submit(streamResult);
                        chunkIndex++;
                    }
                }
            } catch (Exception exception) {
                publisher.submit(AioProviderSupport.buildFsErrorResult(
                        "download_file_stream",
                        exception.getMessage(),
                        DownloadFileStreamResult.class));
            }
        });
    }

    @Override
    public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern, List<String> excludePatterns) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<FileSystemItem> items = AioProviderSupport.withRetry(timeoutSeconds, () -> {
                    try {
                        JsonNode response = client.postJson(
                                "v1/file/glob",
                                new LinkedHashMap<>(Map.of(
                                        "path", path,
                                        "pattern", pattern,
                                        "files_only", Boolean.TRUE,
                                        "include_metadata", Boolean.TRUE)));
                        return parseGlobItems(response.path("files"));
                    } catch (AioProviderSupport.AioHttpException httpException) {
                        if (httpException.getStatusCode() != 404) {
                            throw httpException;
                        }
                        JsonNode response = client.postJson(
                                "v1/file/list",
                                Map.of("path", path, "recursive", Boolean.TRUE, "include_size", Boolean.TRUE));
                        return parseFallbackSearchItems(response.path("files"), pattern, excludePatterns);
                    }
                });
                List<FileSystemItem> sorted = AioProviderSupport.sortFsItems(items, SORT_BY_NAME, false);
                SearchFilesResult result = new SearchFilesResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(SearchFilesData.builder()
                        .totalMatches(sorted.size())
                        .matchingFiles(sorted)
                        .searchPath(path)
                        .searchPattern(pattern)
                        .excludePatterns(excludePatterns)
                        .build());
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildFsErrorResult("search_files", exception.getMessage(), SearchFilesResult.class);
            }
        }, IO_EXECUTOR);
    }

    private ReadFileResult readFileViaLocalSlice(
            String path,
            String mode,
            Integer head,
            Integer tail,
            LineRange lineRange) throws Exception {
        JsonNode response = AioProviderSupport.withRetry(timeoutSeconds, () -> client.postJson(
                "v1/file/read",
                Map.of("file", path)));
        String content = response.path("content").asText("");
        AioProviderSupport.SelectedText selectedText = AioProviderSupport.selectTextLines(content, head, tail, lineRange);
        ReadFileResult result = new ReadFileResult();
        result.setCode(StatusCode.SUCCESS.getCode());
        result.setMessage(StatusCode.SUCCESS.getErrmsg());
        result.setData(ReadFileData.builder()
                .path(path)
                .content(AioProviderSupport.joinLines(selectedText.lines()))
                .mode(mode)
                .build());
        return result;
    }

    private ReadFileResult readFileBytes(String path, int chunkSize) throws Exception {
        byte[] content = AioProviderSupport.withRetry(timeoutSeconds, () -> client.getBytes(
                "v1/file/download",
                Map.of("path", path)));
        if (chunkSize > 0 && content.length > chunkSize) {
            content = java.util.Arrays.copyOf(content, chunkSize);
        }
        ReadFileResult result = new ReadFileResult();
        result.setCode(StatusCode.SUCCESS.getCode());
        result.setMessage(StatusCode.SUCCESS.getErrmsg());
        result.setData(ReadFileData.builder()
                .path(path)
                .content(content)
                .mode(MODE_BYTES)
                .build());
        return result;
    }

    private CompletableFuture<WriteFileResult> writeText(
            String path,
            String content,
            String mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append) {
        return CompletableFuture.supplyAsync(() -> {
            String effectiveMode = mode == null || mode.isBlank() ? MODE_TEXT : mode;
            String text = content == null ? "" : content;
            if (MODE_TEXT.equals(effectiveMode)) {
                if (prependNewline) {
                    text = "\n" + text;
                }
                if (appendNewline) {
                    text = text + "\n";
                }
            }
            String finalText = text;
            try {
                JsonNode response = AioProviderSupport.withRetry(timeoutSeconds, () -> client.postJson(
                        "v1/file/write",
                        Map.of(
                                "file", path,
                                "content", finalText,
                                "encoding", DEFAULT_ENCODING,
                                "append", append)));
                int bytesWritten = response.path("bytes_written").asInt(finalText.getBytes(StandardCharsets.UTF_8).length);
                WriteFileResult result = new WriteFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(WriteFileData.builder()
                        .path(path)
                        .size(bytesWritten)
                        .mode(effectiveMode)
                        .build());
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildFsErrorResult("write_file", exception.getMessage(), WriteFileResult.class);
            }
        }, IO_EXECUTOR);
    }

    private CompletableFuture<WriteFileResult> writeBytes(String path, byte[] content, boolean append) {
        return CompletableFuture.supplyAsync(() -> writeBytesInternal(path, content, append).join(), IO_EXECUTOR);
    }

    private CompletableFuture<WriteFileResult> writeBytesInternal(String path, byte[] content, boolean append) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] raw = content == null ? new byte[0] : content;
            try {
                JsonNode response = AioProviderSupport.withRetry(timeoutSeconds, () -> client.postJson(
                        "v1/file/write",
                        Map.of(
                                "file", path,
                                "content", Base64.getEncoder().encodeToString(raw),
                                "encoding", "base64",
                                "append", append)));
                int bytesWritten = response.path("bytes_written").asInt(raw.length);
                WriteFileResult result = new WriteFileResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(WriteFileData.builder()
                        .path(path)
                        .size(bytesWritten)
                        .mode(MODE_BYTES)
                        .build());
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildFsErrorResult("write_file", exception.getMessage(), WriteFileResult.class);
            }
        }, IO_EXECUTOR);
    }

    private Map<String, Object> listPayload(String path, boolean recursive, Integer maxDepth, boolean includeSize) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", path);
        payload.put("recursive", recursive);
        if (maxDepth != null) {
            payload.put("max_depth", maxDepth);
        }
        if (includeSize) {
            payload.put("include_size", true);
        }
        return payload;
    }

    private List<FileSystemItem> parseGlobItems(JsonNode filesNode) {
        List<FileSystemItem> items = new ArrayList<>();
        for (JsonNode node : filesNode) {
            items.add(FileSystemItem.builder()
                    .name(node.path("name").asText(""))
                    .path(node.path("path").asText(""))
                    .size(node.path("size").asInt(0))
                    .isDirectory(node.path("is_directory").asBoolean(false))
                    .modifiedTime(node.path("modified_time").asText("0"))
                    .type(AioProviderSupport.extensionOf(node.path("name").asText("")))
                    .build());
        }
        return items;
    }

    private List<FileSystemItem> parseFallbackSearchItems(JsonNode filesNode, String pattern, List<String> excludePatterns) {
        List<FileSystemItem> items = new ArrayList<>();
        for (JsonNode node : filesNode) {
            if (node.path("is_directory").asBoolean(false)) {
                continue;
            }
            String name = node.path("name").asText("");
            if (!matchesGlob(name, pattern)) {
                continue;
            }
            if (excludePatterns != null && excludePatterns.stream().anyMatch(exclude -> matchesGlob(name, exclude))) {
                continue;
            }
            items.add(FileSystemItem.builder()
                    .name(name)
                    .path(node.path("path").asText(""))
                    .size(node.path("size").asInt(0))
                    .isDirectory(false)
                    .modifiedTime(node.path("modified_time").asText("0"))
                    .type(AioProviderSupport.extensionOf(name))
                    .build());
        }
        return items;
    }

    private boolean matchesGlob(String fileName, String pattern) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return fileName != null && fileName.matches(regex);
    }
}
