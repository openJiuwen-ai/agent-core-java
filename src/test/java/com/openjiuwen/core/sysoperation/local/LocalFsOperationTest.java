// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysoperation.SysOperation;
import com.openjiuwen.core.sysoperation.SysOperationCard;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.fs.BaseFsOperation;
import com.openjiuwen.core.sysoperation.result.FileMode;
import com.openjiuwen.core.sysoperation.result.SortBy;
import com.openjiuwen.core.sysoperation.result.fs.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalFsOperation.
 * 
 * <p>对应 Python: tests/unit_tests/core/sys_operation/local/test_local_fs_operation.py
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@DisplayName("LocalFsOperation Tests")
class LocalFsOperationTest {

    @TempDir
    Path tempDir;

    private SysOperation sysOp;
    private BaseFsOperation fsOp;

    @BeforeAll
    static void initRegistry() {
        // Trigger static initializer to register operations
        try {
            Class.forName("com.openjiuwen.core.sysoperation.local.LocalFsOperation");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load LocalFsOperation", e);
        }
    }

    @BeforeEach
    void setUp() {
        LocalWorkConfig config = LocalWorkConfig.builder()
            .workDir(tempDir.toString())
            .build();
        SysOperationCard card = SysOperationCard.builder()
            .id("test_fs_op")
            .mode(OperationMode.LOCAL)
            .workConfig(config)
            .build();
        sysOp = new SysOperation(card);
        fsOp = (BaseFsOperation) sysOp.fs();
    }

    // ===================== Read File Tests =====================

    @Nested
    @DisplayName("TestFsReadFile")
    class TestFsReadFile {

        @Test
        @DisplayName("test_read_text_file_basic")
        void testReadTextFileBasic() throws ExecutionException, InterruptedException {
            String fileName = "test_text.txt";
            String content = "Hello, World!\nLine 2\nLine 3";

            // Create test file
            fsOp.writeFile(fileName, content, FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Read file
            ReadFileResult result = fsOp.readFile(fileName).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertNotNull(result.getData());
            assertEquals(content, result.getData().getContent());
            assertEquals(FileMode.TEXT, result.getData().getMode());
            assertTrue(result.getData().getPath().endsWith(fileName));
        }

        @Test
        @DisplayName("test_read_file_not_exists")
        void testReadFileNotExists() throws ExecutionException, InterruptedException {
            ReadFileResult result = fsOp.readFile("nonexistent_file.txt").get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().toLowerCase().contains("not found") 
                || result.getMessage().contains("File not found"));
        }

