/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sys_operation.BaseFsOperation;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Utilities for discovering and downloading remote GitHub skills.
 *
 * <p>Mirrors Python's {@code RemoteSkillUtil} in
 * {@code openjiuwen/core/single_agent/skills/remote_skill_util.py}.</p>
 */
public class RemoteSkillUtil {
    public static final String GITHUB_API = "https://api.github.com";
    public static final Path SKILLS_DIR = Path.of("skills");
    public static final String SKILL_FILE_NAME = "SKILL.md";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ACCEPT_GITHUB_JSON = "application/vnd.github+json";
    private static final String ACCEPT_GITHUB_RAW = "application/vnd.github.raw";

    private String sysOperationId;
    private final String githubApi;
    private final HttpTransport transport;
    private final Function<String, BaseFsOperation> fsResolver;

    public RemoteSkillUtil(String sysOperationId) {
        this(sysOperationId, HttpClient.newHttpClient());
    }

    public RemoteSkillUtil(String sysOperationId, HttpClient httpClient) {
        this(sysOperationId, GITHUB_API, new JavaHttpTransport(httpClient), RemoteSkillUtil::resolveFsOperation);
    }

    RemoteSkillUtil(String sysOperationId,
                    String githubApi,
                    HttpTransport transport,
                    Function<String, BaseFsOperation> fsResolver) {
        this.sysOperationId = sysOperationId;
        this.githubApi = normalizeEndpoint(githubApi);
        this.transport = transport == null
                ? new JavaHttpTransport(HttpClient.newHttpClient())
                : transport;
        this.fsResolver = fsResolver == null ? RemoteSkillUtil::resolveFsOperation : fsResolver;
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public void set_sys_operation_id(String sysOperationId) {
        setSysOperationId(sysOperationId);
    }

    public List<GitHubBlob> listGithubFiles(GitHubTree tree, String token) {
        return listGithubFilesResult(tree, token).files();
    }

    public List<GitHubBlob> listGitHubFiles(GitHubTree tree, String token) {
        return listGithubFiles(tree, token);
    }

    public SearchResult searchGithubForSkills(GitHubTree tree, String token) {
        return searchGitHubForSkills(tree, token);
    }

    public SearchResult searchGitHubForSkills(GitHubTree tree, String token) {
        ListedFiles listedFiles = listGithubFilesResult(tree, token);
        if (listedFiles.truncated()) {
            Loggers.AGENT.warning("Warning: file results truncated. Results can be incomplete");
        }

        List<SkillFile> fileList = new ArrayList<>();
        List<Path> skillPaths = new ArrayList<>();
        List<GitHubBlob> files = listedFiles.files();

        for (int i = 0; i < files.size(); i++) {
            Path filePath = Path.of(files.get(i).path());
            if (filePath.getNameCount() == 1) {
                continue;
            }
            Path fileName = filePath.getFileName();
            if (fileName == null || !SKILL_FILE_NAME.equals(fileName.toString())) {
                continue;
            }

            Path parentDirectory = filePath.getParent();
            if (parentDirectory == null || parentDirectory.getFileName() == null) {
                continue;
            }
            Path baseSkillPath = Path.of(parentDirectory.getFileName().toString());
            addSkillFile(fileList, files.get(i), baseSkillPath, parentDirectory);
            skillPaths.add(baseSkillPath);

            for (int j = i - 1; j >= 0; j--) {
                Path siblingPath = Path.of(files.get(j).path());
                if (!siblingPath.startsWith(parentDirectory)) {
                    break;
                }
                addSkillFile(fileList, files.get(j), baseSkillPath, parentDirectory);
            }

            for (int j = i + 1; j < files.size(); j++) {
                Path siblingPath = Path.of(files.get(j).path());
                if (!siblingPath.startsWith(parentDirectory)) {
                    break;
                }
                addSkillFile(fileList, files.get(j), baseSkillPath, parentDirectory);
            }
        }

        return new SearchResult(fileList, skillPaths);
    }

    public SearchResult search_github_for_skills(GitHubTree tree, String token) {
        return searchGitHubForSkills(tree, token);
    }

    public byte[] downloadFileFromGithub(GitHubTree tree, String filePath, String token) {
        return downloadFileFromGithub(githubApi, transport, tree, filePath, token);
    }

    public byte[] download_file_from_github(GitHubTree tree, String filePath, String token) {
        return downloadFileFromGithub(tree, filePath, token);
    }

    public static byte[] downloadFileFromGitHub(GitHubTree tree, String filePath, String token) {
        return downloadFileFromGithub(GITHUB_API, new JavaHttpTransport(HttpClient.newHttpClient()), tree, filePath,
                token);
    }

    /**
     * Java-friendly wrapper used by existing callers. The snake_case method keeps Python's dynamic
     * string-or-list return behavior for direct parity checks.
     */
    public List<Path> uploadSkillFromGithub(GitHubTree tree, String skillsDir, String token) {
        Object result = uploadSkillFromGithubResult(tree, skillsDir, token);
        if (result instanceof List<?> values) {
            List<Path> paths = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Path path) {
                    paths.add(path);
                } else if (value != null) {
                    paths.add(Path.of(String.valueOf(value)));
                }
            }
            return paths;
        }
        return List.of();
    }

    public List<Path> uploadSkillFromGitHub(GitHubTree tree, String skillsDir, String token) {
        return uploadSkillFromGithub(tree, skillsDir, token);
    }

    public Object upload_skill_from_github(GitHubTree tree, String skillsDir, String token) {
        return uploadSkillFromGithubResult(tree, skillsDir, token);
    }

    private Object uploadSkillFromGithubResult(GitHubTree tree, String skillsDir, String token) {
        SearchResult result = searchGitHubForSkills(tree, token);
        List<Path> skillPaths = result.skillPaths();
        for (SkillFile file : result.files()) {
            byte[] data = downloadFileFromGithub(tree, file.path(), token);
            Loggers.AGENT.info("Uploading file to " + file.relativePath());

            BaseFsOperation fs = getFsOperation();
            if (fs == null) {
                return "sys_operation is not available";
            }

            Path fullPath = resolveSkillPath(skillsDir, file.relativePath());
            try {
                fs.writeFile(
                        fullPath.toString(),
                        data,
                        BaseFsOperation.FileMode.BYTES,
                        true,
                        false,
                        false,
                        true,
                        "644",
                        StandardCharsets.UTF_8.name(),
                        null
                ).toCompletableFuture().join();
            } catch (CompletionException error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                throw new GitHubError("failed to write " + fullPath, cause);
            }
        }
        return skillPaths;
    }

    private BaseFsOperation getFsOperation() {
        if (sysOperationId == null || sysOperationId.isBlank()) {
            return null;
        }
        return fsResolver.apply(sysOperationId);
    }

    private ListedFiles listGithubFilesResult(GitHubTree tree, String token) {
        GitHubTree searchTree = tree.cloneTree();
        Path directory = stripRoot(searchTree.getDirectory());
        searchTree.setDirectory(directory);
        return recursivelyListGithubFiles(searchTree, Path.of(""), token);
    }

    private ListedFiles recursivelyListGithubFiles(GitHubTree tree, Path currentDirectory, String token) {
        String url = githubApi + "/repos/" + encodePathPart(tree.getRepoOwner()) + "/"
                + encodePathPart(tree.getRepoName()) + "/git/trees/" + encodePathPart(tree.getTreeRef());
        Path relativeDirectory = tree.getDirectory() == null ? Path.of("") : tree.getDirectory();

        if (isEmptyPath(relativeDirectory)) {
            JsonNode data = readGitHubJson(url, token, Map.of("recursive", "492"));
            List<GitHubBlob> files = new ArrayList<>();
            for (JsonNode item : data.path("tree")) {
                if (!"blob".equals(item.path("type").asText())) {
                    continue;
                }
                Path resolved = currentDirectory.resolve(item.path("path").asText()).normalize();
                files.add(new GitHubBlob(toGithubPath(resolved)));
            }
            return new ListedFiles(files, data.path("truncated").asBoolean(false));
        }

        JsonNode data = readGitHubJson(url, token, Map.of());
        String nextDirectory = relativeDirectory.getName(0).toString();
        Path remainderDirectory = relativeDirectory.getNameCount() > 1
                ? relativeDirectory.subpath(1, relativeDirectory.getNameCount())
                : Path.of("");

        for (JsonNode item : data.path("tree")) {
            if (!"tree".equals(item.path("type").asText()) || !nextDirectory.equals(item.path("path").asText())) {
                continue;
            }
            GitHubTree nextTree = tree.cloneTree();
            nextTree.setTreeRef(item.path("sha").asText());
            nextTree.setDirectory(remainderDirectory);
            return recursivelyListGithubFiles(nextTree, currentDirectory.resolve(nextDirectory), token);
        }

        throw new GitHubError("Directory " + nextDirectory + " not found in " + currentDirectory);
    }

    private JsonNode readGitHubJson(String url, String token, Map<String, String> params) {
        String fullUrl = url + buildQuery(params);
        HttpResult response = sendGet(fullUrl, token, ACCEPT_GITHUB_JSON);
        try {
            JsonNode data = JSON.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GitHubError(data.path("message").asText("HTTP " + response.statusCode()));
            }
            if (data.has("message")) {
                throw new GitHubError(data.path("message").asText());
            }
            return data;
        } catch (IOException error) {
            throw new GitHubError("failed to parse GitHub response: " + fullUrl, error);
        }
    }

    private static byte[] downloadFileFromGithub(String githubApi,
                                                 HttpTransport transport,
                                                 GitHubTree tree,
                                                 String filePath,
                                                 String token) {
        String url = normalizeEndpoint(githubApi) + "/repos/" + encodePathPart(tree.getRepoOwner()) + "/"
                + encodePathPart(tree.getRepoName()) + "/contents/" + encodePath(filePath)
                + buildQuery(Map.of("ref", tree.getTreeRef()));
        HttpResult response = sendGet(transport, url, token, ACCEPT_GITHUB_RAW);
        if (response.statusCode() != 200) {
            throw new GitHubError("HTTP " + response.statusCode() + " while downloading " + filePath);
        }
        return response.body();
    }

    private HttpResult sendGet(String url, String token, String accept) {
        return sendGet(transport, url, token, accept);
    }

    private static HttpResult sendGet(HttpTransport transport, String url, String token, String accept) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", accept);
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", "Bearer " + token);
        }
        try {
            return transport.get(url, headers);
        } catch (IOException error) {
            throw new GitHubError("failed to query GitHub API: " + url, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new GitHubError("interrupted while querying GitHub API: " + url, error);
        }
    }

    private static void addSkillFile(List<SkillFile> fileList,
                                     GitHubBlob file,
                                     Path baseSkillPath,
                                     Path parentDirectory) {
        Path filePath = Path.of(file.path());
        Path relativePath = baseSkillPath.resolve(parentDirectory.relativize(filePath)).normalize();
        fileList.add(new SkillFile(file.path(), relativePath));
    }

    private static BaseFsOperation resolveFsOperation(String sysOperationId) {
        try {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            Object resourceMgr = runnerType.getMethod("resourceMgr").invoke(null);
            Object sysOperation = resourceMgr.getClass()
                    .getMethod("getSysOperation", String.class)
                    .invoke(resourceMgr, sysOperationId);
            if (sysOperation == null) {
                return null;
            }
            Method fsMethod = sysOperation.getClass().getMethod("fs");
            Object fs = fsMethod.invoke(sysOperation);
            return fs instanceof BaseFsOperation operation ? operation : null;
        } catch (ReflectiveOperationException error) {
            throw new GitHubError("failed to resolve sys_operation " + sysOperationId, error);
        }
    }

    private static Path resolveSkillPath(String skillsDir, Path relativePath) {
        Path root = skillsDir == null || skillsDir.isBlank() ? Path.of("") : Path.of(skillsDir);
        return root.resolve(relativePath).normalize();
    }

    private static Path stripRoot(Path directory) {
        if (isEmptyPath(directory)) {
            return Path.of("");
        }
        if (!directory.isAbsolute()) {
            return directory == null ? Path.of("") : directory;
        }
        Path stripped = Path.of("");
        for (int i = 0; i < directory.getNameCount(); i++) {
            stripped = stripped.resolve(directory.getName(i).toString());
        }
        return stripped;
    }

    private static boolean isEmptyPath(Path path) {
        return path == null || path.toString().isBlank();
    }

    private static String toGithubPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String normalizeEndpoint(String endpoint) {
        String resolved = endpoint == null || endpoint.isBlank() ? GITHUB_API : endpoint;
        while (resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        return resolved;
    }

    private static String buildQuery(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder query = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                query.append('&');
            }
            first = false;
            query.append(encodeQueryPart(entry.getKey()));
            query.append('=');
            query.append(encodeQueryPart(entry.getValue()));
        }
        return query.toString();
    }

    private static String encodePath(String path) {
        String[] segments = path.replace('\\', '/').split("/");
        List<String> encoded = new ArrayList<>();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                encoded.add(encodePathPart(segment));
            }
        }
        return String.join("/", encoded);
    }

    private static String encodePathPart(String value) {
        return encodeQueryPart(value).replace("+", "%20");
    }

    private static String encodeQueryPart(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    interface HttpTransport {
        HttpResult get(String url, Map<String, String> headers) throws IOException, InterruptedException;
    }

    record HttpResult(int statusCode, byte[] body) {
        HttpResult(int statusCode, String body) {
            this(statusCode, body.getBytes(StandardCharsets.UTF_8));
        }
    }

    public record GitHubBlob(String path) {
    }

    public record SkillFile(String path, Path relativePath) {
    }

    public record SearchResult(List<SkillFile> files, List<Path> skillPaths) {
        public SearchResult {
            files = List.copyOf(files);
            skillPaths = List.copyOf(skillPaths);
        }
    }

    private record ListedFiles(List<GitHubBlob> files, boolean truncated) {
        private ListedFiles {
            files = List.copyOf(files);
        }
    }

    private record JavaHttpTransport(HttpClient httpClient) implements HttpTransport {
        @Override
        public HttpResult get(String url, Map<String, String> headers) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new HttpResult(response.statusCode(), response.body());
        }
    }
}
