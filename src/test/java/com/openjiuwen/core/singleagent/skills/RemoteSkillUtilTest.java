/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for GitHub remote skill utility behavior.
 *
 * <p>Mirrors Python's {@code RemoteSkillUtil} in
 * {@code openjiuwen/core/single_agent/skills/remote_skill_util.py}.</p>
 */
class RemoteSkillUtilTest {

    @TempDir
    Path tempDir;

    private static final String API = "https://github.test";
    private static final String TREE_URL = API + "/repos/owner/repo/git/trees/HEAD?recursive=492";
    private static final String SKILL_MD = "skills/example-skill/SKILL.md";
    private static final String REFERENCE = "skills/example-skill/references/example-reference.md";

    @Test
    void searchGithubForSkillsReturnsFilesAndSkillPaths() {
        StubTransport transport = new StubTransport()
                .respond(TREE_URL, 200, treePayload());
        RemoteSkillUtil util = new RemoteSkillUtil(
                "sys-op",
                API,
                transport,
                ignored -> null
        );

        RemoteSkillUtil.SearchResult result = util.searchGitHubForSkills(githubTree(), "token");

        assertThat(result.files())
                .extracting(RemoteSkillUtil.SkillFile::path)
                .containsExactly(SKILL_MD, REFERENCE);
        assertThat(result.files())
                .extracting(RemoteSkillUtil.SkillFile::relativePath)
                .containsExactly(
                        Path.of("example-skill").resolve("SKILL.md"),
                        Path.of("example-skill").resolve("references").resolve("example-reference.md")
                );
        assertThat(result.skillPaths()).containsExactly(Path.of("example-skill"));
        assertThat(transport.requests()).singleElement()
                .satisfies(request -> assertThat(request.headers()).containsEntry("Authorization", "Bearer token"));
    }

    @Test
    void downloadFileFromGithubReturnsRawBytes() {
        byte[] body = new byte[]{0, 1, 2, 3};
        StubTransport transport = new StubTransport()
                .respond(API + "/repos/owner/repo/contents/" + REFERENCE + "?ref=HEAD", 200, body);
        RemoteSkillUtil util = new RemoteSkillUtil(
                "sys-op",
                API,
                transport,
                ignored -> null
        );

        byte[] data = util.downloadFileFromGithub(githubTree(), REFERENCE, null);

        assertThat(data).containsExactly(body);
        assertThat(transport.requests()).singleElement()
                .satisfies(request -> assertThat(request.headers())
                        .containsEntry("Accept", "application/vnd.github.raw"));
    }

    @Test
    void uploadSkillFromGithubWritesBytesViaSysOperationFs() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        RecordingFsOperation fs = new RecordingFsOperation();
        StubTransport transport = new StubTransport()
                .respond(TREE_URL, 200, treePayload())
                .respond(API + "/repos/owner/repo/contents/" + SKILL_MD + "?ref=HEAD", 200, "skill")
                .respond(API + "/repos/owner/repo/contents/" + REFERENCE + "?ref=HEAD", 200, "reference");
        RemoteSkillUtil util = new RemoteSkillUtil(
                "sys-op",
                API,
                transport,
                ignored -> fs
        );

        Object result = util.upload_skill_from_github(githubTree(), workspace.toString(), "token");

        assertThat(result).isInstanceOf(List.class);
        assertThat(result).isEqualTo(List.of(Path.of("example-skill")));
        Path skillMd = workspace.resolve("example-skill").resolve("SKILL.md");
        Path referenceMd = workspace.resolve("example-skill").resolve("references").resolve("example-reference.md");
        assertThat(fs.writes())
                .extracting(WriteCall::path)
                .containsExactly(skillMd.toString(), referenceMd.toString());
        assertThat(fs.writes())
                .extracting(WriteCall::mode)
                .containsOnly(BaseFsOperation.FileMode.BYTES);
        assertThat(new String(fs.writes().get(0).content(), StandardCharsets.UTF_8)).isEqualTo("skill");
        assertThat(new String(fs.writes().get(1).content(), StandardCharsets.UTF_8)).isEqualTo("reference");
    }

    @Test
    void uploadSkillFromGithubReturnsPythonUnavailableMessageWhenFsMissing() {
        StubTransport transport = new StubTransport()
                .respond(TREE_URL, 200, treePayload())
                .respond(API + "/repos/owner/repo/contents/" + SKILL_MD + "?ref=HEAD", 200, "skill");
        RemoteSkillUtil util = new RemoteSkillUtil(
                "missing-sys-op",
                API,
                transport,
                ignored -> null
        );

        Object result = util.upload_skill_from_github(githubTree(), "workspace", null);

        assertThat(result).isEqualTo("sys_operation is not available");
    }

    private static GitHubTree githubTree() {
        return new GitHubTree("owner", "repo");
    }

    private static String treePayload() {
        return """
                {
                  "truncated": false,
                  "tree": [
                    {"path": "README.md", "type": "blob"},
                    {"path": "SKILL.md", "type": "blob"},
                    {"path": "skills/example-skill/SKILL.md", "type": "blob"},
                    {"path": "skills/example-skill/references/example-reference.md", "type": "blob"},
                    {"path": "skills/other.txt", "type": "blob"}
                  ]
                }
                """;
    }

    private record Request(String url, Map<String, String> headers) {
    }

    private static final class StubTransport implements RemoteSkillUtil.HttpTransport {
        private final Map<String, RemoteSkillUtil.HttpResult> responses = new LinkedHashMap<>();
        private final List<Request> requests = new ArrayList<>();

        private StubTransport respond(String url, int statusCode, String body) {
            responses.put(url, new RemoteSkillUtil.HttpResult(statusCode, body));
            return this;
        }

        private StubTransport respond(String url, int statusCode, byte[] body) {
            responses.put(url, new RemoteSkillUtil.HttpResult(statusCode, body));
            return this;
        }

        private List<Request> requests() {
            return requests;
        }

        @Override
        public RemoteSkillUtil.HttpResult get(String url, Map<String, String> headers) {
            requests.add(new Request(url, Map.copyOf(headers)));
            RemoteSkillUtil.HttpResult response = responses.get(url);
            if (response == null) {
                throw new AssertionError("Unexpected request: " + url);
            }
            return response;
        }
    }

    private record WriteCall(String path, byte[] content, BaseFsOperation.FileMode mode) {
    }

    private static final class RecordingFsOperation extends BaseFsOperation {
        private final List<WriteCall> writes = new ArrayList<>();

        private RecordingFsOperation() {
            super("fs", OperationMode.LOCAL, "recording fs", null);
        }

        private List<WriteCall> writes() {
            return writes;
        }

        @Override
        public CompletableFuture<ReadFileResult> readFile(String path, FileMode mode, Integer head, Integer tail,
                                                          BaseFsProtocal.LineRange lineRange, String encoding,
                                                          int chunkSize, Map<String, Object> options) {
            return unsupportedFuture();
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
            writes.add(new WriteCall(path, content.clone(), mode));
            return CompletableFuture.completedFuture(new WriteFileResult());
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
        public CompletableFuture<ListFilesResult> listFiles(String path, boolean recursive, Integer maxDepth,
                                                            SortBy sortBy, boolean sortDescending,
                                                            List<String> fileTypes, Map<String, Object> options) {
            return unsupportedFuture();
        }

        @Override
        public CompletableFuture<ListDirsResult> listDirectories(String path, boolean recursive, Integer maxDepth,
                                                                 SortBy sortBy, boolean sortDescending,
                                                                 Map<String, Object> options) {
            return unsupportedFuture();
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