        @Test
        @DisplayName("test_read_file_head_lines")
        void testReadFileHeadLines() throws ExecutionException, InterruptedException {
            String fileName = "multiline.txt";
            String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";

            fsOp.writeFile(fileName, content, FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Read first 2 lines
            ReadFileResult result = fsOp.readFile(fileName, FileMode.TEXT, 2, null, null, null, "utf-8", 8192, null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            String[] resultLines = result.getData().getContent().split("\n");
            assertEquals(2, resultLines.length);
            assertEquals("Line 1", resultLines[0]);
            assertEquals("Line 2", resultLines[1]);
        }

        @Test
        @DisplayName("test_read_file_tail_lines")
        void testReadFileTailLines() throws ExecutionException, InterruptedException {
            String fileName = "multiline_tail.txt";
            String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";

            fsOp.writeFile(fileName, content, FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Read last 2 lines
            ReadFileResult result = fsOp.readFile(fileName, FileMode.TEXT, null, 2, null, null, "utf-8", 8192, null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            String[] resultLines = result.getData().getContent().split("\n");
            assertEquals(2, resultLines.length);
            assertEquals("Line 4", resultLines[0]);
            assertEquals("Line 5", resultLines[1]);
        }

        @Test
        @DisplayName("test_read_file_line_range")
        void testReadFileLineRange() throws ExecutionException, InterruptedException {
            String fileName = "range_test.txt";
            String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";

            fsOp.writeFile(fileName, content, FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Read lines 2-4
            ReadFileResult result = fsOp.readFile(fileName, FileMode.TEXT, null, null, 2, 4, "utf-8", 8192, null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            String[] resultLines = result.getData().getContent().split("\n");
            assertEquals(3, resultLines.length);
            assertEquals("Line 2", resultLines[0]);
            assertEquals("Line 4", resultLines[2]);
        }

        @Test
        @DisplayName("test_read_file_with_encoding")
        void testReadFileWithEncoding() throws ExecutionException, InterruptedException {
            String fileName = "utf8_content.txt";
            String content = "中文内容 Unicode: 你好世界";

            fsOp.writeFile(fileName, content, FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            ReadFileResult result = fsOp.readFile(fileName, FileMode.TEXT, null, null, null, null, "utf-8", 8192, null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getContent().contains("中文"));
            assertTrue(result.getData().getContent().contains("你好"));
        }

        @Test
        @DisplayName("test_read_file_path_is_directory")
        void testReadFilePathIsDirectory() throws ExecutionException, InterruptedException, IOException {
            String dirName = "test_directory";
            Files.createDirectories(tempDir.resolve(dirName));

            ReadFileResult result = fsOp.readFile(dirName).get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
        }
    }

    // ===================== Write File Tests =====================

    @Nested
    @DisplayName("TestFsWriteFile")
    class TestFsWriteFile {

        @Test
        @DisplayName("test_write_text_file_basic")
        void testWriteTextFileBasic() throws ExecutionException, InterruptedException {
            String fileName = "write_test.txt";
            String content = "Test content";

            WriteFileResult result = fsOp.writeFile(fileName, content, FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertTrue(result.getData().getSize() > 0);
            assertEquals(FileMode.TEXT, result.getData().getMode());

            // Verify content
            ReadFileResult readResult = fsOp.readFile(fileName).get();
            assertEquals(content, readResult.getData().getContent());
        }

        @Test
        @DisplayName("test_write_creates_parent_dirs")
        void testWriteCreatesParentDirs() throws ExecutionException, InterruptedException {
            String filePath = "subdir1/subdir2/deep_file.txt";
            String content = "Deep content";

            WriteFileResult result = fsOp.writeFile(filePath, content, FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());

            // Verify file exists
            ReadFileResult readResult = fsOp.readFile(filePath).get();
            assertEquals(StatusCode.SUCCESS.getCode(), readResult.getCode());
        }

        @Test
        @DisplayName("test_write_create_if_not_exist_false")
        void testWriteCreateIfNotExistFalse() throws ExecutionException, InterruptedException {
            WriteFileResult result = fsOp.writeFile(
                "new_file.txt", "content", FileMode.TEXT, false, false, false, "644", "utf-8", null
            ).get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().toLowerCase().contains("not exist"));
        }

        @Test
        @DisplayName("test_write_prepend_newline")
        void testWritePrependNewline() throws ExecutionException, InterruptedException {
            String fileName = "prepend_test.txt";
            String content = "Content after newline";

            fsOp.writeFile(fileName, content, FileMode.TEXT, true, false, true, "644", "utf-8", null).get();

            ReadFileResult readResult = fsOp.readFile(fileName).get();
            assertTrue(readResult.getData().getContent().startsWith("\n"));
        }

        @Test
        @DisplayName("test_write_append_newline")
        void testWriteAppendNewline() throws ExecutionException, InterruptedException {
            String fileName = "append_test.txt";
            String content = "Content before newline";

            fsOp.writeFile(fileName, content, FileMode.TEXT, false, true, true, "644", "utf-8", null).get();

            ReadFileResult readResult = fsOp.readFile(fileName).get();
            assertTrue(readResult.getData().getContent().endsWith("\n"));
        }

        @Test
        @DisplayName("test_write_to_directory_path_fails")
        void testWriteToDirectoryPathFails() throws ExecutionException, InterruptedException, IOException {
            String dirName = "test_dir";
            Files.createDirectories(tempDir.resolve(dirName));

            WriteFileResult result = fsOp.writeFile(dirName, "content", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().toLowerCase().contains("directory"));
        }
    }

    // ===================== Upload/Download Tests =====================

    @Nested
    @DisplayName("TestFsUploadDownload")
    class TestFsUploadDownload {

        @Test
        @DisplayName("test_upload_file_basic")
        void testUploadFileBasic() throws ExecutionException, InterruptedException, IOException {
            // Create source file outside work_dir
            Path sourceFile = Files.createTempFile("upload_source", ".txt");
            Files.writeString(sourceFile, "Upload test content");

            try {
                String targetName = "uploaded.txt";
                UploadFileResult result = fsOp.uploadFile(
                    sourceFile.toString(), targetName, false, true, true, 1024 * 1024, null
                ).get();

                assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
                assertTrue(result.getData().getSize() > 0);

                // Verify uploaded content
                ReadFileResult readResult = fsOp.readFile(targetName).get();
                assertEquals("Upload test content", readResult.getData().getContent());
            } finally {
                Files.deleteIfExists(sourceFile);
            }
        }

        @Test
        @DisplayName("test_upload_file_overwrite_false")
        void testUploadFileOverwriteFalse() throws ExecutionException, InterruptedException, IOException {
            // Create target file first
            String targetName = "existing.txt";
            fsOp.writeFile(targetName, "existing", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Create source file
            Path sourceFile = Files.createTempFile("upload_source", ".txt");
            Files.writeString(sourceFile, "new content");

            try {
                UploadFileResult result = fsOp.uploadFile(
                    sourceFile.toString(), targetName, false, true, true, 1024 * 1024, null
                ).get();

                assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
                assertTrue(result.getMessage().toLowerCase().contains("exists"));
            } finally {
                Files.deleteIfExists(sourceFile);
            }
        }

        @Test
        @DisplayName("test_upload_file_overwrite_true")
        void testUploadFileOverwriteTrue() throws ExecutionException, InterruptedException, IOException {
            String targetName = "to_overwrite.txt";
            fsOp.writeFile(targetName, "old content", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            Path sourceFile = Files.createTempFile("upload_source", ".txt");
            Files.writeString(sourceFile, "new content");

            try {
                UploadFileResult result = fsOp.uploadFile(
                    sourceFile.toString(), targetName, true, true, true, 1024 * 1024, null
                ).get();

                assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());

                ReadFileResult readResult = fsOp.readFile(targetName).get();
                assertEquals("new content", readResult.getData().getContent());
            } finally {
                Files.deleteIfExists(sourceFile);
            }
        }

        @Test
        @DisplayName("test_download_file_basic")
        void testDownloadFileBasic() throws ExecutionException, InterruptedException, IOException {
            String sourceName = "source_file.txt";
            fsOp.writeFile(sourceName, "Download content", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            Path downloadDir = Files.createTempDirectory("download_test");
            Path localPath = downloadDir.resolve("downloaded.txt");

            try {
                DownloadFileResult result = fsOp.downloadFile(
                    sourceName, localPath.toString(), false, true, true, 1024 * 1024, null
                ).get();

                assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());

                // Verify downloaded content
                String downloadedContent = Files.readString(localPath);
                assertEquals("Download content", downloadedContent);
            } finally {
                Files.deleteIfExists(localPath);
                Files.deleteIfExists(downloadDir);
            }
        }

        @Test
        @DisplayName("test_upload_file_source_not_exists")
        void testUploadFileSourceNotExists() throws ExecutionException, InterruptedException {
            UploadFileResult result = fsOp.uploadFile(
                "/nonexistent/source/file.txt", "target.txt", false, true, true, 1024 * 1024, null
            ).get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().toLowerCase().contains("not found") 
                || result.getMessage().toLowerCase().contains("source"));
        }

        @Test
        @DisplayName("test_download_file_source_not_exists")
        void testDownloadFileSourceNotExists() throws ExecutionException, InterruptedException, IOException {
            Path downloadDir = Files.createTempDirectory("download_test");
            Path localPath = downloadDir.resolve("downloaded.txt");

            try {
                DownloadFileResult result = fsOp.downloadFile(
                    "nonexistent_source.txt", localPath.toString(), false, true, true, 1024 * 1024, null
                ).get();

                assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            } finally {
                Files.deleteIfExists(downloadDir);
            }
        }
    }

    // ===================== List Operations Tests =====================

    @Nested
    @DisplayName("TestFsListOperations")
    class TestFsListOperations {

        @Test
        @DisplayName("test_list_files_basic")
        void testListFilesBasic() throws ExecutionException, InterruptedException {
            // Create test files
            fsOp.writeFile("file1.txt", "1", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("file2.txt", "2", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("file3.txt", "3", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            ListFilesResult result = fsOp.listFiles(".").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals(3, result.getData().getTotalCount());
            assertEquals(3, result.getData().getListItems().size());
        }

        @Test
        @DisplayName("test_list_files_recursive")
        void testListFilesRecursive() throws ExecutionException, InterruptedException {
            // Create nested structure
            fsOp.writeFile("root.txt", "r", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("subdir/sub.txt", "s", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("subdir/deep/deep.txt", "d", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Non-recursive
            ListFilesResult resultFlat = fsOp.listFiles(".", false, null, SortBy.NAME, false, null, null).get();
            assertEquals(1, resultFlat.getData().getTotalCount());  // Only root.txt

            // Recursive
            ListFilesResult resultRecursive = fsOp.listFiles(".", true, null, SortBy.NAME, false, null, null).get();
            assertEquals(3, resultRecursive.getData().getTotalCount());
        }

        @Test
        @DisplayName("test_list_files_max_depth")
        void testListFilesMaxDepth() throws ExecutionException, InterruptedException {
            fsOp.writeFile("level0.txt", "0", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("dir1/level1.txt", "1", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("dir1/dir2/level2.txt", "2", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // max_depth=1 means depth 0 (root) and depth 1 (direct children)
            // In Java implementation, depth starts from 0, so maxDepth=1 only includes level0.txt
            // Use maxDepth=2 to include both level0 and level1
            ListFilesResult result = fsOp.listFiles(".", true, 2, SortBy.NAME, false, null, null).get();
            assertEquals(2, result.getData().getTotalCount());
        }

        @Test
        @DisplayName("test_list_files_sort_by_name")
        void testListFilesSortByName() throws ExecutionException, InterruptedException {
            fsOp.writeFile("charlie.txt", "c", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("alpha.txt", "a", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("bravo.txt", "b", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            ListFilesResult result = fsOp.listFiles(".", false, null, SortBy.NAME, false, null, null).get();

            List<String> names = result.getData().getListItems().stream()
                .map(FileSystemItem::getName)
                .collect(Collectors.toList());
            assertEquals(Arrays.asList("alpha.txt", "bravo.txt", "charlie.txt"), names);
        }

        @Test
        @DisplayName("test_list_files_sort_descending")
        void testListFilesSortDescending() throws ExecutionException, InterruptedException {
            fsOp.writeFile("a.txt", "a", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("b.txt", "b", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("c.txt", "c", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            ListFilesResult result = fsOp.listFiles(".", false, null, SortBy.NAME, true, null, null).get();

            List<String> names = result.getData().getListItems().stream()
                .map(FileSystemItem::getName)
                .collect(Collectors.toList());
            assertEquals(Arrays.asList("c.txt", "b.txt", "a.txt"), names);
        }

        @Test
        @DisplayName("test_list_files_filter_by_type")
        void testListFilesFilterByType() throws ExecutionException, InterruptedException {
            fsOp.writeFile("doc.txt", "t", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("image.png", "p", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("data.json", "j", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            ListFilesResult result = fsOp.listFiles(".", false, null, SortBy.NAME, false, 
                Arrays.asList(".txt", ".json"), null).get();

            assertEquals(2, result.getData().getTotalCount());
            List<String> names = result.getData().getListItems().stream()
                .map(FileSystemItem::getName)
                .collect(Collectors.toList());
            assertTrue(names.contains("doc.txt"));
            assertTrue(names.contains("data.json"));
            assertFalse(names.contains("image.png"));
        }

        @Test
        @DisplayName("test_list_directories_basic")
        void testListDirectoriesBasic() throws ExecutionException, InterruptedException {
            // Create directories via writing files
            fsOp.writeFile("dir1/file.txt", "1", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("dir2/file.txt", "2", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("root_file.txt", "r", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            ListDirsResult result = fsOp.listDirectories(".").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals(2, result.getData().getTotalCount());
            List<String> names = result.getData().getListItems().stream()
                .map(FileSystemItem::getName)
                .collect(Collectors.toList());
            assertTrue(names.contains("dir1"));
            assertTrue(names.contains("dir2"));
        }

        @Test
        @DisplayName("test_list_empty_directory")
        void testListEmptyDirectory() throws ExecutionException, InterruptedException {
            ListFilesResult result = fsOp.listFiles(".").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals(0, result.getData().getTotalCount());
        }

        @Test
        @DisplayName("test_list_files_path_not_directory")
        void testListFilesPathNotDirectory() throws ExecutionException, InterruptedException {
            String fileName = "regular_file.txt";
            fsOp.writeFile(fileName, "content", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            ListFilesResult result = fsOp.listFiles(fileName, false, null, SortBy.NAME, false, null, null).get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().toLowerCase().contains("not a directory"));
        }
    }

    // ===================== Search Files Tests =====================

    @Nested
    @DisplayName("TestFsSearchFiles")
    class TestFsSearchFiles {

        @Test
        @DisplayName("test_search_files_glob_pattern")
        void testSearchFilesGlobPattern() throws ExecutionException, InterruptedException {
            fsOp.writeFile("config.json", "c", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("data.json", "d", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("readme.txt", "r", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            SearchFilesResult result = fsOp.searchFiles(".", "*.json").get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertEquals(2, result.getData().getTotalMatches());
            List<String> names = result.getData().getMatchingFiles().stream()
                .map(FileSystemItem::getName)
                .collect(Collectors.toList());
            assertTrue(names.contains("config.json"));
            assertTrue(names.contains("data.json"));
        }

        @Test
        @DisplayName("test_search_files_recursive")
        void testSearchFilesRecursive() throws ExecutionException, InterruptedException {
            fsOp.writeFile("root.py", "r", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("src/main.py", "m", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("src/utils/helper.py", "h", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Java PathMatcher: *.py only matches root level, **/*.py matches subdirs
            // Use glob pattern that matches both root and subdirs
            SearchFilesResult rootResult = fsOp.searchFiles(".", "*.py").get();
            SearchFilesResult subResult = fsOp.searchFiles(".", "**/*.py").get();

            // Root level files
            assertEquals(1, rootResult.getData().getTotalMatches());
            // Subdir files
            assertEquals(2, subResult.getData().getTotalMatches());
        }

        @Test
        @DisplayName("test_search_files_with_exclude")
        void testSearchFilesWithExclude() throws ExecutionException, InterruptedException {
            fsOp.writeFile("main.py", "m", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("test_main.py", "t", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();
            fsOp.writeFile("utils.py", "u", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            SearchFilesResult result = fsOp.searchFiles(".", "*.py", Arrays.asList("test_*.py")).get();

            assertEquals(2, result.getData().getTotalMatches());
            List<String> names = result.getData().getMatchingFiles().stream()
                .map(FileSystemItem::getName)
                .collect(Collectors.toList());
            assertFalse(names.contains("test_main.py"));
        }

        @Test
        @DisplayName("test_search_files_path_not_directory")
        void testSearchFilesPathNotDirectory() throws ExecutionException, InterruptedException {
            String fileName = "not_a_dir.txt";
            fsOp.writeFile(fileName, "content", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            SearchFilesResult result = fsOp.searchFiles(fileName, "*.txt", null).get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().toLowerCase().contains("not a directory"));
        }

        @Test
        @DisplayName("test_search_files_result_structure")
        void testSearchFilesResultStructure() throws ExecutionException, InterruptedException {
            fsOp.writeFile("search_target.txt", "target", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            SearchFilesResult result = fsOp.searchFiles(".", "*.txt", Arrays.asList("excluded_*.txt")).get();

            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
            assertNotNull(result.getData().getTotalMatches());
            assertNotNull(result.getData().getMatchingFiles());
            assertNotNull(result.getData().getSearchPath());
            assertNotNull(result.getData().getSearchPattern());

            assertEquals("*.txt", result.getData().getSearchPattern());
            assertEquals(Arrays.asList("excluded_*.txt"), result.getData().getExcludePatterns());
        }
    }

    // ===================== Path Security Tests =====================

    @Nested
    @DisplayName("TestFsPathSecurity")
    class TestFsPathSecurity {

        @Test
        @DisplayName("test_path_traversal_blocked")
        void testPathTraversalBlocked() throws ExecutionException, InterruptedException {
            ReadFileResult result = fsOp.readFile("../outside_workdir.txt").get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
            assertTrue(result.getMessage().toLowerCase().contains("denied") 
                || result.getMessage().toLowerCase().contains("traverses outside")
                || result.getMessage().toLowerCase().contains("access"));
        }

        @Test
        @DisplayName("test_deep_path_traversal_blocked")
        void testDeepPathTraversalBlocked() throws ExecutionException, InterruptedException {
            // Try multiple levels of traversal
            ReadFileResult result = fsOp.readFile("../../../../../../etc/passwd").get();

            assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
        }

        @Test
        @DisplayName("test_path_with_dot_segment")
        void testPathWithDotSegment() throws ExecutionException, InterruptedException {
            // Create a file
            fsOp.writeFile("subdir/file.txt", "test", FileMode.TEXT, false, false, true, "644", "utf-8", null).get();

            // Access using . and valid ..
            ReadFileResult result = fsOp.readFile("./subdir/../subdir/file.txt").get();

            // This should resolve to subdir/file.txt within work_dir
            assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        }
    }

    // ===================== No WorkDir Tests =====================

    @Nested
    @DisplayName("TestFsWithoutWorkDir")
    class TestFsWithoutWorkDir {

        @Test
        @DisplayName("test_read_file_with_absolute_path")
        void testReadFileWithAbsolutePath() throws ExecutionException, InterruptedException, IOException {
            // Create SysOperation without work_dir
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(null)
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_fs_op_no_workdir")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            SysOperation opNoWorkdir = new SysOperation(card);
            BaseFsOperation fsOpNoWorkdir = (BaseFsOperation) opNoWorkdir.fs();

            Path tempFile = Files.createTempFile("test_abs_", ".txt");
            Files.writeString(tempFile, "absolute path content");

            try {
                ReadFileResult result = fsOpNoWorkdir.readFile(tempFile.toString()).get();

                assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
                assertEquals("absolute path content", result.getData().getContent());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("test_write_file_with_absolute_path")
        void testWriteFileWithAbsolutePath() throws ExecutionException, InterruptedException, IOException {
            LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(null)
                .build();
            SysOperationCard card = SysOperationCard.builder()
                .id("test_fs_op_no_workdir")
                .mode(OperationMode.LOCAL)
                .workConfig(config)
                .build();
            SysOperation opNoWorkdir = new SysOperation(card);
            BaseFsOperation fsOpNoWorkdir = (BaseFsOperation) opNoWorkdir.fs();

            Path tempDir = Files.createTempDirectory("test_write");
            Path filePath = tempDir.resolve("test_write.txt");

            try {
                WriteFileResult result = fsOpNoWorkdir.writeFile(
                    filePath.toString(), "written content", FileMode.TEXT, false, false, true, "644", "utf-8", null
                ).get();

                assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());

                // Verify content
                String content = Files.readString(filePath);
                assertEquals("written content", content);
            } finally {
                Files.deleteIfExists(filePath);
                Files.deleteIfExists(tempDir);
            }
        }
    }
}

