/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for registering remote skills from GitHub.
 * <p>
 * Downloads skill directories from GitHub and writes them into a local skills directory.
 * </p>
 * 
 * @since 0.1.7
 */
public class RemoteSkillUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GITHUB_API = "https://api.github.com";
    private static final String SKILL_FILE_NAME = "SKILL.md";

    private String sysOperationId;

    /**
     * RemoteSkillUtil.
     * 
     * @param sysOperationId sysOperationId
     * @since 0.1.7
     */
    public RemoteSkillUtil(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * getSysOperationId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSysOperationId() {
        return sysOperationId;
    }

    /**
     * setSysOperationId.
     * 
     * @param sysOperationId sysOperationId
     * @since 0.1.7
     */
    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Download a file from GitHub.
     * 
     * @param tree the GitHub tree reference
     * @param filePath file path within the repository
     * @param token GitHub API token (optional)
     * @return file contents as byte array
     * @since 0.1.7
     */
    public static byte[] downloadFileFromGitHub(GitHubTree tree, String filePath, String token) {
        String query = buildQuery(Map.of("ref", tree.getTreeRef()));
        String url =
            GITHUB_API + "/repos/" + tree.getRepoOwner() + "/" + tree.getRepoName() + "/contents/" + filePath + query;

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github.raw");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();
            if (status != 200) {
                throw new IOException("HTTP " + status + " while downloading " + filePath);
            }

            try (InputStream is = conn.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            throw new GitHubError("Failed to download file from GitHub: " + filePath, e);
        }
    }

    /**
     * Upload skills from GitHub to local storage.
     * 
     * @param tree the GitHub tree
     * @param skillsDir local directory for skills
     * @param token GitHub API token (optional)
     * @return list of skill directory paths
     * @since 0.1.7
     */
    public List<String> uploadSkillFromGitHub(GitHubTree tree, String skillsDir, String token) {
        Loggers.AGENT.info("Uploading skills from GitHub: " + tree.getRepoOwner() + "/" + tree.getRepoName());

        SearchResult searchResult = searchGitHubForSkills(tree, token);
        Path baseDir = skillsDir == null || skillsDir.isBlank() ? Path.of("") : Path.of(skillsDir);
        try {
            Files.createDirectories(baseDir);
            baseDir = baseDir.toRealPath();
        } catch (IOException e) {
            throw new GitHubError("Failed to resolve local skills directory: " + baseDir, e);
        }

        for (SkillFile skillFile : searchResult.files()) {
            byte[] data = downloadFileFromGitHub(tree, skillFile.path(), token);
            try {
                Path target = resolveSafeTarget(baseDir, skillFile.relativePath());
                Files.write(target, data);
            } catch (IOException e) {
                throw new GitHubError("Failed to write downloaded skill file: " + skillFile.relativePath(), e);
            }
        }

        Loggers.AGENT.info("Remote skill upload completed for " + tree.getRepoOwner() + "/" + tree.getRepoName());
        return searchResult.skillPaths();
    }

    static Path resolveSafeTarget(Path baseDir, String relativePath) throws IOException {
        if (baseDir == null || relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Skill base directory and relative path must not be blank.");
        }

        Path realBaseDir = baseDir.toRealPath();
        Path requestedPath = Path.of(relativePath);
        if (requestedPath.isAbsolute()) {
            throw new SecurityException("Remote skill path must be relative: " + relativePath);
        }
        for (Path segment : requestedPath) {
            if ("..".equals(segment.toString())) {
                throw new SecurityException("Remote skill path must not contain '..': " + relativePath);
            }
        }

        Path target = realBaseDir.resolve(requestedPath).normalize();
        if (!target.startsWith(realBaseDir)) {
            throw new SecurityException("Remote skill path is outside the skills directory: " + relativePath);
        }

        Path parent = target.getParent();
        if (parent == null) {
            throw new SecurityException("Remote skill path has no parent: " + relativePath);
        }
        Path existingAncestor = parent;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null || !existingAncestor.toRealPath().startsWith(realBaseDir)) {
            throw new SecurityException("Remote skill path is outside the skills directory: " + relativePath);
        }

        Files.createDirectories(parent);
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realBaseDir)) {
            throw new SecurityException("Remote skill path is outside the skills directory: " + relativePath);
        }

        Path safeTarget = realParent.resolve(target.getFileName()).normalize();
        if (Files.exists(safeTarget, LinkOption.NOFOLLOW_LINKS)) {
            Path realTarget = safeTarget.toRealPath();
            if (!realTarget.startsWith(realBaseDir)) {
                throw new SecurityException("Remote skill path is outside the skills directory: " + relativePath);
            }
            return realTarget;
        }
        return safeTarget;
    }

    /**
     * Search a GitHub tree for skill directories containing SKILL.md files.
     * 
     * @param tree the GitHub tree reference
     * @param token GitHub API token (optional)
     * @return search result containing skill files and skill paths
     * @since 0.1.7
     */
    public SearchResult searchGitHubForSkills(GitHubTree tree, String token) {
        List<GitHubBlob> files = listGitHubFiles(tree, token);
        files.sort(Comparator.comparing(GitHubBlob::path));

        Map<Path, List<GitHubBlob>> filesByParent = new LinkedHashMap<>();
        for (GitHubBlob file : files) {
            Path filePath = Path.of(file.path());
            Path parent = filePath.getParent();
            if (parent == null) {
                continue;
            }
            filesByParent.computeIfAbsent(parent, key -> new ArrayList<>()).add(file);
        }

        List<SkillFile> skillFiles = new ArrayList<>();
        List<String> skillPaths = new ArrayList<>();

        for (Map.Entry<Path, List<GitHubBlob>> entry : filesByParent.entrySet()) {
            Path parent = entry.getKey();
            List<GitHubBlob> siblingFiles = entry.getValue();
            boolean hasSkillFile = siblingFiles.stream()
                    .anyMatch(file -> Path.of(file.path()).getFileName().toString().equals(SKILL_FILE_NAME));
            if (!hasSkillFile) {
                continue;
            }
            if (parent.getNameCount() == 0) {
                continue;
            }

            Path baseSkillPath = Path.of(parent.getFileName().toString());
            skillPaths.add(baseSkillPath.toString());

            for (GitHubBlob file : siblingFiles) {
                Path filePath = Path.of(file.path());
                Path relativePath = baseSkillPath.resolve(parent.relativize(filePath));
                skillFiles.add(new SkillFile(file.path(), relativePath.toString()));
            }
        }

        return new SearchResult(skillFiles, skillPaths);
    }

    /**
     * List all files in a GitHub tree.
     * 
     * @param tree the GitHub tree reference
     * @param token GitHub API token (optional)
     * @return list of file blobs
     * @since 0.1.7
     */
    public List<GitHubBlob> listGitHubFiles(GitHubTree tree, String token) {
        String normalizedDirectory = normalizeDirectory(tree.getDirectory());
        return recursivelyListGitHubFiles(tree, Path.of(""), normalizedDirectory, token);
    }

    /**
     * recursivelyListGitHubFiles.
     * 
     * @param tree tree
     * @param currentDirectory currentDirectory
     * @param remainingDirectory remainingDirectory
     * @param token token
     * @return the result
     * @since 0.1.7
     */
    private List<GitHubBlob> recursivelyListGitHubFiles(GitHubTree tree, Path currentDirectory,
            String remainingDirectory, String token) {
        String treeUrl =
            GITHUB_API + "/repos/" + tree.getRepoOwner() + "/" + tree.getRepoName() + "/git/trees/" + tree.getTreeRef();

        Map<String, String> params = remainingDirectory.isEmpty() ? Map.of("recursive", "1") : Map.of();
        JsonNode data = readGitHubJson(treeUrl, token, params);

        if (remainingDirectory.isEmpty()) {
            if (data.path("truncated").asBoolean(false)) {
                Loggers.AGENT.warning("Warning: file results truncated. Results can be incomplete");
            }

            List<GitHubBlob> files = new ArrayList<>();
            for (JsonNode item : data.path("tree")) {
                if (!"blob".equals(item.path("type").asText())) {
                    continue;
                }
                Path isResolved = currentDirectory.resolve(item.path("path").asText()).normalize();
                files.add(new GitHubBlob(isResolved.toString().replace('\\', '/')));
            }
            return files;
        }

        Path remainingPath = Path.of(remainingDirectory);
        String nextDirectory = remainingPath.getName(0).toString();
        String nextRemainder =
            remainingPath.getNameCount() > 1 ? remainingPath.subpath(1, remainingPath.getNameCount()).toString() : "";

        for (JsonNode item : data.path("tree")) {
            if (!"tree".equals(item.path("type").asText())) {
                continue;
            }
            if (!nextDirectory.equals(item.path("path").asText())) {
                continue;
            }

            GitHubTree nextTree = tree.copy();
            nextTree.setTreeRef(item.path("sha").asText());
            nextTree.setDirectory(nextRemainder);
            return recursivelyListGitHubFiles(nextTree, currentDirectory.resolve(nextDirectory), nextRemainder, token);
        }

        throw new GitHubError("Directory " + nextDirectory + " not found in " + currentDirectory);
    }

    /**
     * readGitHubJson.
     * 
     * @param url url
     * @param token token
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private static JsonNode readGitHubJson(String url, String token, Map<String, String> params) {
        String fullUrl = url + buildQuery(params);
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(fullUrl).toURL().openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();
            try (InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()) {
                JsonNode data = MAPPER.readTree(is);
                if (status < 200 || status >= 300) {
                    String message = data.path("message").asText("HTTP " + status);
                    throw new IOException(message);
                }
                if (data.has("message") && !"".equals(data.path("message").asText())) {
                    throw new IOException(data.path("message").asText());
                }
                return data;
            }
        } catch (IOException e) {
            throw new GitHubError("Failed to query GitHub API: " + fullUrl, e);
        }
    }

    /**
     * buildQuery.
     * 
     * @param params params
     * @return the result
     * @since 0.1.7
     */
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
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            query.append('=');
            query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    /**
     * normalizeDirectory.
     * 
     * @param directory directory
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return "";
        }
        String normalized = directory.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Represents a file blob from GitHub.
     * 
     * @since 0.1.7
     */
    public record GitHubBlob(String path) {
    }

    /**
     * Represents a skill file with its path and relative path.
     * 
     * @since 0.1.7
     */
    public record SkillFile(String path, String relativePath) {
    }

    /**
     * Result of searching for skills in a GitHub tree.
     * 
     * @since 0.1.7
     */
    public record SearchResult(List<SkillFile> files, List<String> skillPaths) {
    }
}
