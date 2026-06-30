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

    /**
     * Auto-generated for codecheck compliance.
     */
    public GitHubTree(String repoOwner, String repoName) {
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.treeRef = "HEAD";
        this.directory = "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GitHubTree copy() {
        return new GitHubTree(repoOwner, repoName, treeRef, directory);
    }
}
