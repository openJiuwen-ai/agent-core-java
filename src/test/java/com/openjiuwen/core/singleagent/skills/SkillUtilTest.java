/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.sys_operation.BaseFsOperation;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sys_operation.result.DownloadFileResult;
import com.openjiuwen.core.sys_operation.result.DownloadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.FileSystemData;
import com.openjiuwen.core.sys_operation.result.FileSystemItem;
import com.openjiuwen.core.sys_operation.result.ListDirsResult;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileData;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.core.sys_operation.result.ReadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.result.UploadFileResult;
import com.openjiuwen.core.sys_operation.result.UploadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.WriteFileResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for high-level skill utility behavior.
 *
 * <p>Mirrors Python's {@code SkillUtil} in
 * {@code openjiuwen/core/single_agent/skills/skill_util.py}.</p>
 *
 * <p>Also mirrors Python's {@code TestSkillCapability} in
 * {@code tests/system_tests/agent/skill/test_skill_real_system.py}.</p>
 *
 * <p>Also mirrors Python's {@code TestSkillCapability} in
 * {@code tests/unit_tests/agent/skill/test_skill_system_mock.py}.</p>
 */
class SkillUtilTest {
    private static final String SYS_OPERATION_ID = "sys-op";
    private static final String SINGLE_SKILL_DIR = "/virtual/single_skill";
    private static final String SINGLE_SKILL_MD = SINGLE_SKILL_DIR + "/skill.md";

    private RecordingFsOperation fs;

    @BeforeEach
    void setUp() {
        fs = new RecordingFsOperation();
        fs.addDirectory(SINGLE_SKILL_DIR);
        fs.addFile(SINGLE_SKILL_DIR, SINGLE_SKILL_MD,
                "---\ndescription: SINGLE desc\n---\nbody\n");
    }

    @Test
    void registerSkillsRegistersThroughSkillManagerAndReturnsTrue() throws Exception {
        SkillUtil util = skillUtil();

        boolean registered = util.register_skills(List.of(SINGLE_SKILL_MD), new Object(), "session");

        assertThat(registered).isTrue();
        assertThat(util.has_skill()).isTrue();
        assertThat(util.get_skill_manager().get("single_skill").getDescription()).isEqualTo("SINGLE desc");
    }

    @Test
    void getSkillPromptIncludesRegisteredSkillInformation() throws Exception {
        SkillUtil util = skillUtil();
        util.registerSkills(SINGLE_SKILL_MD, null, null);

        String prompt = util.get_skill_prompt();

        assertThat(prompt)
                .contains("You are an agent equipped with various skills to solve problems.")
                .contains("using read_file and follow its workflow.")
                .contains("0.Skill name: single_skill")
                .contains("Skill description: SINGLE desc")
                .contains("Skill directory:");
    }

    @Test
    void setSysOperationIdUpdatesSkillManagerAndRemoteSkillUtil() {
        SkillUtil util = skillUtil();

        util.set_sys_operation_id("new-sys-op");

        assertThat(util.getSkillManager().getSysOperationId()).isEqualTo("new-sys-op");
        assertThat(util.getRemoteSkillUtil().getSysOperationId()).isEqualTo("new-sys-op");
    }

    private SkillUtil skillUtil() {
        SkillManager manager = new SkillManager(SYS_OPERATION_ID, ignored -> fs);
        RemoteSkillUtil remote = new RemoteSkillUtil(
                SYS_OPERATION_ID,
                "https://github.test",
                (url, headers) -> {
                    throw new AssertionError("remote GitHub call is not part of SkillUtil focused tests");
                },
                ignored -> fs
        );
        return new SkillUtil(manager, remote);
    }

    private static final class RecordingFsOperation extends BaseFsOperation {
        private final Map<String, List<String>> directories = new LinkedHashMap<>();
        private final Map<String, List<String>> files = new LinkedHashMap<>();
        private final Map<String, Object> content = new LinkedHashMap<>();

        private RecordingFsOperation() {
            super("fs", OperationMode.LOCAL, "recording fs", null);
        }

        private void addDirectory(String path) {
            String normalized = normalize(path);
            directories.computeIfAbsent(normalized, ignored -> new ArrayList<>());
            files.computeIfAbsent(normalized, ignored -> new ArrayList<>());
        }

        private void addFile(String directory, String path, Object fileContent) {
            String normalizedDirectory = normalize(directory);
            String normalizedPath = normalize(path);
            addDirectory(normalizedDirectory);
            files.get(normalizedDirectory).add(normalizedPath);
            content.put(normalizedPath, fileContent);
        }

        private static String normalize(String path) {
            return (path == null ? "" : path).replace('\\', '/');
        }

