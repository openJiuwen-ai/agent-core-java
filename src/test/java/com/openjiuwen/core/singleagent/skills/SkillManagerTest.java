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

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for skill manager registration.
 *
 * <p>Mirrors Python's {@code SkillManager} in
 * {@code openjiuwen/core/single_agent/skills/skill_manager.py}.</p>
 *
 * <p>Also mirrors Python's {@code TestSkillCapability} in
 * {@code tests/system_tests/agent/skill/test_skill_real_system.py}.</p>
 *
 * <p>Also mirrors Python's {@code TestSkillCapability} in
 * {@code tests/unit_tests/agent/skill/test_skill_system_mock.py}.</p>
 */
class SkillManagerTest {
    private static final String SYS_OPERATION_ID = "sys-op";
    private static final String SKILLS_ROOT_OK = "/virtual/skills_ok";
    private static final String SKILLS_ROOT_BAD = "/virtual/skills_bad";
    private static final String GOOD_SKILL_DIR = SKILLS_ROOT_OK + "/good_skill";
    private static final String GOOD_SKILL_MD = GOOD_SKILL_DIR + "/skill.md";
    private static final String BAD_SKILL_DIR = SKILLS_ROOT_BAD + "/bad_skill";
    private static final String BAD_SKILL_MD = BAD_SKILL_DIR + "/skill.md";
    private static final String SINGLE_SKILL_DIR = "/virtual/single_skill";
    private static final String SINGLE_SKILL_MD = SINGLE_SKILL_DIR + "/skill.md";

    private RecordingFsOperation fs;

    @BeforeEach
    void setUp() {
        fs = new RecordingFsOperation();

        fs.addDirectory(SKILLS_ROOT_OK);
        fs.addSubdirectory(SKILLS_ROOT_OK, GOOD_SKILL_DIR);
        fs.addFile(GOOD_SKILL_DIR, GOOD_SKILL_MD, makeSkillMd("UT mock skill description"));

        fs.addDirectory(SKILLS_ROOT_BAD);
        fs.addSubdirectory(SKILLS_ROOT_BAD, BAD_SKILL_DIR);
        fs.addFile(BAD_SKILL_DIR, BAD_SKILL_MD, makeSkillMd(null));

        fs.addDirectory(SINGLE_SKILL_DIR);
        fs.addFile(SINGLE_SKILL_DIR, SINGLE_SKILL_MD, makeSkillMd("SINGLE desc"));
    }

    @Test
    void registerScansParentDirectoryForSkillDirectories() throws Exception {
        SkillManager manager = manager();

        manager.register(Path.of(SKILLS_ROOT_OK));

        assertThat(manager.has("good_skill")).isTrue();
        Skill skill = manager.get("good_skill");
        assertThat(skill).isNotNull();
        assertThat(skill.getDescription()).isEqualTo("UT mock skill description");
        assertThat(skill.getDirectory().getFileName().toString()).isEqualTo("good_skill");
    }

    @Test
    void registerSingleSkillMdFileDirectly() throws Exception {
        SkillManager manager = manager();

        manager.register(Path.of(SINGLE_SKILL_MD));

        assertThat(manager.has("single_skill")).isTrue();
        assertThat(manager.get("single_skill").getDescription()).isEqualTo("SINGLE desc");
    }

    @Test
    void registerSkillDirectoryDirectly() throws Exception {
        SkillManager manager = manager();

        manager.register(Path.of(SINGLE_SKILL_DIR));

        assertThat(manager.has("single_skill")).isTrue();
        assertThat(manager.get("single_skill").getDescription()).isEqualTo("SINGLE desc");
    }

