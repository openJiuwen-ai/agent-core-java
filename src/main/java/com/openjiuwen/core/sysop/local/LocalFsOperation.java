/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.FsConstants;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local file system operation using Java NIO.
 * <p>
 * Mirrors Python's {@code FsOperation} in {@code local/fs_operation.py}.
 */
@Operation(name = "fs", mode = OperationMode.LOCAL, description = "local fs operation")
public class LocalFsOperation extends BaseFsOperation {

    public LocalFsOperation(Object runConfig) {
        super("fs", OperationMode.LOCAL, "local fs operation", runConfig);
    }

    // ==================== Read File ====================

    @Override
    public ReadFileResult readFile(String path, String mode, Integer head, Integer tail,
                                   int[] lineRange, String encoding, int chunkSize,
                                   Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to read file");

        try {
            // Normalize head/tail: 0 鈫?null
            Integer effectiveHead = (head != null && head == 0) ? null : head;
            Integer effectiveTail = (tail != null && tail == 0) ? null : tail;

            // Validate mutually exclusive params
            validateReadParams(effectiveHead, effectiveTail, lineRange, mode);

            // Resolve path
            Path filePath = resolvePath(path, false);
            if (!Files.isRegularFile(filePath)) {
                return buildFsErrorResult("File not found: " + filePath, ReadFileResult::new, null);
            }

            Charset charset = resolveCharset(encoding);
            Object content;
            if ("bytes".equals(mode)) {
                content = readBytesContent(filePath, chunkSize);
            } else {
                content = readTextContent(filePath, charset, effectiveHead, effectiveTail, lineRange);
            }

            ReadFileResult result = ReadFileResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(ReadFileData.builder()
                            .path(filePath.toString())
                            .content(content)
                            .mode(mode)
                            .build())
                    .build();

            Loggers.SYS_OPERATION.info("End to read file, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to read file", e);
            return buildFsErrorResult("readFile: " + e.getMessage(), ReadFileResult::new, null);
        }
    }

    @Override
    public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail,
                                                          int[] lineRange, String encoding, int chunkSize,
                                                          Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to read file streaming");
        List<ReadFileStreamResult> results = new ArrayList<>();

