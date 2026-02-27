// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Represents a GitHub directory tree with its metadata.
 *
 * <p>This class encapsulates information about a GitHub repository's directory structure,
 * including the owner, repository name, tree reference, and the target directory path.
 *
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/skills/remote_skill_util.py::GitHubTree}
 *
 * @since 0.1.4
 */
public class GitHubTree {

    /**
     * Default tree reference for HEAD.
     */
    public static final String HEAD_REF = "HEAD";

    /**
     * The owner of the GitHub repository.
     */
    private String repoOwner;

    /**
     * The name of the GitHub repository.
     */
    private String repoName;

    /**
     * A reference to the root of a GitHub directory within the repository.
     * Use "HEAD" for the root and the corresponding hash for sub-folders.
     */
    private String treeRef = HEAD_REF;

    /**
     * The relative directory (relative to tree_ref) to search.
     */
    private Path directory = Paths.get("");

    /**
     * Default constructor.
     */
    public GitHubTree() {
    }

    /**
     * Full constructor.
     *
     * @param repoOwner the repository owner
     * @param repoName  the repository name
     * @param treeRef   the tree reference
     * @param directory the target directory
     */
    public GitHubTree(String repoOwner, String repoName, String treeRef, Path directory) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.treeRef = treeRef != null ? treeRef : HEAD_REF;
        this.directory = directory != null ? directory : Paths.get("");
    }

    /**
     * Private constructor for builder.
     */
    private GitHubTree(Builder builder) {
        this.repoOwner = builder.repoOwner;
        this.repoName = builder.repoName;
        this.treeRef = builder.treeRef != null ? builder.treeRef : HEAD_REF;
        this.directory = builder.directory != null ? builder.directory : Paths.get("");
    }

    // Getters and Setters

    public String getRepoOwner() {
        return repoOwner;
    }

    public void setRepoOwner(String repoOwner) {
        this.repoOwner = repoOwner;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getTreeRef() {
        return treeRef;
    }

    public void setTreeRef(String treeRef) {
        this.treeRef = treeRef;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    /**
     * Creates a GitHubTree with default treeRef (HEAD) and empty directory.
     *
     * @param repoOwner the repository owner
     * @param repoName  the repository name
     * @return a new GitHubTree instance
     */
    public static GitHubTree of(String repoOwner, String repoName) {
        return builder()
                .repoOwner(repoOwner)
                .repoName(repoName)
                .build();
    }

    /**
     * Creates a GitHubTree with specified directory.
     *
     * @param repoOwner the repository owner
     * @param repoName  the repository name
     * @param directory the target directory path
     * @return a new GitHubTree instance
     */
    public static GitHubTree of(String repoOwner, String repoName, String directory) {
        return builder()
                .repoOwner(repoOwner)
                .repoName(repoName)
                .directory(Paths.get(directory))
                .build();
    }

    /**
     * Creates a GitHubTree with all parameters.
     *
     * @param repoOwner the repository owner
     * @param repoName  the repository name
     * @param treeRef   the tree reference (SHA or HEAD)
     * @param directory the target directory path
     * @return a new GitHubTree instance
     */
    public static GitHubTree of(String repoOwner, String repoName, String treeRef, String directory) {
        return builder()
                .repoOwner(repoOwner)
                .repoName(repoName)
                .treeRef(treeRef)
                .directory(Paths.get(directory))
                .build();
    }

    /**
     * Creates a clone of this GitHubTree.
     *
     * @return a new GitHubTree with the same values
     */
    public GitHubTree clone() {
        return builder()
                .repoOwner(this.repoOwner)
                .repoName(this.repoName)
                .treeRef(this.treeRef)
                .directory(this.directory)
                .build();
    }

    /**
     * Creates a clone with a new tree reference.
     *
     * @param newTreeRef the new tree reference
     * @return a new GitHubTree with the updated tree reference
     */
    public GitHubTree withTreeRef(String newTreeRef) {
        return builder()
                .repoOwner(this.repoOwner)
                .repoName(this.repoName)
                .treeRef(newTreeRef)
                .directory(this.directory)
                .build();
    }

    /**
     * Creates a clone with a new directory.
     *
     * @param newDirectory the new directory path
     * @return a new GitHubTree with the updated directory
     */
    public GitHubTree withDirectory(Path newDirectory) {
        return builder()
                .repoOwner(this.repoOwner)
                .repoName(this.repoName)
                .treeRef(this.treeRef)
                .directory(newDirectory)
                .build();
    }

    /**
     * Returns the GitHub API URL for this tree.
     *
     * @return the API URL string
     */
    public String getTreeApiUrl() {
        return String.format("https://api.github.com/repos/%s/%s/git/trees/%s",
                repoOwner, repoName, treeRef);
    }

    /**
     * Returns the GitHub contents API URL for a specific file path.
     *
     * @param filePath the file path within the repository
     * @return the contents API URL string
     */
    public String getContentsApiUrl(String filePath) {
        return String.format("https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
                repoOwner, repoName, filePath, treeRef);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitHubTree that = (GitHubTree) o;
        return Objects.equals(repoOwner, that.repoOwner) &&
                Objects.equals(repoName, that.repoName) &&
                Objects.equals(treeRef, that.treeRef) &&
                Objects.equals(directory, that.directory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repoOwner, repoName, treeRef, directory);
    }

    @Override
    public String toString() {
        return String.format("GitHubTree{repoOwner='%s', repoName='%s', treeRef='%s', directory=%s}",
                repoOwner, repoName, treeRef, directory);
    }

    /**
     * Creates a new builder for constructing GitHubTree instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing GitHubTree instances.
     */
    public static class Builder {
        private String repoOwner;
        private String repoName;
        private String treeRef = HEAD_REF;
        private Path directory = Paths.get("");

        public Builder repoOwner(String repoOwner) {
            this.repoOwner = repoOwner;
            return this;
        }

        public Builder repoName(String repoName) {
            this.repoName = repoName;
            return this;
        }

        public Builder treeRef(String treeRef) {
            this.treeRef = treeRef;
            return this;
        }

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public GitHubTree build() {
            return new GitHubTree(this);
        }
    }
}
