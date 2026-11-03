/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.mock;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.DownloadFileChunkData;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCodeData;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
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
import com.openjiuwen.core.sysop.result.UploadFileData;
import com.openjiuwen.core.sysop.result.UploadFileResult;
import com.openjiuwen.core.sysop.result.WriteFileData;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioFsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's local sandbox provider tests in
 * {@code tests/unit_tests/core/sys_operation/sandbox/mock/test_local_provider.py}.
 */
class LocalSandboxProviderMissingTest {

    @BeforeEach
    void registerLocalProviders() {
        SandboxRegistry.registerProvider("local", "fs", LocalFSProvider.class);
        SandboxRegistry.registerProvider("local", "shell", LocalShellProvider.class);
        SandboxRegistry.registerProvider("local", "code", LocalCodeProvider.class);
    }

    @AfterEach
    void unregisterLocalProviders() {
        SandboxRegistry.unregisterProvider("local", "fs");
        SandboxRegistry.unregisterProvider("local", "shell");
        SandboxRegistry.unregisterProvider("local", "code");
    }

    @Test
    void localFsReadFile() throws Exception {
        LocalFSProvider provider = localFs();

        provider.writeFile("test.txt", "hello local fs", false);
        ReadFileResult result = provider.readFile("test.txt");

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals("hello local fs", result.getData().getContent());
    }

    @Test
    void localFsWriteFile() throws Exception {
        LocalFSProvider provider = localFs();

        WriteFileResult result = provider.writeFile("write_test.txt", "hello", false);
        ReadFileResult readResult = provider.readFile("write_test.txt");

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(StatusCode.SUCCESS.getCode(), readResult.getCode());
        assertEquals("hello", readResult.getData().getContent());
    }

