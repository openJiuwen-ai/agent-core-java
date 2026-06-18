/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseFsOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationDef;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.OperationRegistry;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sys_operation.result.BaseResult;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Local file-system operation.
 *
 * <p>Mirrors Python's {@code FsOperation} in
 * {@code openjiuwen/core/sys_operation/local/fs_operation.py}.</p>
 */
public class LocalFsOperation extends BaseFsOperation {

    public static final OperationDef OP_DEF = new OperationDef(
            LocalFsOperation.class,
            "local fs operation",
            "fs",
            OperationMode.LOCAL
    );

    private static final String EXECUTION_VALIDATE_READ_PARAMS = "validate_read_params";
    private static final String EXECUTION_READ_FILE = "read_file";
    private static final String EXECUTION_READ_FILE_STREAM = "read_file_stream";
    private static final String EXECUTION_WRITE_FILE = "write_file";
    private static final String EXECUTION_UPLOAD_FILE = "upload_file";
    private static final String EXECUTION_UPLOAD_FILE_STREAM = "upload_file_stream";
    private static final String EXECUTION_DOWNLOAD_FILE = "download_file";
    private static final String EXECUTION_DOWNLOAD_FILE_STREAM = "download_file_stream";
    private static final String EXECUTION_LIST_FILES = "list_files";
    private static final String EXECUTION_LIST_DIRECTORIES = "list_directories";
    private static final String EXECUTION_SEARCH_FILES = "search_files";
    private static final double DEFAULT_LOCK_TIMEOUT_SECONDS = 300.0d;
    private static final ConcurrentHashMap<String, ReentrantReadWriteLock> RW_LOCKS = new ConcurrentHashMap<>();

    static {
        OperationRegistry.register(LocalFsOperation.class);
    }

