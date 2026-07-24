/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.publish;

import java.nio.file.Path;

/**
 * Privileged Git push boundary kept outside Agent tools.
 *
 * @since 0.1.12
 */
public interface ForkPushGateway {
    /**
     * Push an exact verified commit to the robot Fork.
     *
     * @param worktree isolated worktree
     * @param branch policy-compliant branch
     * @param expectedHeadSha verified commit SHA
     * @return typed push result
     */
    PushResult push(Path worktree, String branch, String expectedHeadSha);

    /**
     * Result of one controlled push attempt.
     *
     * @param success whether the push succeeded
     * @param headSha observed local head SHA
     * @param error safe error text
     */
    record PushResult(boolean success, String headSha, String error) {
    }
}
