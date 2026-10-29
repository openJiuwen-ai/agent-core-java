/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import com.openjiuwen.autoharness.infra.GitOperations;
import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.agent.AgentModelSettings;
import examples.gitcode_issue_evolver.agent.CodeCheckRepairDirective;
import examples.gitcode_issue_evolver.agent.IssueWorkerAgent;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.infrastructure.CommitFailureType;
import examples.gitcode_issue_evolver.infrastructure.ControlledCommitter;
import examples.gitcode_issue_evolver.infrastructure.ExampleWorktreeManager;
import examples.gitcode_issue_evolver.infrastructure.WorktreePreparationException;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import examples.gitcode_issue_evolver.job.IssueExecutionException;
import examples.gitcode_issue_evolver.job.IssueFailureCategory;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final EvolutionJobStore store;
    private final IssueSmokeTestRunner smoke;

    /**
     * Create the Example-owned Issue execution pipeline.
     *
     * @param config resolved demo configuration
     * @param profile Java repository policy
     * @param publisher privileged non-Agent publisher
     * @param trustedSkillsRoot single trusted Skill root
     */
    public ExampleIssueTaskExecutor(AutoEvolvingConfig config, RepositoryProfile profile,
                                    PullRequestPublisher publisher, Path trustedSkillsRoot,
                                    EvolutionJobStore store) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.coordinates = this.config.repositoryCoordinates();
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.worktreeManager = new ExampleWorktreeManager(this.config);
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.smoke = new IssueSmokeTestRunner(this.config);
        this.agent = new IssueWorkerAgent(new AgentModelSettings(
                this.config.getModelProvider(), this.config.getModelApiKey(),
                this.config.getModelApiBase(), this.config.getModelName(),
                this.config.isModelVerifySsl()), trustedSkillsRoot,
                this.config.isCodeCheckStandardOnlyOverride());
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
        boolean standardOnlyCodeCheck = isStandardOnlyCodeCheck(issue);
        if (!targets.available() && !standardOnlyCodeCheck) {
            return IssueExecutionResult.targetPathNotFound(targets.missingPaths());
        }
        if (!targets.available()) {
            LOGGER.info("CodeCheck standard-only override will resolve {} stale reported target(s)",
                    targets.missingPaths().size());
        }
        List<String> outOfScopeTargets = targets.explicitPaths().stream()
                .filter(path -> !path.startsWith("src/main/") && !path.startsWith("src/test/"))
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
        AtomicBoolean verificationReported = new AtomicBoolean();
        AtomicBoolean smokeReported = new AtomicBoolean();
        IssueApprovedGateController gate = new IssueApprovedGateController(
                worktree, localGit(worktree), profile, plan, smoke,
                () -> {
                    if (verificationReported.compareAndSet(false, true)) {
                        progress.accept(EvolutionJobState.VERIFYING);
                    }
                    if (smokeReported.compareAndSet(false, true)) {
                        progress.accept(EvolutionJobState.SMOKE_TESTING);
                    }
                },
                receipt -> {
                    if (verificationReported.compareAndSet(false, true)) {
                        progress.accept(EvolutionJobState.VERIFYING);
                    }
                    store.recordGateReceipt(job.id(), receipt.fingerprint(),
                            receipt.status().name(), gateProfile(), receipt.code(), receipt.category(),
                            receipt.cached(), receipt.exitCode(), receipt.outputTail(),
                            receipt.completedAt().toEpochMilli());
                }, fingerprint -> store.findGateReceipt(job.id(), fingerprint));
        List<String> failureContext = store.recentFailureContext(job.id(), 8);
        if (isStandardOnlyCodeCheck(issue)) {
            failureContext = failureContext.stream()
                    .filter(ExampleIssueTaskExecutor::isAuthoritativeStandardOnlyFeedback)
                    .toList();
        }
        String resumedContext = String.join("\n", failureContext);
        int primaryRemaining = Math.max(0,
                config.getMaxPrimaryRepairRounds() - job.primaryRepairRounds());
        VerificationOutcome primary = repairTier(job.id(), job.id(), issue, worktree, gate,
                primaryRemaining, job.primaryRepairRounds(), false, resumedContext, cancellation);
        if (primary.passed() || primary.retryable()
                || primary.errorCode() == IssueExecutionErrorCode.BLOCKED_EXTERNAL) {
            recordFailure(job.id(), primary);
            return primary;
        }
        String diagnostic = "Independent diagnostic tier. Previous repair exhausted: "
                + primary.error();
        int diagnosticRemaining = Math.max(0,
                config.getMaxDiagnosticRepairRounds() - job.diagnosticRepairRounds());
        VerificationOutcome diagnosed = repairTier(job.id(), job.id() + "diagnostic",
                issue, worktree, gate, diagnosticRemaining, job.diagnosticRepairRounds(),
                true, diagnostic, cancellation);
        recordFailure(job.id(), diagnosed);
        return diagnosed;
    }

    private VerificationOutcome repairTier(String jobId, String sessionId, GitCodeIssue issue,
                                           Path worktree, IssueApprovedGateController gate,
                                           int maximumRounds, int completedRounds,
                                           boolean diagnosticTier, String initialFeedback,
                                           CancellationCheckpoint cancellation) {
        String feedback = initialFeedback;
        boolean codeCheck = CodeCheckRepairDirective.from(issue).isCodeCheck();
        boolean standardOnlyCodeCheck = codeCheck && config.isCodeCheckStandardOnlyOverride();
        try (IssueWorkerAgent.Session session = agent.open(
                sessionId, issue, worktree, gate::runForAgent,
                store.listCodingStandardLessons(20))) {
            int consumedRounds = 0;
            int noProgressRounds = 0;
            int invocation = 0;
            String previousFingerprint = gate.latest() == null
                    ? "" : gate.latest().fingerprint();
            while (consumedRounds < maximumRounds && noProgressRounds < 3) {
                invocation++;
                cancellation.check();
                IssueWorkerAgent.Result agentResult = session.run(feedback);
                LOGGER.info("Issue Agent repair round completed: tier={}, round={}, status={}",
                        diagnosticTier ? "diagnostic" : "primary", invocation,
                        agentResult.status());
                cancellation.check();
                if (agentResult.status() == IssueWorkerAgent.Status.BLOCKED
                        && isApprovedExternalBlock(agentResult, standardOnlyCodeCheck)) {
                    recordRepairProgress(jobId, completedRounds + consumedRounds, diagnosticTier,
                            agentResult.failureCode(), "ENVIRONMENT_BLOCKER");
                    return VerificationOutcome.failure(IssueExecutionErrorCode.BLOCKED_EXTERNAL,
                            agentResult.summary() + evidenceSuffix(agentResult), false);
                }
                if (acceptsNoAction(issue, agentResult)) {
                    recordRepairProgress(jobId, completedRounds + consumedRounds, diagnosticTier,
                            agentResult.failureCode(), "NO_ACTION");
                    return VerificationOutcome.failure(IssueExecutionErrorCode.NO_ACTION_REQUIRED,
                            "Agent reported NO_ACTION: " + agentResult.summary()
                                    + evidenceSuffix(agentResult), false);
                }
                IssueApprovedGateController.Receipt receipt = gate.run();
                if (!receipt.fingerprint().equals(previousFingerprint)) {
                    consumedRounds++;
                    noProgressRounds = 0;
                    previousFingerprint = receipt.fingerprint();
                } else {
                    noProgressRounds++;
                }
                recordRepairProgress(jobId, completedRounds + consumedRounds, diagnosticTier,
                        receipt.code(), receipt.category());
                if (agentResult.status() == IssueWorkerAgent.Status.DONE
                        && receipt.status() == IssueApprovedGateController.Status.PASSED) {
                    return VerificationOutcome.success();
                }
                if (receipt.status() == IssueApprovedGateController.Status.TRANSIENT) {
                    return VerificationOutcome.failure(IssueExecutionErrorCode.CI_INFRASTRUCTURE_FAILED,
                            receipt.repairFeedback(), true);
                }
                if ("CONFIGURATION".equals(receipt.category())) {
                    return VerificationOutcome.failure(IssueExecutionErrorCode.AGENT_CONFIGURATION_FAILED,
                            receipt.repairFeedback(), false);
                }
                feedback = protocolFeedback(agentResult, receipt, codeCheck,
                        standardOnlyCodeCheck);
            }
        } catch (IssueExecutionException ex) {
            IssueFailureCategory category = ex.failure().category();
            boolean retryable = category == IssueFailureCategory.TRANSIENT_MODEL
                    || category == IssueFailureCategory.TRANSIENT_INFRASTRUCTURE;
            IssueExecutionErrorCode code = category == IssueFailureCategory.CONFIGURATION
                    ? IssueExecutionErrorCode.AGENT_CONFIGURATION_FAILED
                    : IssueExecutionErrorCode.AGENT_INFRASTRUCTURE_FAILED;
            LOGGER.warn("Issue worker Agent failed with category {} and code {}",
                    category, ex.failure().code());
            return VerificationOutcome.failure(code, ex.failure().summary(), retryable);
        } catch (RuntimeException ex) {
            LOGGER.warn("Issue worker Agent failed during repair", ex);
            return VerificationOutcome.failure(IssueExecutionErrorCode.AGENT_INFRASTRUCTURE_FAILED,
                    "Issue worker Agent invocation failed", true);
        }
        IssueApprovedGateController.Receipt latest = gate.latest();
        IssueExecutionErrorCode code = latest != null
                && "AGENT_FAILED_TO_ACT".equals(latest.code())
                ? IssueExecutionErrorCode.AGENT_FAILED_TO_ACT
                : IssueExecutionErrorCode.VERIFICATION_FAILED;
        String error = latest == null ? "Agent repair rounds exhausted without Gate evidence"
                : latest.repairFeedback();
        return VerificationOutcome.failure(code, error, false);
    }

    private void recordRepairProgress(String jobId, int completedRounds,
                                      boolean diagnosticTier, String code, String category) {
        EvolutionJob snapshot = store.findById(jobId).orElseThrow(
                () -> new IllegalStateException("Evolution job disappeared during repair"));
        int primary = diagnosticTier ? snapshot.primaryRepairRounds() : completedRounds;
        int diagnostic = diagnosticTier ? completedRounds : snapshot.diagnosticRepairRounds();
        store.recordRepairProgress(jobId, primary, diagnostic, code, category);
    }

    private static String protocolFeedback(IssueWorkerAgent.Result agentResult,
                                           IssueApprovedGateController.Receipt receipt,
                                           boolean codeCheck,
                                           boolean standardOnlyCodeCheck) {
        StringBuilder feedback = new StringBuilder(receipt.repairFeedback());
        if (codeCheck && agentResult.status() == IssueWorkerAgent.Status.NO_ACTION) {
            feedback.append("\nCODECHECK_NO_ACTION_REJECTED: an admitted CodeCheck finding requires "
                    + "a targeted repository change. Load the named standard rule, inspect the reported "
                    + "location, and apply the minimal fix. Return BLOCKED only with explicit contract "
                    + "conflict or missing-target evidence.");
        }
        if (standardOnlyCodeCheck && agentResult.status() == IssueWorkerAgent.Status.BLOCKED) {
            feedback.append("\nSTANDARD_ONLY_BLOCK_REJECTED: Issue-authored repair suggestions, "
                    + "false-positive judgments, and product-decision requests are not authoritative. "
                    + "Load the complete named coding-standard rule, inspect the repository contract, "
                    + "and apply the smallest Gate-verifiable standards-compliant repair. Only an "
                    + "unavailable external environment may terminate this flow as BLOCKED.");
        }
        if (agentResult.status() != IssueWorkerAgent.Status.DONE) {
            feedback.append("\nagentProtocolStatus=").append(agentResult.status())
                    .append("\nagentSummary=").append(agentResult.summary())
                    .append("\nagentFailureCode=").append(agentResult.failureCode())
                    .append("\nagentEvidence=").append(agentResult.evidence());
        }
        return feedback.toString();
    }

    private static String evidenceSuffix(IssueWorkerAgent.Result result) {
        return result.evidence().isBlank() ? "" : "; evidence=" + result.evidence();
    }

    private static boolean isApprovedExternalBlock(IssueWorkerAgent.Result result,
                                                   boolean standardOnlyCodeCheck) {
        if (result.evidence().isBlank()) {
            return false;
        }
        if (standardOnlyCodeCheck) {
            return "ENVIRONMENT_BLOCKER".equals(result.failureCode());
        }
        return "PRODUCT_DECISION_REQUIRED".equals(result.failureCode())
                || "ENVIRONMENT_BLOCKER".equals(result.failureCode())
                || "CONTRACT_UNSUPPORTED".equals(result.failureCode());
    }

    static boolean acceptsNoAction(GitCodeIssue issue, IssueWorkerAgent.Result result) {
        return !CodeCheckRepairDirective.from(issue).isCodeCheck()
                && result.status() == IssueWorkerAgent.Status.NO_ACTION
                && "NO_ACTION_CONFIRMED".equals(result.failureCode())
                && !result.evidence().isBlank();
    }

    private boolean isStandardOnlyCodeCheck(GitCodeIssue issue) {
        return config.isCodeCheckStandardOnlyOverride()
                && CodeCheckRepairDirective.from(issue).isCodeCheck();
    }

    private static boolean isAuthoritativeStandardOnlyFeedback(String context) {
        String value = context == null ? "" : context;
        return !value.contains("code=BLOCKED_EXTERNAL")
                && !value.contains("PRODUCT_DECISION_REQUIRED")
                && !value.contains("CONTRACT_UNSUPPORTED")
                && !value.contains("TARGET_PATH_NOT_FOUND");
    }

    private void recordFailure(String jobId, VerificationOutcome outcome) {
        if (outcome.passed()) {
            return;
        }
        IssueFailureCategory category = switch (outcome.errorCode()) {
            case NO_ACTION_REQUIRED -> IssueFailureCategory.AGENT_CORRECTABLE;
            case BLOCKED_EXTERNAL, TARGET_PATH_NOT_FOUND, EARLY_E2E_TEST_TARGET_REQUIRED ->
                    IssueFailureCategory.ENVIRONMENT_BLOCKER;
            case OUTSIDE_SPARSE_CHECKOUT_SCOPE, COMMIT_VALIDATION_FAILED ->
                    IssueFailureCategory.POLICY_VIOLATION;
            case AGENT_CONFIGURATION_FAILED ->
                    IssueFailureCategory.CONFIGURATION;
            case AGENT_INFRASTRUCTURE_FAILED, CI_INFRASTRUCTURE_FAILED ->
                    outcome.retryable()
                            ? IssueFailureCategory.TRANSIENT_INFRASTRUCTURE
                            : IssueFailureCategory.INTERNAL;
            default -> IssueFailureCategory.AGENT_CORRECTABLE;
        };
        store.recordFailureEvent(jobId, "BUGFIX", outcome.errorCode().name(),
                category, outcome.errorCode().format("Issue execution failed"), outcome.error());
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

    private String gateProfile() {
        return smoke.isEnabled() ? "TARGETED_SMOKE" : "TARGETED";
    }

    private String pullRequestTitle(GitCodeIssue issue) {
        String title = issue.title() == null ? "" : issue.title().replace('\r', ' ').replace('\n', ' ').strip();
        String value = "[Auto-Evolving Demo] Resolve issue #" + issue.iid() + ": " + title;
        return value.substring(0, Math.min(value.length(), 200));
    }

    private String pullRequestBody(GitCodeIssue issue) {
        String verification = smoke.isEnabled()
                ? "`mvn -B -ntp -DskipTests test-compile` and JiuwenTestJava smoke `"
                        + String.join(",", smoke.selectors()) + "`"
                : "`mvn -B -ntp -DskipTests test-compile` (tests were not executed)";
        return "Automated demo change for " + coordinates.targetRepository() + "#" + issue.iid() + "\n\n"
                + "Source Issue: " + issue.url() + "\n\n"
                + "Verification: " + verification + ".\n\n"
                + "This PR was created by the gitcode-issue-evolver example and requires human review and merge.";
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