    @Test
    void duplicateRegistrationRequiresOverwrite() throws Exception {
        SkillManager manager = manager();
        manager.register(Path.of(SINGLE_SKILL_MD));

        assertThatThrownBy(() -> manager.register(Path.of(SINGLE_SKILL_MD), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skill already exists: single_skill");

        manager.register(Path.of(SINGLE_SKILL_MD), true);

        assertThat(manager.has("single_skill")).isTrue();
        assertThat(manager.count()).isEqualTo(1);
    }

    @Test
    void registryOperationsMatchPythonSurface() throws Exception {
        SkillManager manager = manager();
        manager.register(Path.of(SINGLE_SKILL_MD));

        assertThat(manager.count()).isEqualTo(1);
        assertThat(Set.copyOf(manager.get_names())).containsExactly("single_skill");
        assertThat(manager.getAll()).singleElement()
                .satisfies(skill -> assertThat(skill.asDict(false))
                        .containsEntry("name", "single_skill")
                        .containsEntry("description", "SINGLE desc")
                        .doesNotContainKey("directory"));

        manager.unregister("single_skill");
        assertThat(manager.has("single_skill")).isFalse();
        assertThat(manager.count()).isZero();

        manager.clear();
        assertThat(manager.count()).isZero();
    }

    @Test
    void missingDescriptionRaisesPythonEquivalentKeyError() {
        SkillManager manager = manager();

        assertThatThrownBy(() -> manager.register(Path.of(SKILLS_ROOT_BAD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description field");
    }

    @Test
    void missingYamlFrontMatterRaisesPythonEquivalentKeyError() {
        fs.content.put(fs.normalize(SINGLE_SKILL_MD), "no front matter");
        SkillManager manager = manager();

        assertThatThrownBy(() -> manager.register(Path.of(SINGLE_SKILL_MD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description field");
    }

    @Test
    void readFileNonZeroRaisesFileNotFound() {
        fs.failRead.add(fs.normalize(SINGLE_SKILL_MD));
        SkillManager manager = manager();

        assertThatThrownBy(() -> manager.register(Path.of(SINGLE_SKILL_MD)))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("read_file failed");
    }

    @Test
    void readFileNoneContentRaisesFileNotFound() {
        fs.content.put(fs.normalize(SINGLE_SKILL_MD), null);
        SkillManager manager = manager();

        assertThatThrownBy(() -> manager.register(Path.of(SINGLE_SKILL_MD)))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("read_file is None");
    }

    private SkillManager manager() {
        return new SkillManager(SYS_OPERATION_ID, ignored -> fs);
    }

    private static String makeSkillMd(String description) {
        if (description == null) {
            return """
                    ---
                    foo: bar
                    ---
                    body
                    """;
        }
        return "---\ndescription: " + description + "\n---\nbody\n";
    }

    private static final class RecordingFsOperation extends BaseFsOperation {
        private final Map<String, List<String>> directories = new LinkedHashMap<>();
        private final Map<String, List<String>> files = new LinkedHashMap<>();
        private final Map<String, Object> content = new LinkedHashMap<>();
        private final List<String> failRead = new ArrayList<>();

        private RecordingFsOperation() {
            super("fs", OperationMode.LOCAL, "recording fs", null);
        }

        private void addDirectory(String path) {
            String normalized = normalize(path);
            directories.computeIfAbsent(normalized, ignored -> new ArrayList<>());
            files.computeIfAbsent(normalized, ignored -> new ArrayList<>());
        }

        private void addSubdirectory(String parent, String subdirectory) {
            String normalizedParent = normalize(parent);
            String normalizedSubdirectory = normalize(subdirectory);
            addDirectory(normalizedParent);
            addDirectory(normalizedSubdirectory);
            directories.get(normalizedParent).add(normalizedSubdirectory);
        }

        private void addFile(String directory, String path, Object fileContent) {
            String normalizedDirectory = normalize(directory);
            String normalizedPath = normalize(path);
            addDirectory(normalizedDirectory);
            files.get(normalizedDirectory).add(normalizedPath);
            content.put(normalizedPath, fileContent);
        }

        private String normalize(String path) {
            return (path == null ? "" : path).replace('\\', '/');
        }

        @Override
        public CompletableFuture<ReadFileResult> readFile(String path, FileMode mode, Integer head, Integer tail,
                                                          BaseFsProtocal.LineRange lineRange, String encoding,
                                                          int chunkSize, Map<String, Object> options) {
            String normalized = normalize(path);
            ReadFileResult result = new ReadFileResult();
            ReadFileData data = new ReadFileData();
            data.setPath(normalized);
            data.setMode("text");
            if (failRead.contains(normalized)) {
                result.setCode(1);
                result.setMessage("read_file failed: " + normalized);
                data.setContent(null);
            } else {
                result.setCode(0);
                result.setMessage("");
                data.setContent(content.get(normalized));
            }
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
