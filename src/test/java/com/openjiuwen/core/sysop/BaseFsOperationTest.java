/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.result.DownloadFileResult;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.ListDirsResult;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.UploadFileResult;
import com.openjiuwen.core.sysop.result.UploadFileStreamResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code BaseFsOperation} in
 * {@code openjiuwen/core/sys_operation/fs.py}.
 */
class BaseFsOperationTest {

    @Test
    void constantsMatchPythonDefaults() {
        assertThat(BaseFsOperation.SAFE_PATH_PATTERN.matcher("safe_Name-1.txt").find()).isFalse();
        assertThat(BaseFsOperation.SAFE_PATH_PATTERN.matcher("bad/path").find()).isTrue();
        assertThat(BaseFsOperation.DEFAULT_READ_CHUNK_SIZE).isZero();
        assertThat(BaseFsOperation.DEFAULT_UPLOAD_CHUNK_SIZE).isZero();
        assertThat(BaseFsOperation.DEFAULT_DOWNLOAD_CHUNK_SIZE).isZero();
        assertThat(BaseFsOperation.DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE).isEqualTo(1024 * 1024);
        assertThat(BaseFsOperation.DEFAULT_UPLOAD_STREAM_CHUNK_SIZE).isEqualTo(1024 * 1024);
        assertThat(BaseFsOperation.DEFAULT_READ_STREAM_CHUNK_SIZE).isEqualTo(8192);
        assertThat(BaseFsOperation.TAIL_CHUNK_SIZE).isEqualTo(1024);
    }

    @Test
    void listToolsExposePythonFsOperationMethods() {
        ExampleFsOperation operation = new ExampleFsOperation();

        List<ToolCard> cards = operation.listTools();

        assertThat(cards).extracting(ToolCard::getName)
                .containsExactly(
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
                );
    }

    @Test
    void enumValuesMatchPythonLiteralChoices() {
        assertThat(BaseFsOperation.FileMode.TEXT.value()).isEqualTo("text");
        assertThat(BaseFsOperation.FileMode.BYTES.value()).isEqualTo("bytes");
        assertThat(BaseFsOperation.SortBy.NAME.value()).isEqualTo("name");
        assertThat(BaseFsOperation.SortBy.MODIFIED_TIME.value()).isEqualTo("modified_time");
        assertThat(BaseFsOperation.SortBy.SIZE.value()).isEqualTo("size");
    }

    @Test
    void keyMethodSignaturesMatchPythonParameters() throws NoSuchMethodException {
        Method readFile = BaseFsOperation.class.getMethod(
                "readFile",
                String.class,
                BaseFsOperation.FileMode.class,
                Integer.class,
                Integer.class,
                BaseFsProtocal.LineRange.class,
                String.class,
                int.class,
                Map.class
        );
        assertThat(readFile.getReturnType()).isEqualTo(CompletableFuture.class);

        Method writeText = BaseFsOperation.class.getMethod(
                "writeFile",
                String.class,
                String.class,
                BaseFsOperation.FileMode.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                Map.class
        );
        assertThat(writeText.getParameterTypes()[1]).isEqualTo(String.class);

        Method writeBytes = BaseFsOperation.class.getMethod(
                "writeFile",
                String.class,
                byte[].class,
                BaseFsOperation.FileMode.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                Map.class
        );
        assertThat(writeBytes.getParameterTypes()[1]).isEqualTo(byte[].class);

        Method search = BaseFsOperation.class.getMethod("searchFiles", String.class, String.class, List.class);
        assertThat(search.getReturnType()).isEqualTo(CompletableFuture.class);
    }

    private static final class ExampleFsOperation extends BaseFsOperation {

        private ExampleFsOperation() {
            super("fs", OperationMode.LOCAL, "example fs operation", null);
        }

