// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.fs.BaseFsOperation;
import com.openjiuwen.core.sysoperation.registry.Operation;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import com.openjiuwen.core.sysoperation.result.FileMode;
import com.openjiuwen.core.sysoperation.result.SortBy;
import com.openjiuwen.core.sysoperation.result.fs.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Local file system operation implementation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.local.fs_operation.FsOperation
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@Operation(name = "fs", mode = OperationMode.LOCAL, description = "local fs operation")
public class LocalFsOperation extends BaseFsOperation {

    private static final int DEFAULT_CHUNK_SIZE = 8192;
    private static final int DEFAULT_TRANSFER_CHUNK_SIZE = 1024 * 1024;

    static {
        OperationRegistry.register(LocalFsOperation.class, "fs", OperationMode.LOCAL, "local fs operation");
    }

    public LocalFsOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ReadFileResult> readFile(
            String path, FileMode mode, Integer head, Integer tail,
            Integer lineRangeStart, Integer lineRangeEnd, String encoding,
            int chunkSize, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = resolvePath(path, false);
                if (!Files.isRegularFile(filePath)) {
                    return ReadFileResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("read_file", "File not found: " + filePath)
                    );
                }

                Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
                String content;

                if (mode == FileMode.BYTES) {
                    // Read binary, return as base64
                    byte[] bytes = Files.readAllBytes(filePath);
                    if (chunkSize > 0 && bytes.length > chunkSize) {
                        bytes = Arrays.copyOf(bytes, chunkSize);
                    }
                    content = Base64.getEncoder().encodeToString(bytes);
                } else if (head == null && tail == null && lineRangeStart == null && lineRangeEnd == null) {
                    // Full text read
                    content = Files.readString(filePath, charset);
                } else {
                    // Line-based read with filtering
                    List<String> lines = Files.readAllLines(filePath, charset);
                    content = filterLines(lines, head, tail, lineRangeStart, lineRangeEnd);
                }

                ReadFileData data = ReadFileData.builder()
                    .path(filePath.toString())
                    .content(content)
                    .mode(mode != null ? mode : FileMode.TEXT)
                    .build();

