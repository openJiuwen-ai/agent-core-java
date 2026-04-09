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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for registering remote skills from GitHub.
 *
 * <p>Downloads skill directories from GitHub and writes them into a local skills directory.</p>
 */
public class RemoteSkillUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GITHUB_API = "https://api.github.com";
    private static final String SKILL_FILE_NAME = "SKILL.md";

    private String sysOperationId;

    public RemoteSkillUtil(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Download a file from GitHub.
     *
     * @param tree     the GitHub tree reference
     * @param filePath file path within the repository
     * @param token    GitHub API token (optional)
     * @return file contents as byte array
     */
    public static byte[] downloadFileFromGitHub(GitHubTree tree, String filePath, String token) {
        String query = buildQuery(Map.of("ref", tree.getTreeRef()));
        String url = GITHUB_API + "/repos/" + tree.getRepoOwner() + "/" + tree.getRepoName()
                + "/contents/" + filePath + query;

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
     * @param tree      the GitHub tree
     * @param skillsDir local directory for skills
     * @param token     GitHub API token (optional)
     * @return list of skill directory paths
     */
    public List<String> uploadSkillFromGitHub(GitHubTree tree, String skillsDir, String token) {
        Loggers.AGENT.info("Uploading skills from GitHub: " + tree.getRepoOwner() + "/" + tree.getRepoName());

        SearchResult searchResult = searchGitHubForSkills(tree, token);
        Path baseDir = skillsDir == null || skillsDir.isBlank() ? Path.of("") : Path.of(skillsDir);

        for (SkillFile skillFile : searchResult.files()) {
            byte[] data = downloadFileFromGitHub(tree, skillFile.path(), token);
            Path target = baseDir.resolve(skillFile.relativePath());
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, data);
            } catch (IOException e) {
                throw new GitHubError("Failed to write downloaded skill file: " + target, e);
            }
        }

        Loggers.AGENT.info("Remote skill upload completed for " + tree.getRepoOwner()
                + "/" + tree.getRepoName());
        return searchResult.skillPaths();
    }

    /**
     * Search a GitHub tree for skill directories containing SKILL.md files.
     *
     * @param tree  the GitHub tree reference
     * @param token GitHub API token (optional)
     * @return search result containing skill files and skill paths
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
     * @param tree  the GitHub tree reference
     * @param token GitHub API token (optional)
     * @return list of file blobs
     */
    public List<GitHubBlob> listGitHubFiles(GitHubTree tree, String token) {
        String normalizedDirectory = normalizeDirectory(tree.getDirectory());
        return recursivelyListGitHubFiles(tree, Path.of(""), normalizedDirectory, token);
    }

    private List<GitHubBlob> recursivelyListGitHubFiles(
            GitHubTree tree,
            Path currentDirectory,
            String remainingDirectory,
            String token
    ) {
        String treeUrl = GITHUB_API + "/repos/" + tree.getRepoOwner() + "/" + tree.getRepoName()
                + "/git/trees/" + tree.getTreeRef();

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
                Path resolved = currentDirectory.resolve(item.path("path").asText()).normalize();
                files.add(new GitHubBlob(resolved.toString().replace('\\', '/')));
            }
            return files;
        }

        Path remainingPath = Path.of(remainingDirectory);
        String nextDirectory = remainingPath.getName(0).toString();
        String nextRemainder = remainingPath.getNameCount() > 1
                ? remainingPath.subpath(1, remainingPath.getNameCount()).toString()
                : "";

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
     */
    public record GitHubBlob(String path) {
    }

    /**
     * Represents a skill file with its path and relative path.
     */
    public record SkillFile(String path, String relativePath) {
    }

    /**
     * Result of searching for skills in a GitHub tree.
     */
    public record SearchResult(List<SkillFile> files, List<String> skillPaths) {
    }
}