        @Override
        public CompletableFuture<ReadFileResult> readFile(String path,
                                                          FileMode mode,
                                                          Integer head,
                                                          Integer tail,
                                                          BaseFsProtocal.LineRange lineRange,
                                                          String encoding,
                                                          int chunkSize,
                                                          Map<String, Object> options) {
            return CompletableFuture.completedFuture(new ReadFileResult());
        }

        @Override
        public Flow.Publisher<ReadFileStreamResult> readFileStream(String path,
                                                                   FileMode mode,
                                                                   Integer head,
                                                                   Integer tail,
                                                                   BaseFsProtocal.LineRange lineRange,
                                                                   String encoding,
                                                                   int chunkSize,
                                                                   Map<String, Object> options) {
            return subscriber -> subscriber.onComplete();
        }

        @Override
        public CompletableFuture<WriteFileResult> writeFile(String path,
                                                            String content,
                                                            FileMode mode,
                                                            boolean prependNewline,
                                                            boolean appendNewline,
                                                            boolean append,
                                                            boolean createIfNotExist,
                                                            String permissions,
                                                            String encoding,
                                                            Map<String, Object> options) {
            return CompletableFuture.completedFuture(new WriteFileResult());
        }

        @Override
        public CompletableFuture<WriteFileResult> writeFile(String path,
                                                            byte[] content,
                                                            FileMode mode,
                                                            boolean prependNewline,
                                                            boolean appendNewline,
                                                            boolean append,
                                                            boolean createIfNotExist,
                                                            String permissions,
                                                            String encoding,
                                                            Map<String, Object> options) {
            return CompletableFuture.completedFuture(new WriteFileResult());
        }

        @Override
        public CompletableFuture<UploadFileResult> uploadFile(String localPath,
                                                              String targetPath,
                                                              boolean overwrite,
                                                              boolean createParentDirs,
                                                              boolean preservePermissions,
                                                              int chunkSize,
                                                              Map<String, Object> options) {
            return CompletableFuture.completedFuture(new UploadFileResult());
        }

        @Override
        public Flow.Publisher<UploadFileStreamResult> uploadFileStream(String localPath,
                                                                       String targetPath,
                                                                       boolean overwrite,
                                                                       boolean createParentDirs,
                                                                       boolean preservePermissions,
                                                                       int chunkSize,
                                                                       Map<String, Object> options) {
            return subscriber -> subscriber.onComplete();
        }

        @Override
        public CompletableFuture<DownloadFileResult> downloadFile(String sourcePath,
                                                                  String localPath,
                                                                  boolean overwrite,
                                                                  boolean createParentDirs,
                                                                  boolean preservePermissions,
                                                                  int chunkSize,
                                                                  Map<String, Object> options) {
            return CompletableFuture.completedFuture(new DownloadFileResult());
        }

        @Override
        public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(String sourcePath,
                                                                           String localPath,
                                                                           boolean overwrite,
                                                                           boolean createParentDirs,
                                                                           boolean preservePermissions,
                                                                           int chunkSize,
                                                                           Map<String, Object> options) {
            return subscriber -> subscriber.onComplete();
        }

        @Override
        public CompletableFuture<ListFilesResult> listFiles(String path,
                                                            boolean recursive,
                                                            Integer maxDepth,
                                                            SortBy sortBy,
                                                            boolean sortDescending,
                                                            List<String> fileTypes,
                                                            Map<String, Object> options) {
            return CompletableFuture.completedFuture(new ListFilesResult());
        }

        @Override
        public CompletableFuture<ListDirsResult> listDirectories(String path,
                                                                 boolean recursive,
                                                                 Integer maxDepth,
                                                                 SortBy sortBy,
                                                                 boolean sortDescending,
                                                                 Map<String, Object> options) {
            return CompletableFuture.completedFuture(new ListDirsResult());
        }

        @Override
        public CompletableFuture<SearchFilesResult> searchFiles(String path,
                                                                String pattern,
                                                                List<String> excludePatterns) {
            return CompletableFuture.completedFuture(new SearchFilesResult());
        }
    }
}
