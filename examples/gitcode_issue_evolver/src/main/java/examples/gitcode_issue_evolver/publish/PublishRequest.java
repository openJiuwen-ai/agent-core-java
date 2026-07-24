/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.publish;

import java.nio.file.Path;
import java.util.List;

/**
 * Validated pipeline output presented to the privileged publisher.
 *
 * @param jobId durable job identifier
 * @param issueIid associated Issue IID
 * @param branch verified issue branch
 * @param expectedHeadSha verified commit SHA
 * @param title PR title
 * @param body PR description
 * @param worktree isolated worktree root
 * @param changedFiles verified changed paths
 * @param ciPassed whether the configured CI gate passed
 * @since 0.1.12
 */
public record PublishRequest(
        String jobId,
        long issueIid,
        String branch,
        String expectedHeadSha,
        String title,
        String body,
        Path worktree,
        List<String> changedFiles,
        boolean ciPassed) {
    public PublishRequest(String jobId, long issueIid, String branch, String expectedHeadSha,
                          String title, String body, Path worktree, List<String> changedFiles,
                          boolean ciPassed) {
        this.jobId = jobId;
        this.issueIid = issueIid;
        this.branch = branch;
        this.expectedHeadSha = expectedHeadSha;
        this.title = title;
        this.body = body;
        this.worktree = worktree;
        this.changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        this.ciPassed = ciPassed;
    }
}
