/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.nio.file.Path;

/**
 * GitHub repository tree reference used for remote skills.
 *
 * <p>Mirrors Python's {@code GitHubTree} in
 * {@code openjiuwen/core/single_agent/skills/remote_skill_util.py}.</p>
 */
public class GitHubTree {
    private String repoOwner;
    private String repoName;
    private String treeRef = "HEAD";
    private Path directory = Path.of("");

    public GitHubTree() {
    }

    public GitHubTree(String repoOwner, String repoName) {
        this(repoOwner, repoName, "HEAD", Path.of(""));
    }

    public GitHubTree(String repoOwner, String repoName, String treeRef, Path directory) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.treeRef = treeRef == null || treeRef.isBlank() ? "HEAD" : treeRef;
        this.directory = directory == null ? Path.of("") : directory;
    }

    public GitHubTree(String repoOwner, String repoName, String treeRef, String directory) {
        this(repoOwner, repoName, treeRef, directory == null ? Path.of("") : Path.of(directory));
    }

    public GitHubTree cloneTree() {
        return new GitHubTree(repoOwner, repoName, treeRef, directory);
    }

    public GitHubTree clone() {
        return cloneTree();
    }

    public GitHubTree copy() {
        return cloneTree();
    }

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
        this.treeRef = treeRef == null || treeRef.isBlank() ? "HEAD" : treeRef;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory == null ? Path.of("") : directory;
    }

    public void setDirectory(String directory) {
        setDirectory(directory == null ? Path.of("") : Path.of(directory));
    }
}