        @Override
        public CompletableFuture<ReadFileResult> readFile(String path, FileMode mode, Integer head, Integer tail,
                                                          BaseFsProtocal.LineRange lineRange, String encoding,
                                                          int chunkSize, Map<String, Object> options) {
            String normalized = normalize(path);
            ReadFileData data = new ReadFileData();
            data.setPath(normalized);
            data.setMode("text");
            data.setContent(content.get(normalized));
            ReadFileResult result = new ReadFileResult();
            result.setCode(0);
            result.setMessage("");
            result.setData(data);
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<ListFilesResult> listFiles(String path, boolean recursive, Integer maxDepth,
                                                            SortBy sortBy, boolean sortDescending,
                                                            List<String> fileTypes, Map<String, Object> options) {
            String normalized = normalize(path);
            ListFilesResult result = new ListFilesResult();
            result.setCode(0);
            result.setData(fileSystemData(normalized, files.getOrDefault(normalized, List.of()), false));
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<ListDirsResult> listDirectories(String path, boolean recursive, Integer maxDepth,
                                                                 SortBy sortBy, boolean sortDescending,
                                                                 Map<String, Object> options) {
            String normalized = normalize(path);
            ListDirsResult result = new ListDirsResult();
            if (content.containsKey(normalized)) {
                result.setCode(1);
                result.setMessage("not a directory: " + normalized);
                result.setData(fileSystemData(normalized, List.of(), true));
            } else {
                result.setCode(0);
                result.setMessage("");
                result.setData(fileSystemData(normalized, directories.getOrDefault(normalized, List.of()), true));
            }
            return CompletableFuture.completedFuture(result);
        }

        private static FileSystemData fileSystemData(String root, List<String> paths, boolean directories) {
            FileSystemData data = new FileSystemData();
            List<FileSystemItem> items = new ArrayList<>();
            for (String path : paths) {
                FileSystemItem item = new FileSystemItem();
                item.setName(Path.of(path).getFileName().toString());
                item.setPath(path);
                item.setDirectory(directories);
                item.setType(directories ? "directory" : "file");
                items.add(item);
            }
            data.setRootPath(root);
            data.setListItems(items);
            data.setTotalCount(items.size());
            data.setRecursive(false);
            return data;
        }

        @Override
        public Flow.Publisher<ReadFileStreamResult> readFileStream(String path, FileMode mode, Integer head,
                                                                   Integer tail,
                                                                   BaseFsProtocal.LineRange lineRange,
                                                                   String encoding,
                                                                   int chunkSize,
                                                                   Map<String, Object> options) {
            return unsupportedPublisher();
        }

        @Override
        public CompletableFuture<WriteFileResult> writeFile(String path, String content, FileMode mode,
                                                            boolean prependNewline, boolean appendNewline,
                                                            boolean append, boolean createIfNotExist,
                                                            String permissions, String encoding,
                                                            Map<String, Object> options) {
            return unsupportedFuture();
        }

        @Override
        public CompletableFuture<WriteFileResult> writeFile(String path, byte[] content, FileMode mode,
                                                            boolean prependNewline, boolean appendNewline,
                                                            boolean append, boolean createIfNotExist,
                                                            String permissions, String encoding,
                                                            Map<String, Object> options) {
            return unsupportedFuture();
        }

        @Override
        public CompletableFuture<UploadFileResult> uploadFile(String localPath, String targetPath, boolean overwrite,
                                                              boolean createParentDirs, boolean preservePermissions,
                                                              int chunkSize, Map<String, Object> options) {
            return unsupportedFuture();
        }

        @Override
        public Flow.Publisher<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath,
                                                                       boolean overwrite, boolean createParentDirs,
                                                                       boolean preservePermissions, int chunkSize,
                                                                       Map<String, Object> options) {
            return unsupportedPublisher();
        }

        @Override
        public CompletableFuture<DownloadFileResult> downloadFile(String sourcePath, String localPath,
                                                                  boolean overwrite, boolean createParentDirs,
                                                                  boolean preservePermissions, int chunkSize,
                                                                  Map<String, Object> options) {
            return unsupportedFuture();
        }

        @Override
        public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
                                                                           boolean overwrite,
                                                                           boolean createParentDirs,
                                                                           boolean preservePermissions,
                                                                           int chunkSize,
                                                                           Map<String, Object> options) {
            return unsupportedPublisher();
        }

        @Override
        public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern,
                                                                List<String> excludePatterns) {
            return unsupportedFuture();
        }

        private static <T> CompletableFuture<T> unsupportedFuture() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        private static <T> Flow.Publisher<T> unsupportedPublisher() {
            return subscriber -> subscriber.onError(new UnsupportedOperationException());
        }
    }
}