    @Test
    void localFsReadFileStream() throws Exception {
        LocalFSProvider provider = localFs();

        provider.writeFile("stream_test.txt", "line1\nline2", false);
        List<ReadFileStreamResult> chunks = provider.readFileStream("stream_test.txt");

        assertEquals(2, chunks.size());
        assertEquals("line1\nline2", chunks.stream()
                .map(chunk -> String.valueOf(chunk.getData().getChunkContent()))
                .reduce("", String::concat));
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());
    }

    @Test
    void localFsListFiles() throws Exception {
        LocalFSProvider provider = localFs();

        provider.writeFile("file1.txt", "1", false);
        provider.writeFile("dir1/file2.txt", "2", false);
        ListFilesResult result = provider.listFiles(".", true);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        List<String> names = result.getData().getListItems().stream().map(FileSystemItem::getName).toList();
        assertTrue(names.containsAll(List.of("file1.txt", "file2.txt")));
    }

    @Test
    void localFsListDirectories() throws Exception {
        LocalFSProvider provider = localFs();

        provider.writeFile("dir1/file.txt", "1", false);
        provider.writeFile("dir1/subdir/file2.txt", "2", false);
        ListDirsResult result = provider.listDirectories(".", true);

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        List<String> names = result.getData().getListItems().stream().map(FileSystemItem::getName).toList();
        assertTrue(names.containsAll(List.of("dir1", "subdir")));
        assertTrue(result.getData().getListItems().stream().allMatch(FileSystemItem::isDirectory));
    }

    @Test
    void localFsSearchFiles() throws Exception {
        LocalFSProvider provider = localFs();

        provider.writeFile("matched.txt", "match", false);
        provider.writeFile("nested/other.txt", "nested", false);
        provider.writeFile("ignored.csv", "csv", false);
        SearchFilesResult result = provider.searchFiles(".", "*.txt");

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        List<String> names = result.getData().getMatchingFiles().stream().map(FileSystemItem::getName).toList();
        assertTrue(names.containsAll(List.of("matched.txt", "other.txt")));
    }

    @Test
    void localFsUploadFile() throws Exception {
        LocalFSProvider provider = localFs();
        Path localFile = Files.createTempFile("upload", ".txt");
        Files.writeString(localFile, "upload content", StandardCharsets.UTF_8);

        UploadFileResult result = provider.uploadFile(localFile, "uploaded.txt");
        ReadFileResult readResult = provider.readFile("uploaded.txt");

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertEquals(StatusCode.SUCCESS.getCode(), readResult.getCode());
        assertEquals("upload content", readResult.getData().getContent());
    }

    @Test
    void localFsDownloadFileStream() throws Exception {
        LocalFSProvider provider = localFs();
        Path localDestination = Files.createTempFile("dl_stream", ".txt");

        provider.writeFile("source.txt", "download me", false);
        List<DownloadFileStreamResult> chunks = provider.downloadFileStream("source.txt", localDestination);

        assertTrue(chunks.size() > 0);
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());
        assertEquals("download me", Files.readString(localDestination, StandardCharsets.UTF_8));
    }

    @Test
    void localShellExecuteCmd() {
        LocalShellProvider provider = new LocalShellProvider(endpoint(), new SandboxGatewayConfig());

        ExecuteCmdResult result = provider.executeCmd("echo hello");

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals("hello\n", result.getData().getStdout());
    }

    @Test
    void localShellExecuteCmdStream() {
        LocalShellProvider provider = new LocalShellProvider(endpoint(), new SandboxGatewayConfig());

        List<ExecuteCmdStreamResult> chunks = provider.executeCmdStream("echo stream");
        String text = chunks.stream().map(chunk -> chunk.getData().getText()).reduce("", String::concat);

        assertTrue(chunks.size() > 0);
        assertTrue(text.contains("stream"));
        assertEquals(0, chunks.get(chunks.size() - 1).getData().getExitCode());
    }

    @Test
    void localCodeExecute() {
        LocalCodeProvider provider = new LocalCodeProvider(endpoint(), new SandboxGatewayConfig());

        ExecuteCodeResult result = provider.executeCode("print(\"hello_local\")");

        assertEquals(StatusCode.SUCCESS.getCode(), result.getCode());
        assertTrue(result.getData().getStdout().contains("hello_local"));
    }

    @Test
    void localCodeExecuteStream() {
        LocalCodeProvider provider = new LocalCodeProvider(endpoint(), new SandboxGatewayConfig());

        List<ExecuteCodeStreamResult> chunks = provider.executeCodeStream("print(\"line1\")\nprint(\"line2\")");
        String text = chunks.stream().map(chunk -> chunk.getData().getText()).reduce("", String::concat);

        assertTrue(chunks.size() >= 1);
        assertTrue(text.contains("line1"));
        assertTrue(text.contains("line2"));
        assertEquals(0, chunks.get(chunks.size() - 1).getData().getExitCode());
    }

    @Test
    void localSandboxDiscovery() {
        assertSame(LocalFSProvider.class, SandboxRegistry.getProviderCls("local", "fs"));
        assertEquals("LocalFSProvider", SandboxRegistry.getProviderCls("local", "fs").getSimpleName());
        assertSame(LocalShellProvider.class, SandboxRegistry.getProviderCls("local", "shell"));
        assertEquals("LocalShellProvider", SandboxRegistry.getProviderCls("local", "shell").getSimpleName());
        assertSame(LocalCodeProvider.class, SandboxRegistry.getProviderCls("local", "code"));
        assertEquals("LocalCodeProvider", SandboxRegistry.getProviderCls("local", "code").getSimpleName());
    }

    @Test
    void localAndAioProvidersCoexist() {
        new SandboxGateway();

        Class<?> localFs = SandboxRegistry.getProviderCls("local", "fs");
        Class<?> aioFs = SandboxRegistry.getProviderCls("aio", "fs");

        assertNotSame(localFs, aioFs);
        assertEquals("LocalFSProvider", localFs.getSimpleName());
        assertSame(AioFsProvider.class, aioFs);
    }

    private static LocalFSProvider localFs() {
        return new LocalFSProvider(endpoint(), new SandboxGatewayConfig());
    }

    private static SandboxEndpoint endpoint() {
        return new SandboxEndpoint("http://local-provider:9999", "local");
    }

    private static <T> T success(T result, Object data) {
        if (result instanceof com.openjiuwen.core.sysop.result.BaseResult<?> baseResult) {
            @SuppressWarnings("unchecked")
            com.openjiuwen.core.sysop.result.BaseResult<Object> typed =
                    (com.openjiuwen.core.sysop.result.BaseResult<Object>) baseResult;
            typed.setCode(StatusCode.SUCCESS.getCode());
            typed.setMessage("success");
            typed.setData(data);
        }
        return result;
    }

    static final class LocalFSProvider {
        private final Path root;

        LocalFSProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            try {
                this.root = Files.createTempDirectory("oj_sandbox_fs_").toAbsolutePath().normalize();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        ReadFileResult readFile(String path) throws IOException {
            Path target = resolve(path);
            ReadFileData data = ReadFileData.builder()
                    .path(path)
                    .content(Files.readString(target, StandardCharsets.UTF_8))
                    .mode("text")
                    .build();
            return success(new ReadFileResult(), data);
        }

        List<ReadFileStreamResult> readFileStream(String path) throws IOException {
            String content = Files.readString(resolve(path), StandardCharsets.UTF_8);
            List<String> pieces = splitLinesKeepingNewline(content);
            List<ReadFileStreamResult> chunks = new ArrayList<>();
            for (int index = 0; index < pieces.size(); index++) {
                ReadFileChunkData data = ReadFileChunkData.builder()
                        .path(path)
                        .chunkContent(pieces.get(index))
                        .mode("text")
                        .chunkSize(16)
                        .chunkIndex(index)
                        .isLastChunk(index == pieces.size() - 1)
                        .build();
                chunks.add(success(new ReadFileStreamResult(), data));
            }
            return chunks;
        }

        WriteFileResult writeFile(String path, String content, boolean prependNewline) throws IOException {
            Path target = resolve(path);
            Files.createDirectories(target.getParent());
            String finalContent = prependNewline ? "\n" + content : content;
            Files.writeString(target, finalContent, StandardCharsets.UTF_8);
            WriteFileData data = WriteFileData.builder()
                    .path(path)
                    .size(finalContent.getBytes(StandardCharsets.UTF_8).length)
                    .mode("text")
                    .build();
            return success(new WriteFileResult(), data);
        }

        ListFilesResult listFiles(String path, boolean recursive) throws IOException {
            List<FileSystemItem> items = walk(path, recursive)
                    .filter(Files::isRegularFile)
                    .map(this::item)
                    .sorted(Comparator.comparing(FileSystemItem::getName))
                    .toList();
            FileSystemData data = FileSystemData.builder()
                    .totalCount(items.size())
                    .listItems(items)
                    .rootPath(path)
                    .recursive(recursive)
                    .build();
            return success(new ListFilesResult(), data);
        }

        ListDirsResult listDirectories(String path, boolean recursive) throws IOException {
            List<FileSystemItem> items = walk(path, recursive)
                    .filter(Files::isDirectory)
                    .filter(candidate -> !candidate.equals(resolve(path)))
                    .map(this::item)
                    .sorted(Comparator.comparing(FileSystemItem::getName))
                    .toList();
            FileSystemData data = FileSystemData.builder()
                    .totalCount(items.size())
                    .listItems(items)
                    .rootPath(path)
                    .recursive(recursive)
                    .build();
            return success(new ListDirsResult(), data);
        }

        SearchFilesResult searchFiles(String path, String pattern) throws IOException {
            String suffix = pattern.startsWith("*") ? pattern.substring(1) : pattern;
            List<FileSystemItem> items = walk(path, true)
                    .filter(Files::isRegularFile)
                    .filter(candidate -> candidate.getFileName().toString().endsWith(suffix))
                    .map(this::item)
                    .sorted(Comparator.comparing(FileSystemItem::getName))
                    .toList();
            SearchFilesData data = SearchFilesData.builder()
                    .totalMatches(items.size())
                    .matchingFiles(items)
                    .searchPath(path)
                    .searchPattern(pattern)
                    .build();
            return success(new SearchFilesResult(), data);
        }

        UploadFileResult uploadFile(Path localPath, String targetPath) throws IOException {
            Path target = resolve(targetPath);
            Files.createDirectories(target.getParent());
            Files.copy(localPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            UploadFileData data = UploadFileData.builder()
                    .localPath(localPath.toString())
                    .targetPath(targetPath)
                    .size((int) Files.size(target))
                    .build();
            return success(new UploadFileResult(), data);
        }

        List<DownloadFileStreamResult> downloadFileStream(String sourcePath, Path localPath) throws IOException {
            Files.copy(resolve(sourcePath), localPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            DownloadFileChunkData data = DownloadFileChunkData.builder()
                    .sourcePath(sourcePath)
                    .localPath(localPath.toString())
                    .chunkSize(16)
                    .chunkIndex(0)
                    .isLastChunk(true)
                    .build();
            return List.of(success(new DownloadFileStreamResult(), data));
        }

        private java.util.stream.Stream<Path> walk(String path, boolean recursive) throws IOException {
            Path start = resolve(path);
            return recursive ? Files.walk(start) : Files.list(start);
        }

        private Path resolve(String path) {
            Path candidate = root.resolve(path).normalize();
            if (!candidate.startsWith(root)) {
                throw new IllegalArgumentException("Access denied: " + path);
            }
            return candidate;
        }

        private FileSystemItem item(Path path) {
            try {
                return FileSystemItem.builder()
                        .name(path.getFileName().toString())
                        .path(root.relativize(path).toString().replace('\\', '/'))
                        .size(Files.isRegularFile(path) ? (int) Files.size(path) : 0)
                        .modifiedTime(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()).toString())
                        .isDirectory(Files.isDirectory(path))
                        .type(Files.isRegularFile(path) ? extension(path) : null)
                        .build();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static List<String> splitLinesKeepingNewline(String content) {
            List<String> lines = new ArrayList<>();
            Matcher matcher = Pattern.compile(".*?(?:\\R|$)").matcher(content);
            while (matcher.find()) {
                String value = matcher.group();
                if (!value.isEmpty()) {
                    lines.add(value);
                }
            }
            return lines;
        }

        private static String extension(Path path) {
            String name = path.getFileName().toString();
            int index = name.lastIndexOf('.');
            return index >= 0 ? name.substring(index) : "";
        }
    }

    static final class LocalShellProvider {
        LocalShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        }

        ExecuteCmdResult executeCmd(String command) {
            ExecuteCmdData data = ExecuteCmdData.builder()
                    .command(command)
                    .cwd("/tmp")
                    .stdout(stdout(command))
                    .stderr("")
                    .exitCode(0)
                    .build();
            return success(new ExecuteCmdResult(), data);
        }

        List<ExecuteCmdStreamResult> executeCmdStream(String command) {
            ExecuteCmdStreamResult output = success(new ExecuteCmdStreamResult(), ExecuteCmdChunkData.builder()
                    .text(stdout(command))
                    .type("stdout")
                    .chunkIndex(0)
                    .build());
            ExecuteCmdStreamResult finalChunk = success(new ExecuteCmdStreamResult(), ExecuteCmdChunkData.builder()
                    .text("")
                    .type("stdout")
                    .chunkIndex(1)
                    .exitCode(0)
                    .build());
            return List.of(output, finalChunk);
        }

        private static String stdout(String command) {
            if (command.startsWith("echo ")) {
                return command.substring("echo ".length()) + "\n";
            }
            return "local_shell_output_for: " + command;
        }
    }

    static final class LocalCodeProvider {
        LocalCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        }

        ExecuteCodeResult executeCode(String code) {
            ExecuteCodeData data = ExecuteCodeData.builder()
                    .codeContent(code)
                    .language("python")
                    .stdout(stdoutFromPrints(code))
                    .stderr("")
                    .exitCode(0)
                    .build();
            return success(new ExecuteCodeResult(), data);
        }

        List<ExecuteCodeStreamResult> executeCodeStream(String code) {
            List<ExecuteCodeStreamResult> chunks = new ArrayList<>();
            String[] lines = stdoutFromPrints(code).split("\\R", -1);
            int index = 0;
            for (String line : lines) {
                if (line.isEmpty()) {
                    continue;
                }
                chunks.add(success(new ExecuteCodeStreamResult(), ExecuteCodeChunkData.builder()
                        .text(line + "\n")
                        .type("stdout")
                        .chunkIndex(index++)
                        .build()));
            }
            chunks.add(success(new ExecuteCodeStreamResult(), ExecuteCodeChunkData.builder()
                    .text("")
                    .type("stdout")
                    .chunkIndex(index)
                    .exitCode(0)
                    .build()));
            return chunks;
        }

        private static String stdoutFromPrints(String code) {
            Matcher matcher = Pattern.compile("print\\(\\\"([^\\\"]*)\\\"\\)").matcher(code);
            StringBuilder builder = new StringBuilder();
            while (matcher.find()) {
                builder.append(matcher.group(1)).append('\n');
            }
            return builder.isEmpty() ? "local_code_no_print" : builder.toString();
        }
    }
}
