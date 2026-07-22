/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import com.openjiuwen.autoharness.infra.GitOperations;
import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.agent.AgentModelSettings;
import examples.gitcode_issue_evolver.agent.IssueWorkerAgent;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.infrastructure.CIGateResult;
import examples.gitcode_issue_evolver.infrastructure.CIGateRunner;
import examples.gitcode_issue_evolver.infrastructure.CommitFailureType;
import examples.gitcode_issue_evolver.infrastructure.ControlledCommitter;
import examples.gitcode_issue_evolver.infrastructure.ExampleWorktreeManager;
import examples.gitcode_issue_evolver.infrastructure.VerificationFailureType;
import examples.gitcode_issue_evolver.infrastructure.WorktreePreparationException;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.profile.ChangeValidation;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.profile.VerificationPlan;
import examples.gitcode_issue_evolver.publish.PublishRequest;
import examples.gitcode_issue_evolver.publish.PublishResult;
import examples.gitcode_issue_evolver.publish.PullRequestPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Runs the complete demo task without depending on AutoHarness pipeline internals.
 *
 * @since 0.1.12
 */
public final class ExampleIssueTaskExecutor implements IssueTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleIssueTaskExecutor.class);
    private static final String DISABLED_LOCAL_GIT_TOKEN = "local-operations-only";
    private final AutoEvolvingConfig config;
    private final RepositoryCoordinates coordinates;
    private final RepositoryProfile profile;
    private final PullRequestPublisher publisher;
    private final ExampleWorktreeManager worktreeManager;
    private final IssueWorkerAgent agent;

    /**
     * Create the Example-owned Issue execution pipeline.
     *
     * @param config resolved demo configuration
     * @param profile Java repository policy
     * @param publisher privileged non-Agent publisher
     * @param trustedSkillsRoot single trusted Skill root
     */
    public ExampleIssueTaskExecutor(AutoEvolvingConfig config, RepositoryProfile profile,
                                    PullRequestPublisher publisher, Path trustedSkillsRoot) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.coordinates = this.config.repositoryCoordinates();
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.worktreeManager = new ExampleWorktreeManager(this.config);
        this.agent = new IssueWorkerAgent(new AgentModelSettings(
                this.config.getModelProvider(), this.config.getModelApiKey(),
                this.config.getModelApiBase(), this.config.getModelName(),
                this.config.isModelVerifySsl()), trustedSkillsRoot);
    }

    @Override
    public IssueExecutionResult execute(EvolutionJob job, GitCodeIssue issue,
                                        Consumer<EvolutionJobState> progress) {
        return execute(job, issue, progress, () -> {
        });
    }

    @Override
    public IssueExecutionResult execute(EvolutionJob job, GitCodeIssue issue,
                                        Consumer<EvolutionJobState> progress,
                                        CancellationCheckpoint cancellation) {
        SparseCheckoutIssuePolicy.Validation sparseValidation = SparseCheckoutIssuePolicy.validate(issue);
        if (!sparseValidation.allowed()) {
            return IssueExecutionResult.outsideSparseCheckoutScope(sparseValidation.excludedPaths());
        }
        ExampleWorktreeManager.PreparedWorktree prepared;
        try {
            cancellation.check();
            prepared = worktreeManager.prepare(job);
        } catch (WorktreePreparationException ex) {
            return IssueExecutionResult.failed(IssueExecutionErrorCode.WORKTREE_INFRASTRUCTURE_FAILED,
                    ex.getMessage(), true);
        }

        IssueTargetPathPreflight.Validation targets = IssueTargetPathPreflight.validate(issue, prepared.path());
        if (!targets.available()) {
            return IssueExecutionResult.targetPathNotFound(targets.missingPaths());
        }
        List<String> outOfScopeTargets = targets.explicitPaths().stream()
                .filter(path -> !path.startsWith("src/main/java/") && !path.startsWith("src/test/java/"))
                .toList();
        if (!outOfScopeTargets.isEmpty()) {
            return IssueExecutionResult.outsideSparseCheckoutScope(outOfScopeTargets);
        }

        progress.accept(EvolutionJobState.IMPLEMENTING);
        VerificationOutcome verification = implementAndVerify(job, issue, prepared.path(), cancellation,
                progress);
        if (!verification.passed()) {
            return IssueExecutionResult.failed(verification.errorCode(), verification.error(),
                    verification.retryable());
        }

        cancellation.check();
        GitOperations git = localGit(prepared.path());
        List<String> changedFiles = git.listDirtyFiles();
        ChangeValidation validation = profile.validateChanges(changedFiles);
        if (changedFiles.isEmpty() || !validation.allowed()) {
            String detail = changedFiles.isEmpty() ? "Agent produced no repository changes"
                    : "Agent changed disallowed paths: " + String.join(", ", validation.violations());
            return IssueExecutionResult.failed(IssueExecutionErrorCode.COMMIT_VALIDATION_FAILED,
                    detail, false);
        }

        ControlledCommitter.CommitResult commit = new ControlledCommitter(git).commit(
                changedFiles, "fix: resolve GitCode issue #" + issue.iid());
        if (!commit.success()) {
            CommitFailureType failureType = commit.failureType();
            IssueExecutionErrorCode code = failureType == CommitFailureType.VALIDATION
                    ? IssueExecutionErrorCode.COMMIT_VALIDATION_FAILED
                    : IssueExecutionErrorCode.COMMIT_INFRASTRUCTURE_FAILED;
            return IssueExecutionResult.failed(code, commit.error(), failureType.isRetryable());
        }

        progress.accept(EvolutionJobState.COMMITTED);
        cancellation.check();
        progress.accept(EvolutionJobState.PUBLISHING);
        PublishResult result = publisher.publish(new PublishRequest(
                job.id(),
                issue.iid(),
                prepared.branch(),
                commit.commitSha(),
                pullRequestTitle(issue),
                pullRequestBody(issue),
                prepared.path(),
                commit.committedFiles(),
                true));
        return IssueExecutionResult.fromPublishResult(result);
    }

    @Override
    public void cleanup(EvolutionJob job) {
        worktreeManager.cleanup(job);
    }

    private VerificationOutcome implementAndVerify(EvolutionJob job, GitCodeIssue issue, Path worktree,
                                                    CancellationCheckpoint cancellation,
                                                    Consumer<EvolutionJobState> progress) {
        VerificationPlan plan = profile.verificationPlan();
        String feedback = "";
        int totalAttempts = Math.max(1, plan.maxFixAttempts() + 1);
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            cancellation.check();
            try {
                Object result = agent.execute(job.id(), issue, worktree, attempt, feedback);
                LOGGER.info("Issue worker Agent completed attempt {} with result type {}", attempt,
                        result == null ? "null" : result.getClass().getSimpleName());
            } catch (RuntimeException ex) {
                LOGGER.warn("Issue worker Agent failed before verification", ex);
                return VerificationOutcome.failure(IssueExecutionErrorCode.AGENT_INFRASTRUCTURE_FAILED,
                        "Issue worker Agent invocation failed", true);
            }
            cancellation.check();
            if (attempt == 1) {
                progress.accept(EvolutionJobState.VERIFYING);
            }
            CIGateResult gate = new CIGateRunner(worktree.toString(), plan.commands(), plan.timeout()).run();
            if (gate.isPassed()) {
                return VerificationOutcome.success();
            }
            VerificationFailureType failureType = gate.resolvedFailureType();
            if (failureType.isInfrastructureFailure()) {
                return VerificationOutcome.failure(IssueExecutionErrorCode.CI_INFRASTRUCTURE_FAILED,
                        safeGateError(gate), true);
            }
            feedback = safeGateError(gate);
        }
        return VerificationOutcome.failure(IssueExecutionErrorCode.VERIFICATION_FAILED,
                feedback.isBlank() ? "Java compilation did not pass" : feedback, false);
    }

    private GitOperations localGit(Path worktree) {
        return new GitOperations(
                worktree.toString(),
                coordinates.publishCloneUri().toString(),
                coordinates.baseBranch(),
                coordinates.publishOwner(),
                coordinates.targetOwner(),
                coordinates.targetName(),
                coordinates.publishOwner(),
                DISABLED_LOCAL_GIT_TOKEN,
                config.getGitUserName(),
                config.getGitUserEmail());
    }

    private String pullRequestTitle(GitCodeIssue issue) {
        String title = issue.title() == null ? "" : issue.title().replace('\r', ' ').replace('\n', ' ').strip();
        String value = "[Auto-Evolving Demo] Resolve issue #" + issue.iid() + ": " + title;
        return value.substring(0, Math.min(value.length(), 200));
    }

    private String pullRequestBody(GitCodeIssue issue) {
        return "Automated demo change for " + coordinates.targetRepository() + "#" + issue.iid() + "\n\n"
                + "Source Issue: " + issue.url() + "\n\n"
                + "Verification: `mvn -B -ntp -DskipTests test-compile` (tests were not executed).\n\n"
                + "This PR was created by the gitcode-issue-evolver example and requires human review and merge.";
    }

    private static String safeGateError(CIGateResult gate) {
        String error = gate.getErrors();
        if (error == null || error.isBlank()) {
            error = String.join("\n", Optional.ofNullable(gate.getGateOutputs()).orElse(List.of()));
        }
        String normalized = error == null ? "" : error.strip();
        return normalized.substring(0, Math.min(normalized.length(), 6000));
    }

    private record VerificationOutcome(boolean passed, boolean retryable,
                                       IssueExecutionErrorCode errorCode, String error) {
        private static VerificationOutcome success() {
            return new VerificationOutcome(true, false, IssueExecutionErrorCode.NONE, "");
        }

        private static VerificationOutcome failure(IssueExecutionErrorCode code, String error,
                                                   boolean retryable) {
            return new VerificationOutcome(false, retryable, code, error == null ? "" : error);
        }
    }
}