    public LocalFsOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ReadFileResult> readFile(String path, FileMode mode, Integer head, Integer tail,
                                                      BaseFsProtocal.LineRange lineRange, String encoding,
                                                      int chunkSize, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            FileMode effectiveMode = mode == null ? FileMode.TEXT : mode;
            Integer normalizedHead = normalizeZero(head);
            Integer normalizedTail = normalizeZero(tail);
            Optional<String> validation = validateReadParams(effectiveMode, normalizedHead, normalizedTail, lineRange);
            if (validation.isPresent()) {
                return errorResult(EXECUTION_VALIDATE_READ_PARAMS, validation.get(), ReadFileResult.class);
            }
            try {
                Path filePath = validateReadableFile(path, EXECUTION_READ_FILE);
                double timeout = getLockTimeout(options);
                return withLock(filePath, false, timeout, () -> {
                    if (effectiveMode == FileMode.BYTES) {
                        byte[] content = readBytes(filePath, chunkSize);
                        return successResult(ReadFileResult.class, ReadFileData.builder()
                                .path(filePath.toString())
                                .content(content)
                                .mode(effectiveMode.value())
                                .build());
                    }
                    Charset charset = charsetOrDefault(encoding);
                    String content = Files.readString(filePath, charset);
                    String selected = joinLines(selectTextLines(content, normalizedHead, normalizedTail, lineRange));
                    return successResult(ReadFileResult.class, ReadFileData.builder()
                            .path(filePath.toString())
                            .content(selected)
                            .mode(effectiveMode.value())
                            .build());
                });
            } catch (Exception exception) {
                return errorResult(EXECUTION_READ_FILE, exception, ReadFileResult.class);
            }
        });
    }

    @Override
    public Flow.Publisher<ReadFileStreamResult> readFileStream(String path, FileMode mode, Integer head,
                                                               Integer tail,
                                                               BaseFsProtocal.LineRange lineRange, String encoding,
                                                               int chunkSize, Map<String, Object> options) {
        return asyncPublisher(publisher -> {
            FileMode effectiveMode = mode == null ? FileMode.TEXT : mode;
            Integer normalizedHead = normalizeZero(head);
            Integer normalizedTail = normalizeZero(tail);
            Optional<String> validation = validateReadParams(effectiveMode, normalizedHead, normalizedTail, lineRange);
            if (validation.isPresent()) {
                publisher.submit(errorResult(
                        EXECUTION_VALIDATE_READ_PARAMS,
                        validation.get(),
                        ReadFileStreamResult.class));
                return;
            }
            try {
                Path filePath = validateReadableFile(path, EXECUTION_READ_FILE_STREAM);
                double timeout = getLockTimeout(options);
                withLock(filePath, false, timeout, () -> {
                    if (effectiveMode == FileMode.BYTES) {
                        publishReadByteChunks(publisher, filePath, chunkSize);
                    } else {
                        publishReadTextChunks(
                                publisher,
                                filePath,
                                normalizedHead,
                                normalizedTail,
                                lineRange,
                                charsetOrDefault(encoding));
                    }
                    return null;
                });
            } catch (Exception exception) {
                publisher.submit(errorResult(EXECUTION_READ_FILE_STREAM, exception, ReadFileStreamResult.class));
            }
        });
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(String path, String content, FileMode mode,
                                                        boolean prependNewline, boolean appendNewline,
                                                        boolean append, boolean createIfNotExist,
                                                        String permissions, String encoding,
                                                        Map<String, Object> options) {
        return writeBytesInternal(
                path,
                prepareTextBytes(content, mode, prependNewline, appendNewline, encoding),
                mode == null ? FileMode.TEXT : mode,
                append,
                createIfNotExist,
                permissions,
                options);
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(String path, byte[] content, FileMode mode,
                                                        boolean prependNewline, boolean appendNewline,
                                                        boolean append, boolean createIfNotExist,
                                                        String permissions, String encoding,
                                                        Map<String, Object> options) {
        FileMode effectiveMode = mode == null ? FileMode.BYTES : mode;
        byte[] raw = content == null ? new byte[0] : content;
        if (effectiveMode == FileMode.TEXT) {
            Charset charset = charsetOrDefault(encoding);
            return writeFile(
                    path,
                    new String(raw, charset),
                    effectiveMode,
                    prependNewline,
                    appendNewline,
                    append,
                    createIfNotExist,
                    permissions,
                    encoding,
                    options);
        }
        return writeBytesInternal(path, raw, effectiveMode, append, createIfNotExist, permissions, options);
    }

    @Override
    public CompletableFuture<UploadFileResult> uploadFile(String localPath, String targetPath, boolean overwrite,
                                                          boolean createParentDirs, boolean preservePermissions,
                                                          int chunkSize, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path source = resolveExternalPath(localPath);
                Path target = resolvePath(targetPath, createParentDirs);
                if (!Files.isRegularFile(source)) {
                    return errorResult(EXECUTION_UPLOAD_FILE, "Source not found: " + source, UploadFileResult.class);
                }
                if (Files.exists(target) && !overwrite) {
                    return errorResult(EXECUTION_UPLOAD_FILE, "Target exists: " + target, UploadFileResult.class);
                }
                double timeout = getLockTimeout(options);
                int size = withOrderedLocks(List.of(
                        new LockRequest(source, false),
                        new LockRequest(target, true)
                ), timeout, () -> {
                    int transferred = transferFile(source, target, chunkSize);
                    if (preservePermissions) {
                        copyPermissions(source, target);
                    }
                    return transferred;
                });
                return successResult(UploadFileResult.class, UploadFileData.builder()
                        .localPath(source.toString())
                        .targetPath(target.toString())
                        .size(size)
                        .build());
            } catch (Exception exception) {
                return errorResult(EXECUTION_UPLOAD_FILE, exception, UploadFileResult.class);
            }
        });
    }

    @Override
    public Flow.Publisher<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath,
                                                                   boolean overwrite, boolean createParentDirs,
                                                                   boolean preservePermissions, int chunkSize,
                                                                   Map<String, Object> options) {
        return asyncPublisher(publisher -> {
            try {
                Path source = resolveExternalPath(localPath);
                Path target = resolvePath(targetPath, createParentDirs);
                if (!Files.isRegularFile(source)) {
                    publisher.submit(errorResult(
                            EXECUTION_UPLOAD_FILE_STREAM,
                            "Source not found: " + source,
                            UploadFileStreamResult.class));
                    return;
                }
                if (Files.exists(target) && !overwrite) {
                    publisher.submit(errorResult(
                            EXECUTION_UPLOAD_FILE_STREAM,
                            "Target exists: " + target,
                            UploadFileStreamResult.class));
                    return;
                }
                double timeout = getLockTimeout(options);
                withOrderedLocks(List.of(
                        new LockRequest(source, false),
                        new LockRequest(target, true)
                ), timeout, () -> {
                    publishTransferChunks(source, target, effectiveTransferChunkSize(chunkSize, DEFAULT_UPLOAD_STREAM_CHUNK_SIZE),
                            (size, index, last) -> {
                                UploadFileStreamResult result = successResult(
                                        UploadFileStreamResult.class,
                                        UploadFileChunkData.builder()
                                                .localPath(source.toString())
                                                .targetPath(target.toString())
                                                .chunkSize(size)
                                                .chunkIndex(index)
                                                .isLastChunk(last)
                                                .build());
                                publisher.submit(result);
                            });
                    if (preservePermissions) {
                        copyPermissions(source, target);
                    }
                    return null;
                });
            } catch (Exception exception) {
                publisher.submit(errorResult(EXECUTION_UPLOAD_FILE_STREAM, exception, UploadFileStreamResult.class));
            }
        });
    }

    @Override
    public CompletableFuture<DownloadFileResult> downloadFile(String sourcePath, String localPath, boolean overwrite,
                                                              boolean createParentDirs, boolean preservePermissions,
                                                              int chunkSize, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path source = resolvePath(sourcePath, false);
                Path target = resolveExternalPath(localPath);
                if (!Files.isRegularFile(source)) {
                    return errorResult(EXECUTION_DOWNLOAD_FILE, "Source not found: " + source, DownloadFileResult.class);
                }
                if (Files.exists(target) && !overwrite) {
                    return errorResult(EXECUTION_DOWNLOAD_FILE, "Destination exists: " + target, DownloadFileResult.class);
                }
                if (createParentDirs && target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                double timeout = getLockTimeout(options);
                int size = withOrderedLocks(List.of(
                        new LockRequest(source, false),
                        new LockRequest(target, true)
                ), timeout, () -> {
                    int transferred = transferFile(source, target, chunkSize);
                    if (preservePermissions) {
                        copyPermissions(source, target);
                    }
                    return transferred;
                });
                return successResult(DownloadFileResult.class, DownloadFileData.builder()
                        .sourcePath(source.toString())
                        .localPath(target.toString())
                        .size(size)
                        .build());
            } catch (Exception exception) {
                return errorResult(EXECUTION_DOWNLOAD_FILE, exception, DownloadFileResult.class);
            }
        });
    }

    @Override
    public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
                                                                       boolean overwrite, boolean createParentDirs,
                                                                       boolean preservePermissions, int chunkSize,
                                                                       Map<String, Object> options) {
        return asyncPublisher(publisher -> {
            try {
                Path source = resolvePath(sourcePath, false);
                Path target = resolveExternalPath(localPath);
                if (!Files.isRegularFile(source)) {
                    publisher.submit(errorResult(
                            EXECUTION_DOWNLOAD_FILE_STREAM,
                            "Source not found: " + source,
                            DownloadFileStreamResult.class));
                    return;
                }
                if (Files.exists(target) && !overwrite) {
                    publisher.submit(errorResult(
                            EXECUTION_DOWNLOAD_FILE_STREAM,
                            "Destination exists: " + target,
                            DownloadFileStreamResult.class));
                    return;
                }
                if (createParentDirs && target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                double timeout = getLockTimeout(options);
                withOrderedLocks(List.of(
                        new LockRequest(source, false),
                        new LockRequest(target, true)
                ), timeout, () -> {
                    publishTransferChunks(source, target, effectiveTransferChunkSize(chunkSize, DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE),
                            (size, index, last) -> {
                                DownloadFileStreamResult result = successResult(
                                        DownloadFileStreamResult.class,
                                        DownloadFileChunkData.builder()
                                                .sourcePath(source.toString())
                                                .localPath(target.toString())
                                                .chunkSize(size)
                                                .chunkIndex(index)
                                                .isLastChunk(last)
                                                .build());
                                publisher.submit(result);
                            });
                    if (preservePermissions) {
                        copyPermissions(source, target);
                    }
                    return null;
                });
            } catch (Exception exception) {
                publisher.submit(errorResult(EXECUTION_DOWNLOAD_FILE_STREAM, exception, DownloadFileStreamResult.class));
            }
        });
    }

    @Override
    public CompletableFuture<ListFilesResult> listFiles(String path, boolean recursive, Integer maxDepth,
                                                        SortBy sortBy, boolean sortDescending,
                                                        List<String> fileTypes, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path root = resolvePath(path, false);
                List<FileSystemItem> items = listItems(root, true, false, recursive, maxDepth, sortBy,
                        sortDescending, fileTypes);
                return successResult(ListFilesResult.class, FileSystemData.builder()
                        .totalCount(items.size())
                        .listItems(items)
                        .rootPath(root.toString())
                        .recursive(recursive)
                        .maxDepth(maxDepth)
                        .build());
            } catch (Exception exception) {
                return errorResult(EXECUTION_LIST_FILES, exception, ListFilesResult.class);
            }
        });
    }

    @Override
    public CompletableFuture<ListDirsResult> listDirectories(String path, boolean recursive, Integer maxDepth,
                                                             SortBy sortBy, boolean sortDescending,
                                                             Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path root = resolvePath(path, false);
                List<FileSystemItem> items = listItems(root, false, true, recursive, maxDepth, sortBy,
                        sortDescending, null);
                return successResult(ListDirsResult.class, FileSystemData.builder()
                        .totalCount(items.size())
                        .listItems(items)
                        .rootPath(root.toString())
                        .recursive(recursive)
                        .maxDepth(maxDepth)
                        .build());
            } catch (Exception exception) {
                return errorResult(EXECUTION_LIST_DIRECTORIES, exception, ListDirsResult.class);
            }
        });
    }

    @Override
    public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern,
                                                            List<String> excludePatterns) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path root = resolvePath(path, false);
                if (!Files.isDirectory(root)) {
                    throw new IOException("Path is not a directory: " + root);
                }
                List<FileSystemItem> items = searchFileItems(root, pattern, excludePatterns);
                return successResult(SearchFilesResult.class, SearchFilesData.builder()
                        .totalMatches(items.size())
                        .matchingFiles(items)
                        .searchPath(root.toString())
                        .searchPattern(pattern)
                        .excludePatterns(excludePatterns)
                        .build());
            } catch (Exception exception) {
                return errorResult(EXECUTION_SEARCH_FILES, exception, SearchFilesResult.class);
            }
        });
    }

    private CompletableFuture<WriteFileResult> writeBytesInternal(String path, byte[] bytes, FileMode mode,
                                                                  boolean append, boolean createIfNotExist,
                                                                  String permissions,
                                                                  Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = resolvePath(path, true);
                if (Files.isDirectory(filePath)) {
                    return errorResult(EXECUTION_WRITE_FILE, "Target path is a directory: " + filePath,
                            WriteFileResult.class);
                }
                if (!createIfNotExist && Files.notExists(filePath)) {
                    return errorResult(EXECUTION_WRITE_FILE, "File does not exist: " + filePath,
                            WriteFileResult.class);
                }
                double timeout = getLockTimeout(options);
                return withLock(filePath, true, timeout, () -> {
                    StandardOpenOption[] openOptions = append
                            ? new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                            StandardOpenOption.APPEND}
                            : new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING};
                    Files.write(filePath, bytes, openOptions);
                    applyPermissions(filePath, permissions);
                    return successResult(WriteFileResult.class, WriteFileData.builder()
                            .path(filePath.toString())
                            .size(bytes.length)
                            .mode((mode == null ? FileMode.TEXT : mode).value())
                            .build());
                });
            } catch (Exception exception) {
                return errorResult(EXECUTION_WRITE_FILE, exception, WriteFileResult.class);
            }
        });
    }

    private byte[] prepareTextBytes(String content, FileMode mode, boolean prependNewline,
                                    boolean appendNewline, String encoding) {
        FileMode effectiveMode = mode == null ? FileMode.TEXT : mode;
        if (effectiveMode == FileMode.BYTES) {
            return content == null ? new byte[0] : content.getBytes(charsetOrDefault(encoding));
        }
        String text = String.valueOf(content);
        if (prependNewline) {
            text = "\n" + text;
        }
        if (appendNewline) {
            text = text + "\n";
        }
        return text.getBytes(charsetOrDefault(encoding));
    }

    private Path validateReadableFile(String path, String execution) throws IOException {
        Path filePath = resolvePath(path, false);
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("File not found: " + filePath + " while executing " + execution);
        }
        return filePath;
    }

    private Path resolvePath(String path, boolean createParent) throws IOException {
        Path base = Path.of(Cwd.getCwd());
        Path normalized = base.resolve(path).toAbsolutePath().normalize();
        enforceSandbox(normalized, path);
        if (createParent && normalized.getParent() != null) {
            Files.createDirectories(normalized.getParent());
        }
        return normalized;
    }

    private Path resolveExternalPath(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    private void enforceSandbox(Path normalized, String originalPath) throws IOException {
        Object config = getRunConfig();
        if (!(config instanceof LocalWorkConfig localWorkConfig) || !localWorkConfig.isRestrictToSandbox()) {
            return;
        }
        List<String> configuredRoots = localWorkConfig.getSandboxRoot();
        List<String> roots = configuredRoots == null || configuredRoots.isEmpty()
                ? Stream.of(Cwd.getWorkspace(), Cwd.getProjectRoot())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .toList()
                : configuredRoots;
        List<Path> resolvedRoots = roots.stream()
                .map(root -> Path.of(root).toAbsolutePath().normalize())
                .toList();
        boolean allowed = resolvedRoots.stream().anyMatch(root -> normalized.startsWith(root));
        if (!allowed) {
            throw new IOException("Access denied: Path " + originalPath + " outside sandbox " + resolvedRoots);
        }
    }

    private double getLockTimeout(Map<String, Object> options) {
        if (options != null && options.containsKey("lock_timeout")) {
            return Double.parseDouble(String.valueOf(options.get("lock_timeout")));
        }
        return DEFAULT_LOCK_TIMEOUT_SECONDS;
    }

    private <T> T withLock(Path path, boolean write, double timeoutSeconds, Callable<T> callable) throws Exception {
        return withOrderedLocks(List.of(new LockRequest(path, write)), timeoutSeconds, callable);
    }

    private <T> T withOrderedLocks(List<LockRequest> requests, double timeoutSeconds, Callable<T> callable)
            throws Exception {
        List<LockRequest> merged = mergeLockRequests(requests);
        List<Lock> acquired = new ArrayList<>();
        try {
            long timeoutMillis = Math.max(1L, Math.round(timeoutSeconds * 1000.0d));
            for (LockRequest request : merged) {
                ReentrantReadWriteLock rwLock = RW_LOCKS.computeIfAbsent(lockKey(request.path()),
                        ignored -> new ReentrantReadWriteLock());
                Lock lock = request.write() ? rwLock.writeLock() : rwLock.readLock();
                if (!lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    throw new IOException("Timed out acquiring file lock: " + request.path());
                }
                acquired.add(lock);
            }
            return callable.call();
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).unlock();
            }
        }
    }

    private List<LockRequest> mergeLockRequests(List<LockRequest> requests) {
        Map<String, LockRequest> merged = new LinkedHashMap<>();
        for (LockRequest request : requests) {
            String key = lockKey(request.path());
            LockRequest existing = merged.get(key);
            if (existing == null || (!existing.write() && request.write())) {
                merged.put(key, request);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(request -> lockKey(request.path())))
                .toList();
    }

    private String lockKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase();
    }

    private Optional<String> validateReadParams(FileMode mode, Integer head, Integer tail,
                                                BaseFsProtocal.LineRange lineRange) {
        if (tail != null) {
            if (head != null) {
                return Optional.of("tail and head cannot be specified simultaneously");
            }
            if (lineRange != null) {
                return Optional.of("tail and line_range cannot be specified simultaneously");
            }
        } else if (head != null && lineRange != null) {
            return Optional.of("head and line_range cannot be specified simultaneously");
        }
        if (mode == FileMode.BYTES && (head != null || tail != null || lineRange != null)) {
            return Optional.of("Parameters 'head', 'tail', and 'line_range' are only supported in text mode");
        }
        return Optional.empty();
    }

    private Integer normalizeZero(Integer value) {
        return value != null && value == 0 ? null : value;
    }

    private byte[] readBytes(Path path, int chunkSize) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            if (chunkSize <= 0) {
                return inputStream.readAllBytes();
            }
            return inputStream.readNBytes(chunkSize);
        }
    }

    private void publishReadByteChunks(SubmissionPublisher<ReadFileStreamResult> publisher, Path filePath,
                                       int chunkSize) throws IOException {
        int effectiveChunkSize = effectiveTransferChunkSize(chunkSize, DEFAULT_READ_STREAM_CHUNK_SIZE);
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] current = inputStream.readNBytes(effectiveChunkSize);
            int index = 0;
            while (current.length > 0) {
                byte[] next = inputStream.readNBytes(effectiveChunkSize);
                publisher.submit(successResult(ReadFileStreamResult.class, ReadFileChunkData.builder()
                        .path(filePath.toString())
                        .chunkContent(current)
                        .mode(FileMode.BYTES.value())
                        .chunkSize(current.length)
                        .chunkIndex(index)
                        .isLastChunk(next.length == 0)
                        .build()));
                current = next;
                index += 1;
            }
        }
    }

    private void publishReadTextChunks(SubmissionPublisher<ReadFileStreamResult> publisher, Path filePath,
                                       Integer head, Integer tail, BaseFsProtocal.LineRange lineRange,
                                       Charset charset) throws IOException {
        String content = Files.readString(filePath, charset);
        List<String> lines = selectTextLines(content, head, tail, lineRange);
        if (lines.isEmpty()) {
            if (shouldEmitEmptyTextChunk(head, tail, lineRange)) {
                publisher.submit(successResult(ReadFileStreamResult.class, ReadFileChunkData.builder()
                        .path(filePath.toString())
                        .chunkContent("")
                        .mode(FileMode.TEXT.value())
                        .chunkSize(0)
                        .chunkIndex(0)
                        .isLastChunk(true)
                        .build()));
            }
            return;
        }
        for (int index = 0; index < lines.size(); index += 1) {
            String line = lines.get(index);
            publisher.submit(successResult(ReadFileStreamResult.class, ReadFileChunkData.builder()
                    .path(filePath.toString())
                    .chunkContent(line)
                    .mode(FileMode.TEXT.value())
                    .chunkSize(line.getBytes(charset).length)
                    .chunkIndex(index)
                    .isLastChunk(index == lines.size() - 1)
                    .build()));
        }
    }

    private boolean shouldEmitEmptyTextChunk(Integer head, Integer tail, BaseFsProtocal.LineRange lineRange) {
        return head != null && head < 0
                || tail != null && tail < 0
                || lineRange != null
                && (lineRange.startLine() <= 0
                || lineRange.endLine() <= 0
                || lineRange.startLine() > lineRange.endLine());
    }

    private List<String> selectTextLines(String content, Integer head, Integer tail,
                                         BaseFsProtocal.LineRange lineRange) {
        if (tail != null) {
            if (tail <= 0) {
                return List.of();
            }
            List<String> lines = splitLinesKeepEndings(content);
            return lines.subList(Math.max(0, lines.size() - tail), lines.size());
        }
        if (head != null) {
            if (head <= 0) {
                return List.of();
            }
            List<String> lines = splitLinesKeepEndings(content);
            return lines.subList(0, Math.min(head, lines.size()));
        }
        if (lineRange != null) {
            int start = lineRange.startLine();
            int end = lineRange.endLine();
            if (start <= 0 || end <= 0 || start > end) {
                return List.of();
            }
            List<String> lines = splitLinesKeepEndings(content);
            int from = Math.min(start - 1, lines.size());
            int to = Math.min(end, lines.size());
            return from >= to ? List.of() : lines.subList(from, to);
        }
        return content.isEmpty() ? List.of() : splitLinesKeepEndings(content);
    }

    private List<String> splitLinesKeepEndings(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < content.length(); index += 1) {
            if (content.charAt(index) == '\n') {
                lines.add(content.substring(start, index + 1));
                start = index + 1;
            }
        }
        if (start < content.length()) {
            lines.add(content.substring(start));
        }
        return lines;
    }

    private String joinLines(List<String> lines) {
        return String.join("", lines);
    }

    private int transferFile(Path source, Path target, int chunkSize) throws IOException {
        int effectiveChunkSize = chunkSize > 0 ? chunkSize : 8192;
        int total = 0;
        try (InputStream inputStream = Files.newInputStream(source);
             OutputStream outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[effectiveChunkSize];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, count);
                total += count;
            }
        }
        return total;
    }

    private void publishTransferChunks(Path source, Path target, int chunkSize, TransferChunkConsumer consumer)
            throws IOException {
        try (InputStream inputStream = Files.newInputStream(source);
             OutputStream outputStream = Files.newOutputStream(target, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            byte[] current = inputStream.readNBytes(chunkSize);
            int index = 0;
            while (current.length > 0) {
                byte[] next = inputStream.readNBytes(chunkSize);
                outputStream.write(current);
                consumer.accept(current.length, index, next.length == 0);
                current = next;
                index += 1;
            }
        }
    }

    private int effectiveTransferChunkSize(int chunkSize, int defaultSize) {
        return chunkSize > 0 ? chunkSize : defaultSize;
    }

    private List<FileSystemItem> listItems(Path root, boolean includeFiles, boolean includeDirs, boolean recursive,
                                           Integer maxDepth, SortBy sortBy, boolean sortDescending,
                                           List<String> fileTypes) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Path is not a directory: " + root);
        }
        int depthLimit = recursive ? (maxDepth == null ? Integer.MAX_VALUE : Math.max(0, maxDepth)) : 1;
        List<FileSystemItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, depthLimit)) {
            stream.skip(1).forEach(path -> {
                try {
                    boolean directory = Files.isDirectory(path);
                    if (!includeFiles && !directory || !includeDirs && directory) {
                        return;
                    }
                    if (fileTypes != null && !fileTypes.isEmpty() && !directory
                            && !fileTypes.contains(extensionOf(path))) {
                        return;
                    }
                    createFileSystemItem(path).ifPresent(items::add);
                } catch (IOException ignored) {
                    // Python logs and skips stat failures while listing.
                }
            });
        }
        items.sort(comparator(sortBy, sortDescending));
        return items;
    }

    private List<FileSystemItem> searchFileItems(Path root, String pattern, List<String> excludePatterns)
            throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<PathMatcher> excludes = excludePatterns == null ? List.of() : excludePatterns.stream()
                .map(exclude -> FileSystems.getDefault().getPathMatcher("glob:" + exclude))
                .toList();
        List<FileSystemItem> items = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(root.relativize(path)) || matcher.matches(path.getFileName()))
                    .filter(path -> excludes.stream().noneMatch(exclude ->
                            exclude.matches(root.relativize(path)) || exclude.matches(path.getFileName())))
                    .forEach(path -> {
                        try {
                            createFileSystemItem(path).ifPresent(items::add);
                        } catch (IOException ignored) {
                            // Python logs and skips stat failures while searching.
                        }
                    });
        }
        items.sort(comparator(SortBy.NAME, false));
        return items;
    }

    private Optional<FileSystemItem> createFileSystemItem(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        boolean directory = attributes.isDirectory();
        return Optional.of(FileSystemItem.builder()
                .name(path.getFileName() == null ? "" : path.getFileName().toString())
                .path(path.toString())
                .size(Math.toIntExact(attributes.size()))
                .modifiedTime(String.valueOf(FileTime.from(attributes.lastModifiedTime().toInstant())))
                .isDirectory(directory)
                .type(directory ? null : extensionOf(path))
                .build());
    }

    private Comparator<FileSystemItem> comparator(SortBy sortBy, boolean descending) {
        Comparator<FileSystemItem> comparator;
        SortBy effectiveSort = sortBy == null ? SortBy.NAME : sortBy;
        if (effectiveSort == SortBy.MODIFIED_TIME) {
            comparator = Comparator.comparing(FileSystemItem::getModifiedTime, Comparator.nullsFirst(String::compareTo));
        } else if (effectiveSort == SortBy.SIZE) {
            comparator = Comparator.comparingInt(FileSystemItem::getSize);
        } else {
            comparator = Comparator.comparing(FileSystemItem::getName, Comparator.nullsFirst(String::compareTo));
        }
        return descending ? comparator.reversed() : comparator;
    }

    private String extensionOf(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    private Charset charsetOrDefault(String encoding) {
        return Charset.forName(encoding == null || encoding.isBlank() ? BaseFsProtocal.DEFAULT_ENCODING : encoding);
    }

    private void applyPermissions(Path path, String permissions) {
        try {
            Set<PosixFilePermission> parsed = parsePosixPermissions(permissions);
            Files.setPosixFilePermissions(path, parsed);
        } catch (UnsupportedOperationException | IOException | IllegalArgumentException ignored) {
            // Python treats permission application as best-effort on non-POSIX systems.
        }
    }

    private void copyPermissions(Path source, Path target) {
        try {
            Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Python treats permission copying as best-effort on non-POSIX systems.
        }
    }

    private Set<PosixFilePermission> parsePosixPermissions(String permissions) {
        String normalized = permissions == null || permissions.isBlank()
                ? BaseFsProtocal.DEFAULT_PERMISSIONS
                : permissions;
        int mode = Integer.parseInt(normalized, 8);
        Set<PosixFilePermission> parsed = new LinkedHashSet<>();
        addPermission(parsed, mode, 0400, PosixFilePermission.OWNER_READ);
        addPermission(parsed, mode, 0200, PosixFilePermission.OWNER_WRITE);
        addPermission(parsed, mode, 0100, PosixFilePermission.OWNER_EXECUTE);
        addPermission(parsed, mode, 0040, PosixFilePermission.GROUP_READ);
        addPermission(parsed, mode, 0020, PosixFilePermission.GROUP_WRITE);
        addPermission(parsed, mode, 0010, PosixFilePermission.GROUP_EXECUTE);
        addPermission(parsed, mode, 0004, PosixFilePermission.OTHERS_READ);
        addPermission(parsed, mode, 0002, PosixFilePermission.OTHERS_WRITE);
        addPermission(parsed, mode, 0001, PosixFilePermission.OTHERS_EXECUTE);
        return parsed;
    }

    private void addPermission(Set<PosixFilePermission> permissions, int mode, int mask,
                               PosixFilePermission permission) {
        if ((mode & mask) != 0) {
            permissions.add(permission);
        }
    }

    private static <T, R extends BaseResult<T>> R successResult(Class<R> resultClass, T data) {
        try {
            R result = resultClass.getDeclaredConstructor().newInstance();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(data);
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create result " + resultClass.getName(), exception);
        }
    }

    private static <R> R errorResult(String execution, Throwable exception, Class<R> resultClass) {
        return errorResult(execution, exception == null ? null : exception.getMessage(), resultClass);
    }

    private static <R> R errorResult(String execution, String message, Class<R> resultClass) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", message == null ? "" : message),
                resultClass);
    }

    private static <T> Flow.Publisher<T> asyncPublisher(Consumer<SubmissionPublisher<T>> emitter) {
        return subscriber -> {
            SubmissionPublisher<T> publisher = new SubmissionPublisher<>();
            publisher.subscribe(subscriber);
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.accept(publisher);
                    publisher.close();
                } catch (RuntimeException exception) {
                    publisher.closeExceptionally(exception);
                }
            });
        };
    }

    private record LockRequest(Path path, boolean write) {
    }

    private interface TransferChunkConsumer {
        void accept(int size, int index, boolean last);
    }
}
