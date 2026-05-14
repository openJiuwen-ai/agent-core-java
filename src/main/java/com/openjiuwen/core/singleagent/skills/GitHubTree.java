/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a GitHub directory tree with its metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GitHubTree {

    private String repoOwner;
    private String repoName;
    private String treeRef = "HEAD";
    private String directory = "";

    public GitHubTree(String repoOwner, String repoName, String treeRef, String directory) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.treeRef = treeRef;
        this.directory = directory;
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
        this.treeRef = treeRef;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public GitHubTree(String repoOwner, String repoName) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.treeRef = "HEAD";
        this.directory = "";
    }

    public GitHubTree copy() {
        return new GitHubTree(repoOwner, repoName, treeRef, directory);
    }
}
