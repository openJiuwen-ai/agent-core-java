/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;

import java.util.function.Consumer;

/**
 * Agent execution boundary used by the durable worker.
 *
 * @since 0.1.12
 */
@FunctionalInterface
public interface IssueTaskExecutor {
    /**
     * Run one leased job through implementation, verification, commit, and publication.
     *
     * @param job leased durable job
     * @param issue current untrusted Issue data
     * @param progress durable progress callback
     * @return typed execution result
     */
    IssueExecutionResult execute(EvolutionJob job, GitCodeIssue issue,
                                 Consumer<EvolutionJobState> progress);

    /**
     * Run with cooperative cancellation checkpoints around durable stage boundaries.
     *
     * @param job leased durable job
     * @param issue current untrusted Issue data
     * @param progress durable progress callback
     * @param cancellation cooperative cancellation checkpoint
     * @return typed execution result
     */
    default IssueExecutionResult execute(EvolutionJob job, GitCodeIssue issue,
                                         Consumer<EvolutionJobState> progress,
                                         CancellationCheckpoint cancellation) {
        cancellation.check();
        return execute(job, issue, state -> {
            cancellation.check();
            progress.accept(state);
            cancellation.check();
        });
    }

    /**
     * Remove only local resources proven to be owned by this Job.
     *
     * @param job durable job whose execution has stopped
     */
    default void cleanup(EvolutionJob job) {
    }

    /** Cooperative checkpoint supplied by the durable worker. */
    @FunctionalInterface
    interface CancellationCheckpoint {
        /** Throw when execution must stop before starting more work. */
        void check();
    }
}