        try {
            Integer effectiveHead = (head != null && head == 0) ? null : head;
            Integer effectiveTail = (tail != null && tail == 0) ? null : tail;

            validateReadParams(effectiveHead, effectiveTail, lineRange, mode);

            Path filePath = resolvePath(path, false);
            if (!Files.isRegularFile(filePath)) {
                results.add(buildFsErrorResult("File not found: " + filePath,
                        ReadFileStreamResult::new, null));
                return results.iterator();
            }

            Charset charset = resolveCharset(encoding);
            int effectiveChunkSize = chunkSize <= 0
                    ? FsConstants.DEFAULT_READ_STREAM_CHUNK_SIZE : chunkSize;

            if (!"text".equals(mode)) {
                // Binary stream
                readBytesStream(filePath, effectiveChunkSize, results);
            } else {
                // Text stream
                streamTextFile(filePath, charset, effectiveHead, effectiveTail, lineRange, mode, results);
            }

            return results.iterator();

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to read file streaming", e);
            results.add(buildFsErrorResult("readFileStream: " + e.getMessage(),
                    ReadFileStreamResult::new, null));
            return results.iterator();
        }
    }

    // ==================== Write File ====================

    @Override
    public WriteFileResult writeFile(String path, Object content, String mode,
                                     boolean prependNewline, boolean appendNewline,
                                     boolean createIfNotExist, String permissions,
                                     String encoding, Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to write file");

        try {
            Path filePath = resolvePath(path, true);
            if (Files.isDirectory(filePath)) {
                return buildFsErrorResult("Target path is a directory: " + filePath,
                        WriteFileResult::new, null);
            }
            if (!createIfNotExist && !Files.exists(filePath)) {
                return buildFsErrorResult("File does not exist: " + filePath,
                        WriteFileResult::new, null);
            }

            byte[] dataBytes;
            if ("bytes".equals(mode)) {
                // Binary mode: accept byte[] directly, or convert String to bytes
                if (content instanceof byte[] rawBytes) {
                    dataBytes = rawBytes;
                } else if (content instanceof String strContent) {
                    dataBytes = strContent.getBytes(StandardCharsets.UTF_8);
                } else {
                    dataBytes = content != null ? content.toString().getBytes(StandardCharsets.UTF_8) : new byte[0];
                }
            } else {
                // Text mode
                String txt = extractTextContent(content);
                if (prependNewline) {
                    txt = "\n" + txt;
                }
                if (appendNewline) {
                    txt = txt + "\n";
                }
                dataBytes = txt.getBytes(resolveCharset(encoding));
            }

            Files.write(filePath, dataBytes);
            applyPermissions(filePath, permissions);

            WriteFileResult result = WriteFileResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(WriteFileData.builder()
                            .path(filePath.toString())
                            .size(dataBytes.length)
                            .mode(mode)
                            .build())
                    .build();

            Loggers.SYS_OPERATION.info("End to write file, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to write file", e);
            return buildFsErrorResult("write_file: " + e.getMessage(), WriteFileResult::new, null);
        }
    }

    // ==================== Upload File ====================

    @Override
    public UploadFileResult uploadFile(String localPath, String targetPath,
                                       boolean overwrite, boolean createParentDirs,
                                       boolean preservePermissions, int chunkSize,
                                       Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to upload file");

        try {
            Path src = Path.of(localPath).toAbsolutePath().normalize();
            Path dst = resolvePath(targetPath, createParentDirs);

            if (!Files.isRegularFile(src)) {
                return buildFsErrorResult("Source not found: " + src, UploadFileResult::new, null);
            }
            if (Files.exists(dst) && !overwrite) {
                return buildFsErrorResult("Target exists: " + dst, UploadFileResult::new, null);
            }

            long size = transferFile(src, dst, chunkSize);
            if (preservePermissions) {
                copyPermissions(src, dst);
            }

            UploadFileResult result = UploadFileResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(UploadFileData.builder()
                            .localPath(src.toString())
                            .targetPath(dst.toString())
                            .size(size)
                            .build())
                    .build();

            Loggers.SYS_OPERATION.info("End to upload file, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to upload file", e);
            return buildFsErrorResult("upload_file: " + e.getMessage(), UploadFileResult::new, null);
        }
    }

    @Override
    public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath,
                                                              boolean overwrite, boolean createParentDirs,
                                                              boolean preservePermissions, int chunkSize,
                                                              Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to upload file streaming");
        List<UploadFileStreamResult> results = new ArrayList<>();

        try {
            Path src = Path.of(localPath).toAbsolutePath().normalize();
            Path dst = resolvePath(targetPath, createParentDirs);

            if (!Files.isRegularFile(src)) {
                results.add(buildFsErrorResult("Source not found: " + src,
                        UploadFileStreamResult::new, null));
                return results.iterator();
            }
            if (Files.exists(dst) && !overwrite) {
                results.add(buildFsErrorResult("Target exists: " + dst,
                        UploadFileStreamResult::new, null));
                return results.iterator();
            }

            int effectiveChunkSize = chunkSize > 0 ? chunkSize : FsConstants.DEFAULT_UPLOAD_STREAM_CHUNK_SIZE;
            try (InputStream in = Files.newInputStream(src);
                 OutputStream out = Files.newOutputStream(dst)) {
                int index = 0;
                byte[] currentChunk = new byte[effectiveChunkSize];
                int currentRead = in.read(currentChunk);

                while (currentRead != -1) {
                    byte[] nextChunk = new byte[effectiveChunkSize];
                    int nextRead = in.read(nextChunk);
                    boolean isLast = (nextRead == -1);

                    out.write(currentChunk, 0, currentRead);
                    results.add(UploadFileStreamResult.builder()
                            .code(StatusCode.SUCCESS.getCode())
                            .message("Success")
                            .data(UploadFileChunkData.builder()
                                    .localPath(src.toString())
                                    .targetPath(dst.toString())
                                    .chunkSize(currentRead)
                                    .chunkIndex(index)
                                    .lastChunk(isLast)
                                    .build())
                            .build());
                    index++;

                    currentChunk = nextChunk;
                    currentRead = nextRead;
                }
            }

            if (preservePermissions) {
                copyPermissions(src, dst);
            }
            return results.iterator();

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to upload file streaming", e);
            results.add(buildFsErrorResult("upload_file_stream: " + e.getMessage(),
                    UploadFileStreamResult::new, null));
            return results.iterator();
        }
    }

    // ==================== Download File ====================

    @Override
    public DownloadFileResult downloadFile(String sourcePath, String localPath,
                                           boolean overwrite, boolean createParentDirs,
                                           boolean preservePermissions, int chunkSize,
                                           Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to download file");

        try {
            Path src = resolvePath(sourcePath, false);
            Path dst = Path.of(localPath).toAbsolutePath().normalize();

            if (!Files.isRegularFile(src)) {
                return buildFsErrorResult("Source not found: " + src, DownloadFileResult::new, null);
            }
            if (Files.exists(dst) && !overwrite) {
                return buildFsErrorResult("Destination exists: " + dst, DownloadFileResult::new, null);
            }
            if (createParentDirs && dst.getParent() != null) {
                Files.createDirectories(dst.getParent());
            }

            long size = transferFile(src, dst, chunkSize);
            if (preservePermissions) {
                copyPermissions(src, dst);
            }

            DownloadFileResult result = DownloadFileResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(DownloadFileData.builder()
                            .sourcePath(src.toString())
                            .localPath(dst.toString())
                            .size(size)
                            .build())
                    .build();

            Loggers.SYS_OPERATION.info("End to download file, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to download file", e);
            return buildFsErrorResult("download_file: " + e.getMessage(), DownloadFileResult::new, null);
        }
    }

    @Override
    public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
                                                                  boolean overwrite, boolean createParentDirs,
                                                                  boolean preservePermissions, int chunkSize,
                                                                  Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to download file streaming");
        List<DownloadFileStreamResult> results = new ArrayList<>();

        try {
            Path src = resolvePath(sourcePath, false);
            Path dst = Path.of(localPath).toAbsolutePath().normalize();

            if (!Files.isRegularFile(src)) {
                results.add(buildFsErrorResult("Source not found: " + src,
                        DownloadFileStreamResult::new, null));
                return results.iterator();
            }
            if (Files.exists(dst) && !overwrite) {
                results.add(buildFsErrorResult("Destination exists: " + dst,
                        DownloadFileStreamResult::new, null));
                return results.iterator();
            }
            if (createParentDirs && dst.getParent() != null) {
                Files.createDirectories(dst.getParent());
            }

            int effectiveChunkSize = chunkSize > 0 ? chunkSize
                    : FsConstants.DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE;
            try (InputStream in = Files.newInputStream(src);
                 OutputStream out = Files.newOutputStream(dst)) {
                byte[] buffer = new byte[effectiveChunkSize];
                int index = 0;
                int bytesRead;

                // Read ahead approach to determine last chunk
                byte[] currentChunk = new byte[effectiveChunkSize];
                int currentRead = in.read(currentChunk);

                while (currentRead != -1) {
                    byte[] nextChunk = new byte[effectiveChunkSize];
                    int nextRead = in.read(nextChunk);
                    boolean isLast = (nextRead == -1);

                    out.write(currentChunk, 0, currentRead);
                    results.add(DownloadFileStreamResult.builder()
                            .code(StatusCode.SUCCESS.getCode())
                            .message("Success")
                            .data(DownloadFileChunkData.builder()
                                    .sourcePath(src.toString())
                                    .localPath(dst.toString())
                                    .chunkSize(currentRead)
                                    .chunkIndex(index)
                                    .lastChunk(isLast)
                                    .build())
                            .build());
                    index++;

                    currentChunk = nextChunk;
                    currentRead = nextRead;
                }
            }

            if (preservePermissions) {
                copyPermissions(src, dst);
            }
            return results.iterator();

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to download file streaming", e);
            results.add(buildFsErrorResult("download_file_stream: " + e.getMessage(),
                    DownloadFileStreamResult::new, null));
            return results.iterator();
        }
    }

    // ==================== List Files / Directories ====================

    @Override
    public ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth,
                                     String sortBy, boolean sortDescending,
                                     List<String> fileTypes, Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to list files");

        try {
            Path basePath = resolvePath(path, false);
            if (!Files.isDirectory(basePath)) {
                return buildFsErrorResult("Path is not a directory: " + basePath,
                        ListFilesResult::new, null);
            }

            List<FileSystemItem> items = listItemsInternal(basePath, true, false,
                    recursive, maxDepth, sortBy, sortDescending, fileTypes);

            ListFilesResult result = ListFilesResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(FileSystemData.builder()
                            .totalCount(items.size())
                            .listItems(items)
                            .rootPath(basePath.toString())
                            .recursive(recursive)
                            .maxDepth(maxDepth)
                            .build())
                    .build();

            Loggers.SYS_OPERATION.info("End to list files, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to list files", e);
            return buildFsErrorResult("list_files: " + e.getMessage(), ListFilesResult::new, null);
        }
    }

    @Override
    public ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth,
                                          String sortBy, boolean sortDescending,
                                          Map<String, Object> options) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to list directories");

        try {
            Path basePath = resolvePath(path, false);
            if (!Files.isDirectory(basePath)) {
                return buildFsErrorResult("Path is not a directory: " + basePath,
                        ListDirsResult::new, null);
            }

            List<FileSystemItem> items = listItemsInternal(basePath, false, true,
                    recursive, maxDepth, sortBy, sortDescending, null);

            ListDirsResult result = ListDirsResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(FileSystemData.builder()
                            .totalCount(items.size())
                            .listItems(items)
                            .rootPath(basePath.toString())
                            .recursive(recursive)
                            .maxDepth(maxDepth)
                            .build())
                    .build();

            Loggers.SYS_OPERATION.info("End to list directories, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to list directories", e);
            return buildFsErrorResult("list_directories: " + e.getMessage(), ListDirsResult::new, null);
        }
    }

    // ==================== Search Files ====================

    @Override
    public SearchFilesResult searchFiles(String path, String pattern,
                                         List<String> excludePatterns) {
        long startTime = System.currentTimeMillis();
        Loggers.SYS_OPERATION.info("Start to search files");

        try {
            Path basePath = resolvePath(path, false);
            if (!Files.isDirectory(basePath)) {
                return buildFsErrorResult("Path is not a directory: " + basePath,
                        SearchFilesResult::new, null);
            }

            List<FileSystemItem> matchedItems = searchFilesInternal(basePath, pattern, excludePatterns);

            SearchFilesResult result = SearchFilesResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(SearchFilesData.builder()
                            .totalMatches(matchedItems.size())
                            .matchingFiles(matchedItems)
                            .searchPath(basePath.toString())
                            .searchPattern(pattern)
                            .excludePatterns(excludePatterns)
                            .build())
                    .build();

            Loggers.SYS_OPERATION.info("End to search files, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;

        } catch (Exception e) {
            Loggers.SYS_OPERATION.error("Failed to search files", e);
            return buildFsErrorResult("search_files: " + e.getMessage(), SearchFilesResult::new, null);
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Resolve path relative to workDir if configured. Enforces sandbox.
     */
    private static final Pattern UNSAFE_CHAR_PATTERN = Pattern.compile("[^\\w.-]");

    private Path resolvePath(String path, boolean createParent) {
        String workDirVal = null;
        if (getRunConfig() instanceof LocalWorkConfig config) {
            workDirVal = config.getWorkDir();
        }

        Path finalPath;
        if (workDirVal == null) {
            finalPath = Path.of(path).toAbsolutePath().normalize();
        } else {
            Path workDir = toRealOrAbsolutePath(Path.of(workDirVal));
            Path rawResolved = toRealOrAbsolutePath(workDir.resolve(path));
            Path relPath;
            try {
                relPath = workDir.relativize(rawResolved);
            } catch (IllegalArgumentException e) {
                throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                        "execution", "resolve_path",
                        "error_msg", "Access denied: Path " + path + " traverses outside " + workDir);
            }
            if (relPath.startsWith("..")) {
                throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                        "execution", "resolve_path",
                        "error_msg", "Access denied: Path " + path + " traverses outside " + workDir);
            }
            // Sanitize each path segment: replace non-word, non-dot, non-hyphen chars with '_'
            Path sanitized = workDir;
            for (int i = 0; i < relPath.getNameCount(); i++) {
                String part = relPath.getName(i).toString();
                String cleanPart = UNSAFE_CHAR_PATTERN.matcher(part).replaceAll("_");
                sanitized = sanitized.resolve(cleanPart);
            }
            finalPath = sanitized;
        }

        if (createParent && finalPath.getParent() != null) {
            try {
                Files.createDirectories(finalPath.getParent());
            } catch (IOException e) {
                Loggers.SYS_OPERATION.warning("Failed to create parent directories: {}", e.getMessage());
            }
        }

        return finalPath;
    }

    /**
     * Resolve a path to its real (canonical) path if it exists, otherwise fall back to absolute + normalize.
     * This mirrors Python's pathlib.Path.resolve() which follows symlinks.
     */
    private static Path toRealOrAbsolutePath(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            // Path does not exist yet — fall back to absolute + normalize
            return p.toAbsolutePath().normalize();
        }
    }

    private void validateReadParams(Integer head, Integer tail, int[] lineRange, String mode) {
        // Binary mode: no text-only params
        if ("bytes".equals(mode)) {
            if (head != null || tail != null || lineRange != null) {
                throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                        "execution", "validate_read_params",
                        "error_msg", "Parameters 'head', 'tail', and 'line_range' are only supported in text mode");
            }
            return;
        }

        // Mutually exclusive check
        if (tail != null) {
            if (head != null) {
                throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                        "execution", "validate_read_params",
                        "error_msg", "tail and head cannot be specified simultaneously");
            }
            if (lineRange != null) {
                throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                        "execution", "validate_read_params",
                        "error_msg", "tail and line_range cannot be specified simultaneously");
            }
        } else if (head != null && lineRange != null) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                    "execution", "validate_read_params",
                    "error_msg", "head and line_range cannot be specified simultaneously");
        }
    }

    // --- Read helpers ---

    private byte[] readBytesContent(Path filePath, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            return Files.readAllBytes(filePath);
        }
        byte[] bytes = new byte[chunkSize];
        int read;
        try (InputStream in = Files.newInputStream(filePath)) {
            read = in.read(bytes);
        }
        if (read == -1) return new byte[0];
        return Arrays.copyOf(bytes, read);
    }

    /**
     * Split text into lines preserving original line endings (\r\n, \r, or \n).
     * Mirrors Python's str.splitlines(True).
     */
    private static List<String> splitLinesKeepEndings(String content) {
        List<String> lines = new ArrayList<>();
        int len = content.length();
        int start = 0;
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                lines.add(content.substring(start, i + 1));
                start = i + 1;
            } else if (c == '\r') {
                if (i + 1 < len && content.charAt(i + 1) == '\n') {
                    lines.add(content.substring(start, i + 2));
                    start = i + 2;
                    i++;
                } else {
                    lines.add(content.substring(start, i + 1));
                    start = i + 1;
                }
            }
        }
        if (start < len) {
            lines.add(content.substring(start));
        }
        return lines;
    }

    private Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(encoding);
    }

    private String extractTextContent(Object content) {
        if (content instanceof String strContent) {
            return strContent;
        }
        if (content instanceof Map<?, ?> mapContent) {
            Object value = mapContent.get("value");
            if (value == null) {
                value = mapContent.get("text");
            }
            if (value == null) {
                value = mapContent.get("content");
            }
            if (value != null) {
                return value.toString();
            }
        }
        return content != null ? content.toString() : "";
    }

    private String readTextContent(Path filePath, Charset charset, Integer head, Integer tail,
                                   int[] lineRange) throws IOException {
        // When no filtering params specified, read entire file content preserving original format
        if (head == null && tail == null && lineRange == null) {
            return Files.readString(filePath, charset);
        }

        String content = Files.readString(filePath, charset);
        List<String> lines = splitLinesKeepEndings(content);
        List<String> selectedLines;

        if (tail != null && tail > 0) {
            int start = Math.max(0, lines.size() - tail);
            selectedLines = lines.subList(start, lines.size());
        } else if (head != null && head > 0) {
            selectedLines = lines.subList(0, Math.min(head, lines.size()));
        } else if (lineRange != null && lineRange.length == 2) {
            int startLine = lineRange[0];
            int endLine = lineRange[1];
            if (startLine <= 0 || endLine <= 0 || startLine > endLine) {
                return "";
            }
            int startIdx = Math.min(startLine - 1, lines.size());
            int endIdx = Math.min(endLine, lines.size());
            selectedLines = lines.subList(startIdx, endIdx);
        } else {
            // Negative head/tail — return empty
            return "";
        }

        return String.join("", selectedLines);
    }

    private void readBytesStream(Path filePath, int chunkSize, List<ReadFileStreamResult> results)
            throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            byte[] currentChunk = new byte[chunkSize];
            int currentRead = in.read(currentChunk);
            int index = 0;

            while (currentRead != -1) {
                byte[] nextChunk = new byte[chunkSize];
                int nextRead = in.read(nextChunk);
                boolean isLast = (nextRead == -1);

                results.add(ReadFileStreamResult.builder()
                        .code(StatusCode.SUCCESS.getCode())
                        .message("Success")
                        .data(ReadFileChunkData.builder()
                                .path(filePath.toString())
                                .chunkContent(Arrays.copyOf(currentChunk, currentRead))
                                .mode("bytes")
                                .chunkSize(currentRead)
                                .chunkIndex(index)
                                .lastChunk(isLast)
                                .build())
                        .build());
                index++;
                currentChunk = nextChunk;
                currentRead = nextRead;
            }
        }
    }

    private void streamTextFile(Path filePath, Charset charset, Integer head, Integer tail,
                                int[] lineRange, String mode, List<ReadFileStreamResult> results)
            throws IOException {
        String content = Files.readString(filePath, charset);
        List<String> allLines = splitLinesKeepEndings(content);
        List<String> selectedLines;

        if (head == null && tail == null && lineRange == null) {
            selectedLines = allLines;
        } else if (tail != null && tail > 0) {
            int start = Math.max(0, allLines.size() - tail);
            selectedLines = new ArrayList<>(allLines.subList(start, allLines.size()));
        } else if (head != null && head > 0) {
            selectedLines = new ArrayList<>(allLines.subList(0, Math.min(head, allLines.size())));
        } else if (lineRange != null && lineRange.length == 2) {
            int startLine = lineRange[0];
            int endLine = lineRange[1];
            if (startLine <= 0 || endLine <= 0 || startLine > endLine) {
                results.add(ReadFileStreamResult.builder()
                        .code(StatusCode.SUCCESS.getCode())
                        .message("Success")
                        .data(ReadFileChunkData.builder()
                                .path(filePath.toString())
                                .chunkContent("")
                                .mode(mode)
                                .chunkSize(0)
                                .chunkIndex(0)
                                .lastChunk(true)
                                .build())
                        .build());
                return;
            }
            int startIdx = Math.min(startLine - 1, allLines.size());
            int endIdx = Math.min(endLine, allLines.size());
            selectedLines = new ArrayList<>(allLines.subList(startIdx, endIdx));
        } else {
            // Negative head/tail — return empty
            selectedLines = new ArrayList<>();
        }

        emitStreamChunks(filePath, charset, mode, selectedLines, results);
    }

    private void emitStreamChunks(Path filePath, Charset charset, String mode,
                                  List<String> lines, List<ReadFileStreamResult> results) {
        if (lines.isEmpty()) {
            results.add(ReadFileStreamResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(ReadFileChunkData.builder()
                            .path(filePath.toString())
                            .chunkContent("")
                            .mode(mode)
                            .chunkSize(0)
                            .chunkIndex(0)
                            .lastChunk(true)
                            .build())
                    .build());
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isLast = (i == lines.size() - 1);
            results.add(ReadFileStreamResult.builder()
                    .code(StatusCode.SUCCESS.getCode())
                    .message("Success")
                    .data(ReadFileChunkData.builder()
                            .path(filePath.toString())
                            .chunkContent(line)
                            .mode(mode)
                            .chunkSize(line.getBytes(charset).length)
                            .chunkIndex(i)
                            .lastChunk(isLast)
                            .build())
                    .build());
        }
    }

    // --- Transfer helpers ---

    private long transferFile(Path src, Path dst, int chunkSize) throws IOException {
        if (chunkSize <= 0) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return Files.size(dst);
        }

        long totalSize = 0;
        try (InputStream in = Files.newInputStream(src);
             OutputStream out = Files.newOutputStream(dst)) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalSize += bytesRead;
            }
        }
        return totalSize;
    }

    // --- Permission helpers ---

    private void applyPermissions(Path path, String permissions) {
        if (isWindows()) return;
        try {
            int perm = Integer.parseInt(permissions, 8);
            // PosixFilePermission set from octal - simplified
            Set<java.nio.file.attribute.PosixFilePermission> permSet =
                    java.nio.file.attribute.PosixFilePermissions.fromString(
                            toPermString(perm));
            Files.setPosixFilePermissions(path, permSet);
        } catch (Exception e) {
            Loggers.SYS_OPERATION.warning("Failed to apply permissions: {}", e.getMessage());
        }
    }

    private void copyPermissions(Path src, Path dst) {
        if (isWindows()) return;
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms =
                    Files.getPosixFilePermissions(src);
            Files.setPosixFilePermissions(dst, perms);
        } catch (Exception e) {
            Loggers.SYS_OPERATION.warning("Failed to copy permissions: {}", e.getMessage());
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String toPermString(int perm) {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 2; i >= 0; i--) {
            int p = (perm >> (i * 3)) & 7;
            sb.append((p & 4) != 0 ? 'r' : '-');
            sb.append((p & 2) != 0 ? 'w' : '-');
            sb.append((p & 1) != 0 ? 'x' : '-');
        }
        return sb.toString();
    }

    // --- List / Search helpers ---

    private List<FileSystemItem> listItemsInternal(Path basePath, boolean includeFiles,
                                                   boolean includeDirs, boolean recursive,
                                                   Integer maxDepth, String sortBy,
                                                   boolean sortDescending, List<String> fileTypes) throws IOException {
        List<FileSystemItem> items = new ArrayList<>();

        if (!recursive) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(basePath)) {
                for (Path p : stream) {
                    boolean isDir = Files.isDirectory(p);
                    if (!includeFiles && !isDir) continue;
                    if (!includeDirs && isDir) continue;
                    if (fileTypes != null && !isDir) {
                        String ext = getExtension(p);
                        if (!fileTypes.contains(ext)) continue;
                    }
                    FileSystemItem item = createFsItem(p);
                    if (item != null) items.add(item);
                }
            }
        } else {
            int effectiveMaxDepth = maxDepth != null ? maxDepth : Integer.MAX_VALUE;
            int walkDepth = effectiveMaxDepth == Integer.MAX_VALUE ? Integer.MAX_VALUE : effectiveMaxDepth + 1;
            Files.walkFileTree(basePath, EnumSet.noneOf(FileVisitOption.class), walkDepth,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (dir.equals(basePath)) return FileVisitResult.CONTINUE;
                            if (includeDirs) {
                                FileSystemItem item = createFsItem(dir);
                                if (item != null) items.add(item);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!includeFiles) return FileVisitResult.CONTINUE;
                            if (fileTypes != null) {
                                String ext = getExtension(file);
                                if (!fileTypes.contains(ext)) return FileVisitResult.CONTINUE;
                            }
                            FileSystemItem item = createFsItem(file);
                            if (item != null) items.add(item);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }

        sortItems(items, sortBy, sortDescending);
        return items;
    }

    private List<FileSystemItem> searchFilesInternal(Path basePath, String pattern,
                                                     List<String> excludePatterns) throws IOException {
        List<FileSystemItem> items = new ArrayList<>();

        // For simple patterns without path separators, match against filename only
        // (mirrors Python's fnmatch behavior). For patterns with path separators,
        // match against the full relative path.
        boolean isSimplePattern = !pattern.contains("/") && !pattern.contains("\\");
        PathMatcher matcher = basePath.getFileSystem().getPathMatcher("glob:" + pattern);

        Set<Path> excludeSet = new HashSet<>();
        if (excludePatterns != null) {
            for (String excludePattern : excludePatterns) {
                boolean isSimpleExclude = !excludePattern.contains("/") && !excludePattern.contains("\\");
                PathMatcher excludeMatcher = basePath.getFileSystem().getPathMatcher("glob:" + excludePattern);
                try (Stream<Path> walkStream = Files.walk(basePath)) {
                    walkStream.filter(p -> {
                        Path toMatch = isSimpleExclude ? p.getFileName() : basePath.relativize(p);
                        return excludeMatcher.matches(toMatch);
                    }).forEach(excludeSet::add);
                }
            }
        }

        try (Stream<Path> walkStream = Files.walk(basePath)) {
            walkStream
                    .filter(p -> !p.equals(basePath))
                    .filter(p -> {
                        Path toMatch = isSimplePattern ? p.getFileName() : basePath.relativize(p);
                        return matcher.matches(toMatch);
                    })
                    .filter(p -> !excludeSet.contains(p))
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        FileSystemItem item = createFsItem(p);
                        if (item != null) items.add(item);
                    });
        }

        return items;
    }

    private FileSystemItem createFsItem(Path p) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            boolean isDir = attrs.isDirectory();
            return FileSystemItem.builder()
                    .name(p.getFileName().toString())
                    .path(p.toString())
                    .size(attrs.size())
                    .modifiedTime(LocalDateTime.ofInstant(
                            attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()).toString())
                    .directory(isDir)
                    .type(isDir ? null : getExtension(p))
                    .build();
        } catch (Exception e) {
            Loggers.SYS_OPERATION.warning("Failed to create fs item for: {}", p, e);
            return null;
        }
    }

    private void sortItems(List<FileSystemItem> items, String sortBy, boolean reverse) {
        Comparator<FileSystemItem> comparator = switch (sortBy != null ? sortBy : "name") {
            case "modified_time" -> Comparator.comparing(FileSystemItem::getModifiedTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "size" -> Comparator.comparingLong(FileSystemItem::getSize);
            default -> Comparator.comparing(FileSystemItem::getName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (reverse) {
            comparator = comparator.reversed();
        }
        items.sort(comparator);
    }

    private String getExtension(Path p) {
        String name = p.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(dotIndex) : "";
    }

    // --- Error result builder ---

    @SuppressWarnings("unchecked")
    private <T extends BaseResult<?>> T buildFsErrorResult(String errorMsg,
                                                            BaseResult.ResultFactory<T> factory,
                                                            Object data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR,
                "fs_operation", errorMsg,
                factory, data);
    }
}