                return ReadFileResult.success(data);
            } catch (Exception e) {
                return ReadFileResult.failure(
                    StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                    formatFsError("read_file", e.getMessage())
                );
            }
        });
    }

    @Override
    public Stream<ReadFileStreamResult> readFileStream(
            String path, FileMode mode, Integer head, Integer tail,
            Integer lineRangeStart, Integer lineRangeEnd, String encoding,
            int chunkSize, Map<String, Object> options) {
        // Simplified: return single-element stream with full content
        return readFile(path, mode, head, tail, lineRangeStart, lineRangeEnd, encoding, chunkSize, options)
            .thenApply(result -> {
                if (result.isFailure()) {
                    return Stream.of(ReadFileStreamResult.failure(result.getCode(), result.getMessage()));
                }
                ReadFileChunkData chunkData = ReadFileChunkData.builder()
                    .path(result.getData().getPath())
                    .chunkContent(result.getData().getContent())
                    .mode(result.getData().getMode())
                    .chunkSize(result.getData().getContent().length())
                    .chunkIndex(0)
                    .lastChunk(true)
                    .build();
                return Stream.of(ReadFileStreamResult.success(chunkData));
            })
            .join();
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(
            String path, Object content, FileMode mode, boolean prependNewline,
            boolean appendNewline, boolean createIfNotExist, String permissions,
            String encoding, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath = resolvePath(path, true);
                if (Files.isDirectory(filePath)) {
                    return WriteFileResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("write_file", "Target path is a directory: " + filePath)
                    );
                }
                if (!createIfNotExist && !Files.exists(filePath)) {
                    return WriteFileResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("write_file", "File does not exist: " + filePath)
                    );
                }

                Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
                byte[] dataBytes;

                if (mode == FileMode.BYTES) {
                    if (content instanceof byte[]) {
                        dataBytes = (byte[]) content;
                    } else if (content instanceof String) {
                        // Assume base64 encoded
                        dataBytes = Base64.getDecoder().decode((String) content);
                    } else {
                        dataBytes = content.toString().getBytes(charset);
                    }
                } else {
                    String text = content.toString();
                    if (prependNewline) {
                        text = "\n" + text;
                    }
                    if (appendNewline) {
                        text = text + "\n";
                    }
                    dataBytes = text.getBytes(charset);
                }

                Files.write(filePath, dataBytes);
                applyPermissions(filePath, permissions);

                WriteFileData data = WriteFileData.builder()
                    .path(filePath.toString())
                    .size(dataBytes.length)
                    .mode(mode != null ? mode : FileMode.TEXT)
                    .build();

                return WriteFileResult.success(data);
            } catch (Exception e) {
                return WriteFileResult.failure(
                    StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                    formatFsError("write_file", e.getMessage())
                );
            }
        });
    }

    @Override
    public CompletableFuture<UploadFileResult> uploadFile(
            String localPath, String targetPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path src = Paths.get(localPath).toAbsolutePath().normalize();
                Path dst = resolvePath(targetPath, createParentDirs);

                if (!Files.isRegularFile(src)) {
                    return UploadFileResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("upload_file", "Source not found: " + src)
                    );
                }
                if (Files.exists(dst) && !overwrite) {
                    return UploadFileResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("upload_file", "Target exists: " + dst)
                    );
                }

                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                long size = Files.size(dst);
                if (preservePermissions) {
                    copyPermissions(src, dst);
                }

                UploadFileData data = UploadFileData.builder()
                    .localPath(src.toString())
                    .targetPath(dst.toString())
                    .size(size)
                    .build();

                return UploadFileResult.success(data);
            } catch (Exception e) {
                return UploadFileResult.failure(
                    StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                    formatFsError("upload_file", e.getMessage())
                );
            }
        });
    }

    @Override
    public Stream<UploadFileStreamResult> uploadFileStream(
            String localPath, String targetPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        // Simplified implementation
        return uploadFile(localPath, targetPath, overwrite, createParentDirs, preservePermissions, chunkSize, options)
            .thenApply(result -> {
                if (result.isFailure()) {
                    return Stream.of(UploadFileStreamResult.failure(result.getCode(), result.getMessage()));
                }
                UploadFileChunkData chunkData = UploadFileChunkData.builder()
                    .localPath(result.getData().getLocalPath())
                    .targetPath(result.getData().getTargetPath())
                    .chunkSize((int) result.getData().getSize())
                    .chunkIndex(0)
                    .lastChunk(true)
                    .build();
                return Stream.of(UploadFileStreamResult.success(chunkData));
            })
            .join();
    }

    @Override
    public CompletableFuture<DownloadFileResult> downloadFile(
            String sourcePath, String localPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path src = resolvePath(sourcePath, false);
                Path dst = Paths.get(localPath).toAbsolutePath().normalize();

                if (!Files.isRegularFile(src)) {
                    return DownloadFileResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("download_file", "Source not found: " + src)
                    );
                }
                if (Files.exists(dst) && !overwrite) {
                    return DownloadFileResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("download_file", "Destination exists: " + dst)
                    );
                }
                if (createParentDirs) {
                    Files.createDirectories(dst.getParent());
                }

                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                long size = Files.size(dst);
                if (preservePermissions) {
                    copyPermissions(src, dst);
                }

                DownloadFileData data = DownloadFileData.builder()
                    .sourcePath(src.toString())
                    .localPath(dst.toString())
                    .size(size)
                    .build();

                return DownloadFileResult.success(data);
            } catch (Exception e) {
                return DownloadFileResult.failure(
                    StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                    formatFsError("download_file", e.getMessage())
                );
            }
        });
    }

    @Override
    public Stream<DownloadFileStreamResult> downloadFileStream(
            String sourcePath, String localPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        return downloadFile(sourcePath, localPath, overwrite, createParentDirs, preservePermissions, chunkSize, options)
            .thenApply(result -> {
                if (result.isFailure()) {
                    return Stream.of(DownloadFileStreamResult.failure(result.getCode(), result.getMessage()));
                }
                DownloadFileChunkData chunkData = DownloadFileChunkData.builder()
                    .sourcePath(result.getData().getSourcePath())
                    .localPath(result.getData().getLocalPath())
                    .chunkSize((int) result.getData().getSize())
                    .chunkIndex(0)
                    .lastChunk(true)
                    .build();
                return Stream.of(DownloadFileStreamResult.success(chunkData));
            })
            .join();
    }

    @Override
    public CompletableFuture<ListFilesResult> listFiles(
            String path, boolean recursive, Integer maxDepth, SortBy sortBy,
            boolean sortDescending, List<String> fileTypes, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path basePath = resolvePath(path, false);
                if (!Files.isDirectory(basePath)) {
                    return ListFilesResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("list_files", "Path is not a directory: " + basePath)
                    );
                }

                List<FileSystemItem> items = listItems(basePath, recursive, maxDepth, true, false, fileTypes);
                sortItems(items, sortBy, sortDescending);

                FileSystemData data = FileSystemData.builder()
                    .totalCount(items.size())
                    .listItems(items)
                    .rootPath(basePath.toString())
                    .recursive(recursive)
                    .maxDepth(maxDepth)
                    .build();

                return ListFilesResult.success(data);
            } catch (Exception e) {
                return ListFilesResult.failure(
                    StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                    formatFsError("list_files", e.getMessage())
                );
            }
        });
    }

    @Override
    public CompletableFuture<ListDirsResult> listDirectories(
            String path, boolean recursive, Integer maxDepth, SortBy sortBy,
            boolean sortDescending, Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path basePath = resolvePath(path, false);
                if (!Files.isDirectory(basePath)) {
                    return ListDirsResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("list_directories", "Path is not a directory: " + basePath)
                    );
                }

                List<FileSystemItem> items = listItems(basePath, recursive, maxDepth, false, true, null);
                sortItems(items, sortBy, sortDescending);

                FileSystemData data = FileSystemData.builder()
                    .totalCount(items.size())
                    .listItems(items)
                    .rootPath(basePath.toString())
                    .recursive(recursive)
                    .maxDepth(maxDepth)
                    .build();

                return ListDirsResult.success(data);
            } catch (Exception e) {
                return ListDirsResult.failure(
                    StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                    formatFsError("list_directories", e.getMessage())
                );
            }
        });
    }

    @Override
    public CompletableFuture<SearchFilesResult> searchFiles(
            String path, String pattern, List<String> excludePatterns) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path basePath = resolvePath(path, false);
                if (!Files.isDirectory(basePath)) {
                    return SearchFilesResult.failure(
                        StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                        formatFsError("search_files", "Path is not a directory: " + basePath)
                    );
                }

                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                List<PathMatcher> excludeMatchers = new ArrayList<>();
                if (excludePatterns != null) {
                    for (String ep : excludePatterns) {
                        excludeMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + ep));
                    }
                }

                List<FileSystemItem> items = new ArrayList<>();
                try (var walker = Files.walk(basePath)) {
                    walker.filter(Files::isRegularFile)
                        .filter(p -> matcher.matches(basePath.relativize(p)))
                        .filter(p -> excludeMatchers.stream().noneMatch(em -> em.matches(basePath.relativize(p))))
                        .forEach(p -> {
                            FileSystemItem item = createFileSystemItem(p);
                            if (item != null) {
                                items.add(item);
                            }
                        });
                }

                SearchFilesData data = SearchFilesData.builder()
                    .totalMatches(items.size())
                    .matchingFiles(items)
                    .searchPath(basePath.toString())
                    .searchPattern(pattern)
                    .excludePatterns(excludePatterns)
                    .build();

                return SearchFilesResult.success(data);
            } catch (Exception e) {
                return SearchFilesResult.failure(
                    StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(),
                    formatFsError("search_files", e.getMessage())
                );
            }
        });
    }

    // ==================== Helper Methods ====================

    private Path resolvePath(String path, boolean createParent) throws IOException {
        LocalWorkConfig config = getLocalWorkConfig();
        String workDirVal = config != null ? config.getWorkDir() : null;
        
        Path finalPath;
        if (workDirVal == null) {
            finalPath = Paths.get(path).toAbsolutePath().normalize();
        } else {
            Path workDir = Paths.get(workDirVal).toAbsolutePath().normalize();
            Path rawResolved = workDir.resolve(path).normalize();
            
            if (!rawResolved.startsWith(workDir)) {
                throw new SecurityException("Access denied: Path " + path + " traverses outside " + workDir);
            }
            finalPath = rawResolved;
        }

        if (createParent && finalPath.getParent() != null) {
            Files.createDirectories(finalPath.getParent());
        }

        return finalPath;
    }

    private LocalWorkConfig getLocalWorkConfig() {
        Object config = getRunConfig();
        if (config instanceof LocalWorkConfig) {
            return (LocalWorkConfig) config;
        }
        return null;
    }

    private String filterLines(List<String> lines, Integer head, Integer tail,
                               Integer lineRangeStart, Integer lineRangeEnd) {
        List<String> result;
        
        if (tail != null) {
            int start = Math.max(0, lines.size() - tail);
            result = lines.subList(start, lines.size());
        } else if (lineRangeStart != null && lineRangeEnd != null) {
            int start = Math.max(0, lineRangeStart - 1);
            int end = Math.min(lines.size(), lineRangeEnd);
            result = lines.subList(start, end);
        } else if (head != null) {
            result = lines.subList(0, Math.min(lines.size(), head));
        } else {
            result = lines;
        }
        
        return String.join("\n", result);
    }

    private List<FileSystemItem> listItems(Path basePath, boolean recursive, Integer maxDepth,
                                            boolean includeFiles, boolean includeDirs,
                                            List<String> fileTypes) throws IOException {
        List<FileSystemItem> items = new ArrayList<>();
        int depth = recursive ? (maxDepth != null ? maxDepth : Integer.MAX_VALUE) : 1;

        try (var walker = Files.walk(basePath, depth)) {
            walker.skip(1) // Skip the root
                .filter(p -> {
                    boolean isDir = Files.isDirectory(p);
                    if (!includeFiles && !isDir) return false;
                    if (!includeDirs && isDir) return false;
                    if (fileTypes != null && !isDir) {
                        String ext = getExtension(p);
                        return fileTypes.contains(ext);
                    }
                    return true;
                })
                .forEach(p -> {
                    FileSystemItem item = createFileSystemItem(p);
                    if (item != null) {
                        items.add(item);
                    }
                });
        }
        
        return items;
    }

    private void sortItems(List<FileSystemItem> items, SortBy sortBy, boolean descending) {
        Comparator<FileSystemItem> comparator;
        if (sortBy == SortBy.MODIFIED_TIME) {
            comparator = Comparator.comparing(FileSystemItem::getModifiedTime);
        } else if (sortBy == SortBy.SIZE) {
            comparator = Comparator.comparingLong(FileSystemItem::getSize);
        } else {
            comparator = Comparator.comparing(FileSystemItem::getName);
        }
        
        if (descending) {
            comparator = comparator.reversed();
        }
        
        items.sort(comparator);
    }

    private FileSystemItem createFileSystemItem(Path p) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            boolean isDir = attrs.isDirectory();
            LocalDateTime modTime = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
            
            return FileSystemItem.builder()
                .name(p.getFileName().toString())
                .path(p.toString())
                .size(attrs.size())
                .modifiedTime(modTime.toString())
                .directory(isDir)
                .type(isDir ? null : getExtension(p))
                .build();
        } catch (IOException e) {
            return null;
        }
    }

    private String getExtension(Path p) {
        String name = p.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx) : "";
    }

    private void applyPermissions(Path path, String permissions) {
        // Best effort - may not work on all platforms
        if (permissions != null && !System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                int perm = Integer.parseInt(permissions, 8);
                Set<java.nio.file.attribute.PosixFilePermission> perms = 
                    java.nio.file.attribute.PosixFilePermissions.fromString(
                        permissionIntToString(perm));
                Files.setPosixFilePermissions(path, perms);
            } catch (Exception ignored) {
                // Permission application is best-effort
            }
        }
    }

    private void copyPermissions(Path src, Path dst) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Set<java.nio.file.attribute.PosixFilePermission> perms = 
                    Files.getPosixFilePermissions(src);
                Files.setPosixFilePermissions(dst, perms);
            } catch (Exception ignored) {
                // Permission copy is best-effort
            }
        }
    }

    private String permissionIntToString(int perm) {
        StringBuilder sb = new StringBuilder(9);
        sb.append((perm & 0400) != 0 ? 'r' : '-');
        sb.append((perm & 0200) != 0 ? 'w' : '-');
        sb.append((perm & 0100) != 0 ? 'x' : '-');
        sb.append((perm & 0040) != 0 ? 'r' : '-');
        sb.append((perm & 0020) != 0 ? 'w' : '-');
        sb.append((perm & 0010) != 0 ? 'x' : '-');
        sb.append((perm & 0004) != 0 ? 'r' : '-');
        sb.append((perm & 0002) != 0 ? 'w' : '-');
        sb.append((perm & 0001) != 0 ? 'x' : '-');
        return sb.toString();
    }

    private String formatFsError(String operation, String message) {
        return String.format("[sys_operation][%s] execution error: %s", operation, message);
    }
}

