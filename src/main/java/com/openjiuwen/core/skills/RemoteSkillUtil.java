// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for registering remote skills from GitHub.
 *
 * <p>This class provides functionality to download skill directories from GitHub
 * repositories and save them locally.
 *
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/skills/remote_skill_util.py::RemoteSkillUtil}
 *
 * @since 0.1.4
 */
public class RemoteSkillUtil {

    private static final Logger log = LoggerFactory.getLogger(RemoteSkillUtil.class);

    /**
     * The GitHub API base URL.
     */
    public static final String GITHUB_API = "https://api.github.com";

    /**
     * The default skills directory name.
     */
    public static final String SKILLS_DIR = "skills/";

    /**
     * The skill file name to look for.
     */
    public static final String SKILL_FILE_NAME = "SKILL.md";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * The system operation ID used for file operations.
     */
    private String sysOperationId;

    /**
     * Constructs a RemoteSkillUtil with the specified system operation ID.
     *
     * @param sysOperationId the system operation ID
     */
    public RemoteSkillUtil(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Gets the system operation ID.
     *
     * @return the system operation ID
     */
    public String getSysOperationId() {
        return sysOperationId;
    }

    /**
     * Sets the system operation ID.
     *
     * @param sysOperationId the new system operation ID
     */
    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Searches GitHub for skills in the specified tree.
     *
     * @param tree  the GitHub tree to search
     * @param token the GitHub API token (optional)
     * @return a SearchResult containing files and skill paths
     */
    public SearchResult searchGitHubForSkills(GitHubTree tree, String token) {
        ListFilesResult result = listGitHubFiles(tree, token);

        if (result.truncated()) {
            log.warn("GitHub file results truncated. Results can be incomplete");
        }

        List<GitHubFile> fileList = new ArrayList<>();
        List<Path> skillPaths = new ArrayList<>();

        List<GitHubFile> files = result.files();

        // Find SKILL.md files and collect related files
        for (int i = 0; i < files.size(); i++) {
            GitHubFile file = files.get(i);
            Path filePath = file.path();

            // Skip root directory files
            if (filePath.getNameCount() == 1) {
                continue;
            }

            Path parentDirectory = filePath.getParent();
            String fileName = filePath.getFileName().toString();

            if (!SKILL_FILE_NAME.equals(fileName)) {
                continue;
            }

            // Found SKILL.md, add it and search for sibling files
            Path baseSkillPath = Path.of(parentDirectory.getFileName().toString());
            addFile(fileList, file, baseSkillPath, parentDirectory);
            skillPaths.add(baseSkillPath);

            // Search backward for files in the same directory
            for (int j = i - 1; j >= 0; j--) {
                GitHubFile siblingFile = files.get(j);
                if (!isRelativeTo(siblingFile.path(), parentDirectory)) {
                    break;
                }
                addFile(fileList, siblingFile, baseSkillPath, parentDirectory);
            }

            // Search forward for files in the same directory
            for (int j = i + 1; j < files.size(); j++) {
                GitHubFile siblingFile = files.get(j);
                if (!isRelativeTo(siblingFile.path(), parentDirectory)) {
                    break;
                }
                addFile(fileList, siblingFile, baseSkillPath, parentDirectory);
            }
        }

        return new SearchResult(fileList, skillPaths);
    }

    /**
     * Uploads skills from GitHub to the local skills directory.
     *
     * @param tree       the GitHub tree to download from
     * @param skillsDir  the local skills directory
     * @param token      the GitHub API token (optional)
     * @return list of skill paths that were uploaded
     */
    public CompletableFuture<List<Path>> uploadSkillFromGitHub(
            GitHubTree tree,
            String skillsDir,
            String token) {

        return CompletableFuture.supplyAsync(() -> {
            SearchResult result = searchGitHubForSkills(tree, token);

            for (GitHubFile file : result.files()) {
                try {
                    byte[] data = downloadFileFromGitHub(tree, file.path().toString(), token);
                    Path relativePath = file.relativePath();
                    Path fullPath = Path.of(skillsDir).resolve(relativePath);

                    log.info("Uploading file to {}", relativePath);
                    Files.createDirectories(fullPath.getParent());
                    Files.write(fullPath, data);

                } catch (Exception e) {
                    log.warn("Failed to download file: {}", file.path(), e);
                }
            }

            return result.skillPaths();
        }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Downloads a file from GitHub.
     *
     * @param tree     the GitHub tree
     * @param filePath the file path within the repository
     * @param token    the GitHub API token (optional)
     * @return the file content as bytes
     * @throws BaseError if download fails
     */
    public byte[] downloadFileFromGitHub(GitHubTree tree, String filePath, String token) {
        String url = String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                tree.getRepoOwner(), tree.getRepoName(), filePath, tree.getTreeRef());

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.raw");

        if (token != null && !token.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        try (Response response = HTTP_CLIENT.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw BaseError.builder(StatusCode.SKILL_GITHUB_DOWNLOAD_ERROR)
                        .param("file_path", filePath)
                        .param("status_code", response.code())
                        .build();
            }

            return response.body().bytes();

        } catch (IOException e) {
            throw BaseError.builder(StatusCode.SKILL_GITHUB_API_ERROR)
                    .param("error_msg", "Failed to download file: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    /**
     * Lists all files in a GitHub tree.
     *
     * @param tree  the GitHub tree to list
     * @param token the GitHub API token (optional)
     * @return the list result
     */
    private ListFilesResult listGitHubFiles(GitHubTree tree, String token) {
        // Normalize directory path
        Path directory = tree.getDirectory();
        if (directory.isAbsolute() && directory.getNameCount() > 0) {
            directory = directory.subpath(0, directory.getNameCount());
            tree = tree.withDirectory(directory);
        }

        return recursivelyListGitHubFiles(tree, Path.of(""), token);
    }

    /**
     * Recursively lists files in a GitHub tree.
     */
    private ListFilesResult recursivelyListGitHubFiles(
            GitHubTree tree,
            Path currentDirectory,
            String token) {

        String url = tree.getTreeApiUrl();
        Path relativeDirectory = tree.getDirectory();

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json");

        if (token != null && !token.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        try (Response response = HTTP_CLIENT.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorMessage = "GitHub API request failed";
                try {
                    JsonNode errorNode = OBJECT_MAPPER.readTree(response.body().string());
                    if (errorNode.has("message")) {
                        errorMessage = errorNode.get("message").asText();
                    }
                } catch (Exception ignored) {
                }

                throw BaseError.builder(StatusCode.SKILL_GITHUB_API_ERROR)
                        .param("error_msg", errorMessage)
                        .build();
            }

            JsonNode data = OBJECT_MAPPER.readTree(response.body().string());

            // If no relative directory specified, fetch entire tree recursively
            if (relativeDirectory.getNameCount() == 0) {
                boolean truncated = data.has("truncated") && data.get("truncated").asBoolean();
                List<GitHubFile> files = new ArrayList<>();

                if (data.has("tree")) {
                    for (JsonNode item : data.get("tree")) {
                        if ("blob".equals(item.get("type").asText())) {
                            Path path = currentDirectory.resolve(item.get("path").asText());
                            files.add(new GitHubFile(path, null));
                        }
                    }
                }

                return new ListFilesResult(files, truncated);
            }

            // Navigate to specific directory
            String nextDirectory = relativeDirectory.getName(0).toString();
            Path remainderDirectory = relativeDirectory.getNameCount() > 1
                    ? relativeDirectory.subpath(1, relativeDirectory.getNameCount())
                    : Path.of("");

            if (data.has("tree")) {
                for (JsonNode item : data.get("tree")) {
                    if ("tree".equals(item.get("type").asText()) &&
                            nextDirectory.equals(item.get("path").asText())) {

                        String newTreeRef = item.get("sha").asText();
                        GitHubTree newTree = tree.clone()
                                .withTreeRef(newTreeRef)
                                .withDirectory(remainderDirectory);

                        return recursivelyListGitHubFiles(
                                newTree,
                                currentDirectory.resolve(nextDirectory),
                                token);
                    }
                }
            }

            throw BaseError.builder(StatusCode.SKILL_GITHUB_DIRECTORY_NOT_FOUND)
                    .param("directory", nextDirectory)
                    .build();

        } catch (IOException e) {
            throw BaseError.builder(StatusCode.SKILL_GITHUB_API_ERROR)
                    .param("error_msg", e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    /**
     * Checks if a path is relative to a parent directory.
     */
    private boolean isRelativeTo(Path path, Path parent) {
        if (path.getNameCount() < parent.getNameCount()) {
            return false;
        }
        for (int i = 0; i < parent.getNameCount(); i++) {
            if (!path.getName(i).equals(parent.getName(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds a file to the list with its relative path.
     */
    private void addFile(List<GitHubFile> fileList, GitHubFile file, Path baseSkillPath, Path parentDirectory) {
        Path relativePath = baseSkillPath.resolve(file.path().subpath(parentDirectory.getNameCount(), file.path().getNameCount()));
        fileList.add(new GitHubFile(file.path(), relativePath));
    }

    /**
     * Represents a GitHub file with its path and relative path.
     */
    public record GitHubFile(Path path, Path relativePath) {
    }

    /**
     * Result of listing GitHub files.
     */
    public record ListFilesResult(List<GitHubFile> files, boolean truncated) {
    }

    /**
     * Result of searching for skills.
     */
    public record SearchResult(List<GitHubFile> files, List<Path> skillPaths) {
    }
}
