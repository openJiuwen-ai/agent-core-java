/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.agent.FeaturePathPolicy;
import examples.gitcode_feature_evolver.agent.FeatureStageAgent;
import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureIssue;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.infrastructure.ContainerGateResult;
import examples.gitcode_feature_evolver.infrastructure.DependencyPrefetcher;
import examples.gitcode_feature_evolver.infrastructure.FeatureGitPublisher;
import examples.gitcode_feature_evolver.infrastructure.FeatureWorktreeManager;
import examples.gitcode_feature_evolver.infrastructure.RootlessContainerGateRunner;
import examples.gitcode_feature_evolver.infrastructure.SystemTestWorktreeManager;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.ApprovedGateReceipt;
import examples.gitcode_feature_evolver.job.FeatureExecutionException;
import examples.gitcode_feature_evolver.job.FeatureFailure;
import examples.gitcode_feature_evolver.job.FeatureFailureCategory;
import examples.gitcode_feature_evolver.job.FeatureFailureEvent;
import examples.gitcode_feature_evolver.job.FeatureJobMutation;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.publish.FeaturePullRequestPublisher;
import examples.gitcode_feature_evolver.publish.SystemTestPullRequestPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/**
 * Executes one persisted Feature DevFlow state with service-owned Git, PR, and container gates.
 *
 * @since 0.1.12
 */
public final class FeatureStageExecutor implements FeatureStageRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureStageExecutor.class);
    private final FeatureEvolvingConfig config;
    private final FeatureJobStore store;
    private final FeatureStageAgent agent;
    private final FeatureWorktreeManager worktrees;
    private final RootlessContainerGateRunner container;
    private final FeatureGitPublisher gitPublisher;
    private final DependencyPrefetcher dependencyPrefetcher;
    private final FeaturePullRequestPublisher pullRequests;
    private final SystemTestWorktreeManager systemTestWorktrees;
    private final SystemTestPullRequestPublisher systemTestPullRequests;

    /**
     * Create the complete bounded stage executor.
     *
     * @param config validated feature configuration
     * @param agent restricted stage Agent
     * @param infrastructure trusted Worktree, container, Git, and PR boundaries
     */
    public FeatureStageExecutor(FeatureEvolvingConfig config, FeatureJobStore store,
                                FeatureStageAgent agent, Infrastructure infrastructure) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
        Infrastructure required = Objects.requireNonNull(infrastructure,
                "infrastructure must not be null");
        this.worktrees = required.worktrees();
        this.container = required.container();
        this.gitPublisher = required.gitPublisher();
        this.dependencyPrefetcher = new DependencyPrefetcher(config);
        this.pullRequests = required.pullRequests();
        this.systemTestWorktrees = required.systemTests().worktrees();
        this.systemTestPullRequests = required.systemTests().pullRequests();
    }

    /**
     * Execute exactly one current durable stage.
     *
     * @param request trusted execution request and cancellation checkpoint
     * @return next mutation and optional canonical PR update
     */
    @Override
    public FeatureStageOutcome execute(ExecutionRequest request) {
        ExecutionRequest required = Objects.requireNonNull(request, "request must not be null");
        FeatureJob job = required.job();
        FeatureStage stage = job.progress().stage();
        if (stage == FeatureStage.RETRY_SCHEDULED) {
            return restoreRetry(job);
        }
        if (stage == FeatureStage.DEPENDENCY_PREFETCH) {
            return dependencyPrefetch(required);
        }
        if (stage == FeatureStage.CANCEL_REQUESTED) {
            return transition(job, FeatureStage.CANCELLED,
                    job.progress().gateRound(), job.progress().taskAttempt(), "Cancellation completed");
        }
        if (isSystemTestStage(stage)) {
            return executeSystemTestStage(required);
        }
        StageContext context = context(required);
        return switch (stage) {
            case ADMITTED -> transition(job, FeatureStage.SPECIFY, 0, 0, "Worktree prepared");
            case SPECIFY -> specify(context);
            case REVIEW_R1 -> reviewR1(context);
            case CREATE_DRAFT_PR -> createDraftPullRequest(context);
            case DESIGN -> design(context);
            case REVIEW_R2 -> reviewR2(context);
            case IMPLEMENT_RED -> red(context);
            case IMPLEMENT_GREEN -> green(context);
            case IMPLEMENT_REFACTOR -> refactor(context);
            case PUBLISH_TASK -> publishTask(context);
            case REVIEW_R3 -> reviewR3(context);
            case SHIP -> ship(context);
            case RETRY_SCHEDULED, DEPENDENCY_PREFETCH, CANCEL_REQUESTED -> throw new IllegalStateException(
                    "Preprocessed feature stage reached the stage dispatcher");
            default -> waitingState(job);
        };
    }

    private FeatureStageOutcome executeSystemTestStage(ExecutionRequest request) {
        if (!config.systemTestEnabled()) {
            return configurationFailure(request.job(),
                    "System-test stage is disabled by the current configuration");
        }
        SystemTestContext context = systemTestContext(request);
        return switch (request.job().progress().stage()) {
            case SYSTEM_TEST -> systemTest(context);
            case REVIEW_SYSTEM_TEST -> reviewSystemTest(context);
            case PUBLISH_SYSTEM_TEST -> publishSystemTest(context);
            default -> throw new IllegalStateException("Unsupported system-test stage");
        };
    }

    private FeatureStageOutcome dependencyPrefetch(ExecutionRequest request) {
        FeatureJob job = request.job();
        FeatureStage resume = job.progress().resumeStage();
        if (resume == null) {
            return transition(job, FeatureStage.FAILED_INTERNAL,
                    job.progress().gateRound(), job.progress().taskAttempt(),
                    "Dependency prefetch has no recovery stage");
        }
        int round = job.recovery().retries().dependencyPrefetchRounds() + 1;
        if (round > config.maxDependencyPrefetchRounds()) {
            throw new FeatureExecutionException(new FeatureFailure(
                    "DEPENDENCY_PREFETCH_EXHAUSTED", FeatureFailureCategory.DEPENDENCY_MISSING,
                    FeatureStage.DEPENDENCY_PREFETCH, resume,
                    new FeatureFailure.Diagnostic(
                    "Automatic dependency prefetch budget exhausted", "")));
        }
        request.cancellation().check();
        DependencyPrefetcher.Result result = isSystemTestStage(resume)
                ? prefetchSystemTest(request) : prefetchFeature(request);
        recordPrefetch(job, resume, round, result);
        if (result.status() == DependencyPrefetcher.Status.POLICY_VIOLATION) {
            restorePrefetchPolicy(job, resume);
        }
        return prefetchOutcome(job, resume, result);
    }

    private void restorePrefetchPolicy(FeatureJob job, FeatureStage resume) {
        if (isSystemTestStage(resume)) {
            FeatureWorktreeManager.PreparedMergedSource source = worktrees.prepareMergedSource(job);
            SystemTestWorktreeManager.PreparedSystemTestWorktree tests =
                    systemTestWorktrees.prepare(job);
            restorePolicySnapshot(source.path(), false, FeatureStage.DEPENDENCY_PREFETCH);
            restorePolicySnapshot(tests.path(), true, FeatureStage.DEPENDENCY_PREFETCH);
            return;
        }
        FeatureWorktreeManager.PreparedWorktree feature = worktrees.prepare(job);
        restorePolicySnapshot(feature.path(), false, FeatureStage.DEPENDENCY_PREFETCH);
    }

    private DependencyPrefetcher.Result prefetchFeature(ExecutionRequest request) {
        FeatureWorktreeManager.PreparedWorktree prepared = worktrees.prepare(request.job());
        List<String> dirty = gitPublisher.dirtyFiles(prepared.path());
        return dependencyPrefetcher.prefetchFeature(request.job(), prepared.path(), dirty);
    }

    private DependencyPrefetcher.Result prefetchSystemTest(ExecutionRequest request) {
        FeatureWorktreeManager.PreparedMergedSource source =
                worktrees.prepareMergedSource(request.job());
        SystemTestWorktreeManager.PreparedSystemTestWorktree tests =
                systemTestWorktrees.prepare(request.job());
        List<String> dirty = new java.util.ArrayList<>(
                gitPublisher.dirtyFiles(source.path()));
        dirty.addAll(gitPublisher.systemTestChangedFiles(tests.path()));
        return dependencyPrefetcher.prefetchSystemTest(request.job(), source.path(),
                tests.path(), dirty);
    }

    private void recordPrefetch(FeatureJob job, FeatureStage resume, int round,
                                DependencyPrefetcher.Result result) {
        FeatureFailureEvent.RepairAttempt attempt =
                new FeatureFailureEvent.RepairAttempt("PREFETCH", round);
        if (result.status() == DependencyPrefetcher.Status.PASSED) {
            store.recordRecoveryProgress(job.identity().id(), job.record().version(),
                    attempt, result.summary());
            return;
        }
        FeatureFailureCategory category = switch (result.status()) {
            case DEPENDENCY_UNAVAILABLE -> FeatureFailureCategory.DEPENDENCY_MISSING;
            case BUILD_CONTRACT_INVALID -> FeatureFailureCategory.CONFIGURATION;
            case TRANSIENT -> FeatureFailureCategory.TRANSIENT_INFRASTRUCTURE;
            case POLICY_VIOLATION -> FeatureFailureCategory.POLICY_VIOLATION;
            case PASSED -> throw new IllegalStateException("Passed prefetch was not short-circuited");
        };
        FeatureFailure failure = new FeatureFailure("DEPENDENCY_PREFETCH_" + result.status(),
                category, FeatureStage.DEPENDENCY_PREFETCH, resume,
                new FeatureFailure.Diagnostic(result.summary(), ""));
        store.recordFailure(job.identity().id(), job.record().version(), failure, attempt, 0L);
    }

    private FeatureStageOutcome prefetchOutcome(FeatureJob job, FeatureStage resume,
                                                DependencyPrefetcher.Result result) {
        return switch (result.status()) {
            case PASSED -> transition(job, resume, job.progress().gateRound(),
                    job.progress().taskAttempt(), "Dependency prefetch completed");
            case DEPENDENCY_UNAVAILABLE -> throw new FeatureExecutionException(
                    new FeatureFailure("DEPENDENCY_UNAVAILABLE",
                            FeatureFailureCategory.DEPENDENCY_MISSING,
                            FeatureStage.DEPENDENCY_PREFETCH, resume,
                            new FeatureFailure.Diagnostic(result.summary(), "")));
            case BUILD_CONTRACT_INVALID -> throw new FeatureExecutionException(
                    new FeatureFailure("DEPENDENCY_PREFETCH_BUILD_CONTRACT_INVALID",
                            FeatureFailureCategory.CONFIGURATION,
                            FeatureStage.DEPENDENCY_PREFETCH, null,
                            new FeatureFailure.Diagnostic(result.summary(), "")));
            case POLICY_VIOLATION -> throw new FeatureExecutionException(
                    new FeatureFailure("DEPENDENCY_PREFETCH_POLICY_VIOLATION",
                            FeatureFailureCategory.POLICY_VIOLATION,
                            FeatureStage.DEPENDENCY_PREFETCH, null,
                            new FeatureFailure.Diagnostic(result.summary(), "")));
            case TRANSIENT -> throw new FeatureExecutionException(new FeatureFailure(
                    "DEPENDENCY_PREFETCH_TRANSIENT",
                    FeatureFailureCategory.TRANSIENT_INFRASTRUCTURE,
                    FeatureStage.DEPENDENCY_PREFETCH, resume,
                    new FeatureFailure.Diagnostic(result.summary(), "")));
        };
    }

    private Path gateCache(FeatureJob job) {
        Path isolated = dependencyPrefetcher.cacheFor(job);
        return java.nio.file.Files.isDirectory(isolated)
                ? isolated : config.containerMavenCache();
    }

    private SystemTestContext systemTestContext(ExecutionRequest request) {
        request.cancellation().check();
        FeatureWorktreeManager.PreparedMergedSource source =
                worktrees.prepareMergedSource(request.job());
        SystemTestWorktreeManager.PreparedSystemTestWorktree tests =
                systemTestWorktrees.prepare(request.job());
        request.cancellation().check();
        SystemTestArtifactInspector inspector = new SystemTestArtifactInspector(
                tests.path(), request.job(), config.systemTestWriteScopes(), source.revision());
        return new SystemTestContext(request.job(), new IssueData(request.issue(), request.comments()),
                source.path(), source.revision(), tests.path(), tests.branch(), inspector,
                request.cancellation());
    }

    private StageContext context(ExecutionRequest request) {
        FeatureJob requiredJob = request.job();
        request.cancellation().check();
        FeatureWorktreeManager.PreparedWorktree prepared = worktrees.prepare(requiredJob);
        request.cancellation().check();
        FeatureArtifactInspector inspector = new FeatureArtifactInspector(
                prepared.path(), requiredJob, config.componentRoot());
        IssueData issueData = new IssueData(request.issue(), request.comments());
        return new StageContext(requiredJob, issueData, prepared.path(), inspector,
                request.cancellation());
    }

    private FeatureStageOutcome specify(StageContext context) {
        AgentScope scope = AgentScope.same(context.inspector().artifactWriteScope(), "N/A");
        AgentInvocation invocation = invoke(context, FeatureStage.SPECIFY, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        List<String> errors = context.inspector().validateArtifacts(FeatureStage.SPECIFY);
        if (!errors.isEmpty()) {
            return retryAgentStage(context.job(), FeatureStage.SPECIFY,
                    "Specification artifacts failed validation: " + String.join(", ", errors));
        }
        return transition(context.job(), FeatureStage.REVIEW_R1, reviewRound(context.job()), 0,
                "Specification artifacts ready for independent R1 review");
    }

    private FeatureStageOutcome reviewR1(StageContext context) {
        int round = reviewRound(context.job());
        String reviewPath = context.inspector().reviewPath(FeatureStage.REVIEW_R1, round);
        AgentScope scope = new AgentScope(List.of(reviewPath),
                context.inspector().artifactWriteScope(), context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(context, FeatureStage.REVIEW_R1, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        FeatureArtifactInspector.Verdict verdict = context.inspector().verdict(reviewPath);
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            return reviewRework(context.job(), FeatureStage.SPECIFY, FeatureStage.REVIEW_R1,
                    round, "R1 requested specification rework");
        }
        return transition(context.job(), FeatureStage.CREATE_DRAFT_PR, round, 0,
                "R1 passed; create or reconcile the long-lived Draft PR");
    }

    private FeatureStageOutcome createDraftPullRequest(StageContext context) {
        FeatureStage next = FeatureStage.DESIGN;
        Publication publication = publish(context, context.inspector().artifactWriteScope(),
                "docs(devflow): complete R1 artifacts", next, false);
        if (!publication.success()) {
            return publication.failure().orElseThrow();
        }
        FeatureJobMutation mutation = new FeatureJobMutation(next, null,
                0, 0,
                "Long-lived Draft PR created or reconciled");
        return new FeatureStageOutcome(mutation, Optional.of(publication.binding().orElseThrow()));
    }

    private FeatureStageOutcome design(StageContext context) {
        AgentScope scope = AgentScope.same(context.inspector().artifactWriteScope(),
                context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(context, FeatureStage.DESIGN, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        List<String> errors = context.inspector().validateArtifacts(FeatureStage.DESIGN);
        if (!errors.isEmpty()) {
            return retryAgentStage(context.job(), FeatureStage.DESIGN,
                    "Design artifacts failed validation: " + String.join(", ", errors));
        }
        try {
            context.inspector().implementationScopes();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return retryAgentStage(context.job(), FeatureStage.DESIGN, ex.getMessage());
        }
        return transition(context.job(), FeatureStage.REVIEW_R2, reviewRound(context.job()), 0,
                "Design ready for independent R2 review");
    }

    private FeatureStageOutcome reviewR2(StageContext context) {
        int round = reviewRound(context.job());
        String reviewPath = context.inspector().reviewPath(FeatureStage.REVIEW_R2, round);
        AgentScope scope = new AgentScope(List.of(reviewPath),
                context.inspector().artifactWriteScope(), context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(context, FeatureStage.REVIEW_R2, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        FeatureArtifactInspector.Verdict verdict = context.inspector().verdict(reviewPath);
        if (verdict == FeatureArtifactInspector.Verdict.PASS) {
            try {
                context.inspector().validateVerificationPlan();
            } catch (IllegalStateException ex) {
                return retryAgentStage(context.job(), FeatureStage.DESIGN, ex.getMessage());
            }
        }
        FeatureStage next = r2NextStage(context.job(), verdict, round);
        Publication publication = publish(context, context.inspector().artifactWriteScope(),
                "docs(devflow): record R2 review", next, false);
        if (!publication.success()) {
            return publication.failure().orElseThrow();
        }
        FeatureJobMutation mutation = reviewMutation(context.job(), verdict, next, round, "R2");
        return new FeatureStageOutcome(mutation, Optional.of(publication.binding().orElseThrow()));
    }

    private FeatureStageOutcome red(StageContext context) {
        List<String> scopes = context.inspector().tddWriteScopes(FeatureStage.IMPLEMENT_RED);
        Optional<FeatureStageOutcome> retryRestore = restoreRedRetry(context, scopes);
        if (retryRestore.isPresent()) {
            return retryRestore.orElseThrow();
        }
        FeatureArtifactInspector.PlanCursor task = context.inspector().nextTask();
        if (task.complete() && context.job().progress().gateRound() <= 1) {
            return transition(context.job(), FeatureStage.REVIEW_R3, 1, 0,
                    "All planned tasks completed");
        }
        Optional<VerificationSelection> verification;
        try {
            verification = redSelection(context.inspector(), task);
        } catch (IllegalStateException ex) {
            return retryAgentStage(context.job(), FeatureStage.DESIGN,
                    "RED selector contract is invalid: " + ex.getMessage());
        }
        BaselineVerification baseline = verifyGreenBaseline(context);
        if (baseline.failure().isPresent()) {
            return baseline.failure().orElseThrow();
        }
        AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(context, FeatureStage.IMPLEMENT_RED, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        return verifyRed(context, task, verification, baseline.gate(), invocation);
    }

    private Optional<FeatureStageOutcome> restoreRedRetry(
            StageContext context, List<String> scopes) {
        if (context.job().progress().taskAttempt() == 0) {
            return Optional.empty();
        }
        FeatureGitPublisher.RestoreResult restored = gitPublisher.restoreRetrySnapshot(
                context.worktree(), scopes);
        if (restored.success()) {
            if (!restored.restoredFiles().isEmpty()) {
                LOGGER.info("Restored {} path(s) before retrying IMPLEMENT_RED for Issue #{}",
                        restored.restoredFiles().size(),
                        context.job().identity().issue().iid());
            }
            return Optional.empty();
        }
        if (restored.retryable()) {
            return Optional.of(publicationFailure(context.job(), FeatureStage.IMPLEMENT_RED,
                    restored.error(), true));
        }
        return Optional.of(failedInternal(context.job(), restored.error()));
    }

    private FeatureStageOutcome verifyRed(
            StageContext context, FeatureArtifactInspector.PlanCursor task,
            Optional<VerificationSelection> expected, ContainerGateResult baseline,
            AgentInvocation invocation) {
        FeatureArtifactInspector.PlanCursor selectedTask = task;
        VerificationSelection verification;
        if (task.complete()) {
            selectedTask = context.inspector().nextTask();
            if (selectedTask.complete()) {
                return retryAgentStage(context.job(), FeatureStage.IMPLEMENT_RED,
                        "R3 rework did not create a bounded plan task");
            }
            try {
                verification = verificationSelection(context.inspector(), selectedTask.taskId(),
                        FeatureArtifactInspector.TestPhase.RED);
            } catch (IllegalStateException ex) {
                return retryAgentStage(context.job(), FeatureStage.IMPLEMENT_RED,
                        ex.getMessage());
            }
        } else {
            verification = expected.orElseThrow();
            if (!verification.contract().equals(
                    context.inspector().verificationContract(selectedTask.taskId()))) {
                return failedPolicy(context,
                        "Agent changed the R2-approved test selector contract during RED");
            }
        }
        ContainerGateResult gate = receiptResult(invocation.receipt().orElseThrow(), true);
        context.cancellation().check();
        if (gate.expectedRed()) {
            context.inspector().recordTaskStatus(selectedTask.taskId(), "red");
            context.inspector().appendEvidence(
                    "BASELINE " + selectedTask.taskId(), baseline, "pending");
            context.inspector().appendEvidence("RED " + selectedTask.taskId(), gate, "pending");
            return transition(context.job(), FeatureStage.IMPLEMENT_GREEN,
                    context.job().progress().gateRound(), 0, "Trustworthy RED captured");
        }
        return failedGate(context.job(), FeatureStage.IMPLEMENT_RED, gate,
                "RED did not produce the expected test failure");
    }

    private static Optional<VerificationSelection> redSelection(
            FeatureArtifactInspector inspector, FeatureArtifactInspector.PlanCursor task) {
        if (task.complete()) {
            return Optional.empty();
        }
        return Optional.of(verificationSelection(inspector, task.taskId(),
                FeatureArtifactInspector.TestPhase.RED));
    }

    private BaselineVerification verifyGreenBaseline(StageContext context) {
        ContainerGateResult baseline = container.run(
                RootlessContainerGateRunner.Profile.BASELINE, context.worktree(), List.of(),
                gateCache(context.job()));
        context.cancellation().check();
        if (baseline.passed()) {
            List<String> dirty = gitPublisher.dirtyFiles(context.worktree());
            if (!dirty.isEmpty()) {
                return BaselineVerification.failed(baseline, failedInternal(context.job(),
                        "Pre-RED verification left Worktree changes: "
                                + String.join(", ", dirty)));
            }
            return BaselineVerification.passed(baseline);
        }
        if (baseline.outcome() == ContainerGateResult.Outcome.DEPENDENCY_MISSING) {
            return BaselineVerification.failed(baseline, failedGate(context.job(),
                    FeatureStage.IMPLEMENT_RED, baseline,
                    "Pre-RED baseline dependency is unavailable"));
        }
        if (baseline.outcome() == ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED
                || baseline.outcome() == ContainerGateResult.Outcome.TIMED_OUT) {
            return BaselineVerification.failed(baseline, publicationFailure(context.job(),
                    FeatureStage.IMPLEMENT_RED,
                    "Pre-RED baseline infrastructure failed: " + baseline.outcome(), true));
        }
        return BaselineVerification.failed(baseline, blockedExternal(context.job(),
                FeatureStage.IMPLEMENT_RED,
                "Pre-RED baseline is not green: " + baseline.outcome()));
    }

    private FeatureStageOutcome green(StageContext context) {
        List<String> scopes = context.inspector().tddWriteScopes(FeatureStage.IMPLEMENT_GREEN);
        FeatureArtifactInspector.PlanCursor task = context.inspector().nextTask();
        VerificationSelection verification;
        try {
            verification = verificationSelection(context.inspector(), task.taskId(),
                    FeatureArtifactInspector.TestPhase.GREEN);
        } catch (IllegalStateException ex) {
            return failedAutomation(context.job(), ex.getMessage());
        }
        AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(context, FeatureStage.IMPLEMENT_GREEN, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        if (!verification.contract().equals(
                context.inspector().verificationContract(task.taskId()))) {
            return failedPolicy(context,
                    "Agent changed the R2-approved test selector contract during GREEN");
        }
        ContainerGateResult gate = receiptResult(invocation.receipt().orElseThrow(), false);
        context.cancellation().check();
        if (gate.passed()) {
            context.inspector().recordTaskStatus(task.taskId(), "green");
            context.inspector().appendEvidence("GREEN " + task.taskId(), gate, "pending");
            return transition(context.job(), FeatureStage.IMPLEMENT_REFACTOR,
                    context.job().progress().gateRound(), 0, "GREEN targeted tests passed");
        }
        return failedGate(context.job(), FeatureStage.IMPLEMENT_GREEN, gate,
                "GREEN verification did not pass");
    }

    private FeatureStageOutcome refactor(StageContext context) {
        List<String> scopes = context.inspector().tddWriteScopes(FeatureStage.IMPLEMENT_REFACTOR);
        FeatureArtifactInspector.PlanCursor before = context.inspector().nextTask();
        String completedTask;
        VerificationSelection verification;
        ApprovedGateReceipt receipt;
        if (!before.complete() && List.of("red", "green", "refactor")
                .contains(before.status())) {
            try {
                verification = verificationSelection(context.inspector(), before.taskId(),
                        FeatureArtifactInspector.TestPhase.REFACTOR);
            } catch (IllegalStateException ex) {
                return failedAutomation(context.job(), ex.getMessage());
            }
            context.inspector().recordTaskStatus(before.taskId(), "refactor");
            AgentInvocation invocation = invokeRefactorAgent(
                    context, scopes, before, verification);
            if (invocation.failure().isPresent()) {
                return invocation.failure().orElseThrow();
            }
            receipt = invocation.receipt().orElseThrow();
            completedTask = before.taskId();
        } else {
            Optional<String> latestDone = context.inspector().lastDoneTaskId();
            if (latestDone.isEmpty()) {
                return failedAutomation(context.job(),
                        "REFACTOR recovery cannot identify the completed task");
            }
            completedTask = latestDone.orElseThrow();
            try {
                verification = verificationSelection(context.inspector(), completedTask,
                        FeatureArtifactInspector.TestPhase.REFACTOR);
            } catch (IllegalStateException ex) {
                return failedAutomation(context.job(), ex.getMessage());
            }
            AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
            receipt = featureGate(context, FeatureStage.IMPLEMENT_REFACTOR, scope).get();
        }
        context.inspector().recordTaskStatus(completedTask, "refactor");
        ContainerGateResult gate = receiptResult(receipt, false);
        context.cancellation().check();
        if (!gate.passed()) {
            return failedGate(context.job(), FeatureStage.IMPLEMENT_REFACTOR, gate,
                    "REFACTOR verification did not pass");
        }
        context.inspector().recordTaskStatus(completedTask, "done");
        context.inspector().appendEvidence("REFACTOR " + completedTask, gate, "pending");
        return transition(context.job(), FeatureStage.PUBLISH_TASK,
                context.job().progress().gateRound(), 0,
                "Verified task is ready for controlled publication");
    }

    private AgentInvocation invokeRefactorAgent(
            StageContext context, List<String> scopes,
            FeatureArtifactInspector.PlanCursor before,
            VerificationSelection verification) {
        AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(
                context, FeatureStage.IMPLEMENT_REFACTOR, scope);
        if (invocation.failure().isPresent()) {
            return invocation;
        }
        FeatureArtifactInspector.PlanCursor after = context.inspector().nextTask();
        Optional<String> latestDone = context.inspector().lastDoneTaskId();
        boolean taskRemains = !after.complete() && before.taskId().equals(after.taskId())
                && List.of("green", "refactor").contains(after.status());
        boolean taskWasMarkedDone = latestDone.isPresent()
                && before.taskId().equals(latestDone.orElseThrow());
        if (!taskRemains && !taskWasMarkedDone) {
            return new AgentInvocation(Optional.of(retryAgentStage(context.job(),
                    FeatureStage.IMPLEMENT_REFACTOR,
                    "Agent changed the active TDD task unexpectedly in plan.md")),
                    invocation.receipt());
        }
        if (!verification.contract().equals(
                context.inspector().verificationContract(before.taskId()))) {
            return new AgentInvocation(Optional.of(failedPolicy(context,
                    "Agent changed the R2-approved test selector contract during REFACTOR")),
                    invocation.receipt());
        }
        return invocation;
    }

    private FeatureStageOutcome publishTask(StageContext context) {
        Optional<String> completed = context.inspector().lastDoneTaskId();
        if (completed.isEmpty()) {
            return failedAutomation(context.job(),
                    "Task publication cannot identify the verified completed task");
        }
        List<String> scopes = context.inspector().tddWriteScopes(FeatureStage.IMPLEMENT_REFACTOR);
        return publishCompletedTask(context, scopes, completed.orElseThrow());
    }

    private FeatureStageOutcome publishCompletedTask(StageContext context, List<String> scopes,
                                                     String taskId) {
        boolean hasPendingTddTask;
        try {
            hasPendingTddTask = context.inspector().hasPendingTddTask();
        } catch (IllegalStateException ex) {
            return failedAutomation(context.job(), ex.getMessage());
        }
        FeatureGitPublisher.Result codeCommit = gitPublisher.commitAndPush(
                context.job(), context.worktree(), scopes,
                "feat: complete " + taskId + " for issue #"
                        + context.job().identity().issue().iid());
        context.cancellation().check();
        if (!codeCommit.success()) {
            return publicationFailure(context.job(), FeatureStage.PUBLISH_TASK,
                    codeCommit.error(), codeCommit.retryable());
        }
        ContainerGateResult anchor = new ContainerGateResult(ContainerGateResult.Outcome.PASSED,
                0, "Controller recorded verified task commit", List.of());
        context.inspector().appendEvidence("TASK_COMMIT " + taskId, anchor, codeCommit.headSha());
        FeatureStage next = hasPendingTddTask
                ? FeatureStage.IMPLEMENT_RED : FeatureStage.REVIEW_R3;
        int round = hasPendingTddTask ? context.job().progress().gateRound()
                : Math.max(1, context.job().progress().gateRound());
        Publication publication = publish(context, context.inspector().artifactWriteScope(),
                "docs(devflow): record task verification evidence", next, false);
        if (!publication.success()) {
            return publication.failure().orElseThrow();
        }
        FeatureJobMutation mutation = new FeatureJobMutation(next, null, round, 0,
                hasPendingTddTask ? "Advance to the next source TDD task"
                        : "Source implementation tasks complete");
        return new FeatureStageOutcome(mutation, Optional.of(publication.binding().orElseThrow()));
    }

    private FeatureStageOutcome reviewR3(StageContext context) {
        int round = reviewRound(context.job());
        String reviewPath = context.inspector().reviewPath(FeatureStage.REVIEW_R3, round);
        AgentScope scope = new AgentScope(List.of(reviewPath),
                context.inspector().artifactWriteScope(), context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(context, FeatureStage.REVIEW_R3, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        FeatureArtifactInspector.Verdict verdict = context.inspector().verdict(reviewPath);
        FeatureStage next = r3NextStage(context.job(), verdict, round);
        Publication publication = publish(context, context.inspector().artifactWriteScope(),
                "docs(devflow): record R3 review", next, false);
        if (!publication.success()) {
            return publication.failure().orElseThrow();
        }
        FeatureJobMutation mutation = reviewMutation(context.job(), verdict, next, round, "R3");
        return new FeatureStageOutcome(mutation, Optional.of(publication.binding().orElseThrow()));
    }

    private FeatureStageOutcome ship(StageContext context) {
        List<String> scopes = context.inspector().shipWriteScopes();
        List<String> testSelectors = context.inspector().allTestSelectors();
        if (testSelectors.isEmpty()) {
            return failedAutomation(context.job(),
                    "Final targeted verification has no approved test selectors");
        }
        AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
        AgentInvocation invocation = invoke(context, FeatureStage.SHIP, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        List<String> errors = context.inspector().validateArtifacts(FeatureStage.SHIP);
        if (!errors.isEmpty()) {
            return retryAgentStage(context.job(), FeatureStage.SHIP,
                    "SHIP artifacts failed validation: " + String.join(", ", errors));
        }
        if (!testSelectors.equals(context.inspector().allTestSelectors())) {
            return failedPolicy(context,
                    "Agent changed the R2-approved test selectors during SHIP");
        }
        ContainerGateResult gate = receiptResult(invocation.receipt().orElseThrow(), false);
        context.cancellation().check();
        if (!gate.passed()) {
            return failedGate(context.job(), FeatureStage.SHIP, gate,
                    "Final SHIP targeted verification did not pass");
        }
        context.inspector().appendEvidence("FINAL", gate, "pending");
        return publishShip(context, scopes);
    }

    private FeatureStageOutcome systemTest(SystemTestContext context) {
        AgentScope scope = AgentScope.same(context.inspector().authorWriteScopes(),
                context.inspector().currentEvidence());
        AgentInvocation invocation = invokeSystemTest(
                context, FeatureStage.SYSTEM_TEST, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        List<String> dirty = gitPublisher.systemTestChangedFiles(context.testWorktree());
        SystemTestArtifactInspector.Validation validation =
                context.inspector().validateAuthor(dirty);
        if (!validation.valid()) {
            return retryAgentStage(context.job(), FeatureStage.SYSTEM_TEST,
                    "System-test output failed validation: "
                            + String.join(", ", validation.errors()));
        }
        ContainerGateResult selected = receiptResult(invocation.receipt().orElseThrow(), false);
        context.cancellation().check();
        if (!selected.passed()) {
            return failedGate(context.job(), FeatureStage.SYSTEM_TEST, selected,
                    "Configured smoke and new post-merge system tests did not pass");
        }
        context.inspector().appendEvidence("SELECTED_SYSTEM_TEST", selected, "pending");
        return transition(context.job(), FeatureStage.REVIEW_SYSTEM_TEST,
                reviewRound(context.job()), 0,
                "Post-merge system tests are ready for independent review");
    }

    private FeatureStageOutcome reviewSystemTest(SystemTestContext context) {
        int round = reviewRound(context.job());
        String reviewPath = context.inspector().reviewPath(round);
        AgentScope scope = new AgentScope(List.of(reviewPath),
                context.inspector().authorWriteScopes(), context.inspector().currentEvidence());
        AgentInvocation invocation = invokeSystemTest(
                context, FeatureStage.REVIEW_SYSTEM_TEST, scope);
        if (invocation.failure().isPresent()) {
            return invocation.failure().orElseThrow();
        }
        FeatureArtifactInspector.Verdict verdict = context.inspector().verdict(reviewPath);
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            return reviewRework(context.job(), FeatureStage.SYSTEM_TEST,
                    FeatureStage.REVIEW_SYSTEM_TEST, round,
                    "System-test review requested rework");
        }
        return transition(context.job(), FeatureStage.PUBLISH_SYSTEM_TEST, 0, 0,
                "System-test review passed; publish the separate test PR");
    }

    private FeatureStageOutcome publishSystemTest(SystemTestContext context) {
        List<String> dirty = gitPublisher.systemTestChangedFiles(context.testWorktree());
        SystemTestArtifactInspector.Validation validation =
                context.inspector().validateAuthor(dirty);
        if (!validation.valid()) {
            return retryAgentStage(context.job(), FeatureStage.SYSTEM_TEST,
                    "System-test publication validation failed: "
                            + String.join(", ", validation.errors()));
        }
        AgentScope receiptScope = AgentScope.same(context.inspector().authorWriteScopes(), "N/A");
        ApprovedGateReceipt receipt = systemTestGate(
                context, FeatureStage.SYSTEM_TEST, receiptScope).get();
        ContainerGateResult selected = receiptResult(receipt, false);
        context.cancellation().check();
        if (!selected.passed()) {
            return failedGate(context.job(), FeatureStage.PUBLISH_SYSTEM_TEST, selected,
                    "Final configured smoke and new system tests did not pass");
        }
        FeatureGitPublisher.Result testedCommit = gitPublisher.commitAndPushSystemTests(
                context.branch(), context.testWorktree(), context.inspector().authorWriteScopes(),
                "test(system): cover feature issue #" + context.job().identity().issue().iid());
        context.cancellation().check();
        if (!testedCommit.success()) {
            return publicationFailure(context.job(), FeatureStage.PUBLISH_SYSTEM_TEST,
                    testedCommit.error(), testedCommit.retryable());
        }
        context.inspector().appendEvidence("FINAL_SELECTED_SYSTEM_TEST", selected,
                testedCommit.headSha());
        FeatureGitPublisher.Result evidenceCommit = gitPublisher.commitAndPushSystemTests(
                context.branch(), context.testWorktree(), context.inspector().authorWriteScopes(),
                "docs(devflow): record system-test evidence");
        context.cancellation().check();
        if (!evidenceCommit.success()) {
            return publicationFailure(context.job(), FeatureStage.PUBLISH_SYSTEM_TEST,
                    evidenceCommit.error(), evidenceCommit.retryable());
        }
        SystemTestPullRequestPublisher.Result remote = systemTestPullRequests.publish(
                context.job(), context.branch(), context.sourceRevision(),
                evidenceCommit.headSha());
        context.cancellation().check();
        if (!remote.success() || remote.pullRequest() == null) {
            return publicationFailure(context.job(), FeatureStage.PUBLISH_SYSTEM_TEST,
                    remote.error(), remote.retryable());
        }
        FeatureJob.PullRequest binding = binding(remote.pullRequest(), evidenceCommit.headSha());
        FeatureJobMutation mutation = new FeatureJobMutation(
                FeatureStage.SYSTEM_TEST_READY_FOR_REVIEW, null, 0, 0,
                "System-test PR is ready for human review and merge");
        return new FeatureStageOutcome(mutation, Optional.empty(), Optional.of(binding));
    }

    static List<String> selectedSystemTests(List<String> smokeSelectors,
                                            List<String> newTestSelectors) {
        LinkedHashSet<String> selected = new LinkedHashSet<>(smokeSelectors);
        selected.addAll(newTestSelectors);
        return List.copyOf(selected);
    }

    private FeatureStageOutcome publishShip(StageContext context, List<String> scopes) {
        FeatureGitPublisher.Result deliverables = gitPublisher.commitAndPush(context.job(),
                context.worktree(), scopes, "docs(devflow): complete feature closeout");
        context.cancellation().check();
        if (!deliverables.success()) {
            return publicationFailure(context.job(), FeatureStage.SHIP,
                    deliverables.error(), deliverables.retryable());
        }
        ContainerGateResult anchor = new ContainerGateResult(ContainerGateResult.Outcome.PASSED,
                0, "Controller recorded final verified commit", List.of());
        context.inspector().appendEvidence("SHIP_COMMIT", anchor, deliverables.headSha());
        Publication publication = publish(context, context.inspector().artifactWriteScope(),
                "docs(devflow): record final commit evidence", FeatureStage.READY_FOR_REVIEW, true);
        if (!publication.success()) {
            return publication.failure().orElseThrow();
        }
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.READY_FOR_REVIEW,
                null, 0, 0, "Feature PR is ready for human review and merge");
        return new FeatureStageOutcome(mutation, Optional.of(publication.binding().orElseThrow()));
    }

    private AgentInvocation invoke(StageContext context, FeatureStage stage,
                                   AgentScope scope) {
        FeatureStageAgent.Assignment assignment = new FeatureStageAgent.Assignment(
                context.job(), stage, config.componentRoot(),
                context.job().progress().taskAttempt() + 1,
                assignmentEvidence(context.job(), stage, scope.evidence()));
        context.cancellation().check();
        ApprovedGateController gate = featureGate(context, stage, scope);
        FeatureStageAgent.RepairExecution execution;
        try {
            execution = agent.executeWithGate(
                    assignment, context.issueData().issue(), context.issueData().comments(),
                    context.worktree(), scope.writeScopes(), gateControl(
                            context.job(), gate, context.worktree(), false));
        } catch (FeatureExecutionException ex) {
            restoreThrownPolicy(ex, context.worktree(), false, stage);
            throw ex;
        }
        context.cancellation().check();
        restorePolicyViolation(execution, context.worktree(), false);
        return invocation(context.job(), stage, execution);
    }

    static String assignmentEvidence(FeatureJob job, FeatureStage stage, String evidence) {
        String current = evidence == null || evidence.isBlank() ? "N/A" : evidence;
        if (stage != FeatureStage.IMPLEMENT_RED) {
            return current;
        }
        StringBuilder result = new StringBuilder(current)
                .append("\n\nTRUSTED RED GATE CONTRACT\n")
                .append("The selected test sources must compile and the fixed Maven selector must ")
                .append("reach a JUnit test failure or error. Java compilation failure is never ")
                .append("accepted as RED. When the feature introduces a missing Java API, use a ")
                .append("compile-safe behavioral probe such as reflection, fail through JUnit when ")
                .append("the API is absent, and continue to assert its behavior once present.");
        if (job.progress().taskAttempt() > 0) {
            result.append("\n\nTRUSTED RETRY FEEDBACK\n")
                    .append(redRetryFeedback(job.record().lastError()));
        }
        return result.toString();
    }

    private static String redRetryFeedback(String lastError) {
        String error = lastError == null ? "" : lastError;
        if (error.startsWith("RED did not produce the expected test failure: TEST_FAILED")) {
            return "The previous selected RED command ended in TEST_FAILED rather than a "
                    + "trustworthy JUnit failure. Rebuild the test from the clean committed "
                    + "snapshot, ensure it compiles, and make the missing behavior fail inside "
                    + "the test runtime.";
        }
        if (error.startsWith("Agent returned INVALID_OUTPUT")) {
            return "The previous invocation did not satisfy the structured-result contract. "
                    + "Rebuild the bounded test change and return exactly the required JSON object.";
        }
        if (error.startsWith("Feature stage execution failed:")) {
            return "The previous invocation terminated unexpectedly. Rebuild the bounded test "
                    + "change from the clean committed snapshot and keep tool reads focused.";
        }
        return "The previous RED attempt was rejected by the controller. Rebuild one bounded, "
                + "compilable test change from the clean committed snapshot.";
    }

    private AgentInvocation invokeSystemTest(SystemTestContext context,
                                             FeatureStage stage,
                                             AgentScope scope) {
        FeatureStageAgent.Assignment assignment = new FeatureStageAgent.Assignment(
                context.job(), stage, ".", context.job().progress().taskAttempt() + 1,
                scope.evidence());
        context.cancellation().check();
        ApprovedGateController gate = systemTestGate(context, stage, scope);
        FeatureStageAgent.RepairExecution execution;
        try {
            execution = agent.executeSystemTestWithGate(assignment,
                    context.issueData().issue(), context.issueData().comments(),
                    context.sourceWorktree(), context.sourceRevision(), context.testWorktree(),
                    scope.writeScopes(), gateControl(
                            context.job(), gate, context.testWorktree(), true));
        } catch (FeatureExecutionException ex) {
            restoreThrownPolicy(ex, context.testWorktree(), true, stage);
            throw ex;
        }
        context.cancellation().check();
        restorePolicyViolation(execution, context.testWorktree(), true);
        return invocation(context.job(), stage, execution);
    }

    private void restorePolicyViolation(FeatureStageAgent.RepairExecution execution,
                                        Path worktree, boolean systemTest) {
        boolean violation = execution.failure().map(FeatureFailure::category)
                .filter(category -> category == FeatureFailureCategory.POLICY_VIOLATION)
                .isPresent();
        if (!violation) {
            return;
        }
        restorePolicySnapshot(worktree, systemTest, execution.agentResult().stage());
    }

    private void restoreThrownPolicy(FeatureExecutionException failure, Path worktree,
                                     boolean systemTest, FeatureStage stage) {
        if (failure.failure().category() == FeatureFailureCategory.POLICY_VIOLATION) {
            restorePolicySnapshot(worktree, systemTest, stage);
        }
    }

    private void restorePolicySnapshot(Path worktree, boolean systemTest, FeatureStage stage) {
        FeatureGitPublisher.RestoreResult restored =
                gitPublisher.restorePolicySnapshot(worktree, systemTest);
        if (!restored.success()) {
            throw new FeatureExecutionException(new FeatureFailure(
                    "POLICY_SNAPSHOT_RESTORE_FAILED",
                    restored.retryable() ? FeatureFailureCategory.TRANSIENT_INFRASTRUCTURE
                            : FeatureFailureCategory.INTERNAL,
                    stage, restored.retryable() ? stage : null,
                    new FeatureFailure.Diagnostic(restored.error(), "")));
        }
    }

    private FeatureStageAgent.GateControl gateControl(FeatureJob job,
                                                       ApprovedGateController gate,
                                                       Path worktree,
                                                       boolean systemTest) {
        return new FeatureStageAgent.GateControl(gate,
                config.maxPrimaryRepairRounds(), config.maxDiagnosticRepairRounds(),
                job.recovery().repairs().primary(),
                job.recovery().repairs().diagnostic(),
                (tier, round, failure) -> recordRepair(job, tier, round, failure),
                failureHistory(job) + "\nCURRENT BOUNDED DIFF\n"
                        + gitPublisher.boundedDiff(worktree, systemTest));
    }

    private void recordRepair(FeatureJob job, String tier, int round, FeatureFailure failure) {
        store.recordFailure(job.identity().id(), job.record().version(), failure,
                new FeatureFailureEvent.RepairAttempt(tier, round), 0L);
    }

    private String failureHistory(FeatureJob job) {
        StringBuilder history = new StringBuilder();
        store.listFailureEvents(job.identity().id(), 20).forEach(event -> history
                .append(event.attempt().tier()).append('#').append(event.attempt().number())
                .append(' ').append(event.failure().code()).append(": ")
                .append(event.failure().diagnostic().summary()).append('\n'));
        return history.toString();
    }

    private AgentInvocation invocation(FeatureJob job, FeatureStage stage,
                                       FeatureStageAgent.RepairExecution execution) {
        if (execution.success()) {
            return new AgentInvocation(Optional.empty(), execution.gateReceipt());
        }
        FeatureFailure failure = execution.failure().orElseGet(() -> new FeatureFailure(
                "REPAIR_BUDGET_EXHAUSTED", FeatureFailureCategory.AGENT_CORRECTABLE,
                stage, stage, new FeatureFailure.Diagnostic(
                "Automatic repair budget exhausted", execution.agentResult().summary())));
        return new AgentInvocation(Optional.of(failureOutcome(job, failure)),
                execution.gateReceipt());
    }

    private FeatureStageOutcome failureOutcome(FeatureJob job, FeatureFailure failure) {
        return switch (failure.category()) {
            case DEPENDENCY_MISSING -> transition(job, FeatureStage.DEPENDENCY_PREFETCH,
                    job.progress().gateRound(), job.progress().taskAttempt(),
                    failure.diagnostic().summary(), failure.originStage());
            case TRANSIENT_MODEL, TRANSIENT_GITCODE, TRANSIENT_INFRASTRUCTURE ->
                    throw new FeatureExecutionException(failure);
            case POLICY_VIOLATION, CONFIGURATION, PRODUCT_DECISION,
                    ENVIRONMENT_BLOCKER, INTERNAL, AGENT_CORRECTABLE ->
                    throw new FeatureExecutionException(failure);
        };
    }

    private ApprovedGateController featureGate(StageContext context, FeatureStage stage,
                                                AgentScope scope) {
        List<String> selectors = featureSelectors(context, stage);
        String profile = gateProfile(stage);
        ApprovedGateController.GateIdentity identity = new ApprovedGateController.GateIdentity(
                profile, selectors, config.containerImage(), "");
        ApprovedGateController.WorktreeState state = new ApprovedGateController.WorktreeState(
                context.worktree(), () -> gitPublisher.currentHead(context.worktree()),
                () -> gitPublisher.dirtyFiles(context.worktree()));
        ApprovedGateController.GateEvaluation evaluation =
                new ApprovedGateController.GateEvaluation(Optional::empty,
                        () -> evaluateFeatureGate(context, stage, scope,
                                featureSelectors(context, stage)),
                        () -> featureSelectors(context, stage));
        ApprovedGateController.GateSpec spec = new ApprovedGateController.GateSpec(
                context.job(), stage, identity, state, evaluation);
        return new ApprovedGateController(store, spec);
    }

    private ApprovedGateController systemTestGate(SystemTestContext context, FeatureStage stage,
                                                   AgentScope scope) {
        List<String> selectors = stage == FeatureStage.SYSTEM_TEST
                ? config.systemTestSmokeSelectors() : List.of();
        ApprovedGateController.GateIdentity identity = new ApprovedGateController.GateIdentity(
                stage == FeatureStage.SYSTEM_TEST ? "SYSTEM_TEST_SELECTED" : "STATIC",
                selectors, config.containerImage(), context.sourceRevision());
        ApprovedGateController.WorktreeState state = new ApprovedGateController.WorktreeState(
                context.testWorktree(),
                () -> gitPublisher.currentSystemTestHead(context.testWorktree()),
                () -> systemTestFingerprintPaths(context, stage),
                () -> gitPublisher.systemTestChangedFiles(context.testWorktree()));
        ApprovedGateController.GateEvaluation evaluation = systemTestGateEvaluation(
                context, stage, scope);
        ApprovedGateController.GateSpec spec = new ApprovedGateController.GateSpec(
                context.job(), stage, identity, state, evaluation);
        return new ApprovedGateController(store, spec);
    }

    private ApprovedGateController.GateEvaluation systemTestGateEvaluation(
            SystemTestContext context, FeatureStage stage, AgentScope scope) {
        if (stage != FeatureStage.SYSTEM_TEST) {
            return new ApprovedGateController.GateEvaluation(Optional::empty,
                    () -> evaluateSystemTestGate(context, stage, scope),
                    () -> systemTestSelectors(context, stage));
        }
        return new ApprovedGateController.GateEvaluation(
                () -> evaluateSystemTestPrecondition(context, stage, scope),
                () -> evaluateSystemTestGate(context, stage, scope),
                () -> systemTestSelectors(context, stage));
    }

    private ApprovedGateReceipt.Result evaluateFeatureGate(
            StageContext context, FeatureStage stage, AgentScope scope,
            List<String> selectors) {
        List<String> violations = FeaturePathPolicy.violations(
                gitPublisher.dirtyFiles(context.worktree()), scope.validationScopes());
        if (!violations.isEmpty()) {
            return ApprovedGateResults.policy(stage, String.join(", ", violations));
        }
        try {
            return switch (stage) {
                case SPECIFY -> ApprovedGateResults.staticValidation(stage,
                        context.inspector().validateArtifacts(stage));
                case DESIGN -> designGate(context);
                case REVIEW_R1, REVIEW_R2, REVIEW_R3 -> reviewGate(context, stage);
                case IMPLEMENT_RED -> ApprovedGateResults.container(stage,
                        container.run(RootlessContainerGateRunner.Profile.RED,
                                context.worktree(), selectors, gateCache(context.job())), true);
                case IMPLEMENT_GREEN, IMPLEMENT_REFACTOR, SHIP -> featureTestGate(
                        context, stage, selectors);
                default -> ApprovedGateResults.staticValidation(stage, List.of());
            };
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ApprovedGateResults.staticValidation(stage, List.of(safe(ex.getMessage())));
        }
    }

    private ApprovedGateReceipt.Result designGate(StageContext context) {
        List<String> errors = new java.util.ArrayList<>(
                context.inspector().validateArtifacts(FeatureStage.DESIGN));
        try {
            context.inspector().implementationScopes();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            errors.add(safe(ex.getMessage()));
        }
        return ApprovedGateResults.staticValidation(FeatureStage.DESIGN, errors);
    }

    private ApprovedGateReceipt.Result reviewGate(StageContext context, FeatureStage stage) {
        String reviewPath = context.inspector().reviewPath(stage, reviewRound(context.job()));
        FeatureArtifactInspector.Verdict verdict = context.inspector().verdict(reviewPath);
        if (stage == FeatureStage.REVIEW_R2
                && verdict == FeatureArtifactInspector.Verdict.PASS) {
            context.inspector().validateVerificationPlan();
        }
        return ApprovedGateResults.staticValidation(stage, List.of());
    }

    private ApprovedGateReceipt.Result featureTestGate(StageContext context, FeatureStage stage,
                                                        List<String> selectors) {
        if (stage == FeatureStage.SHIP) {
            List<String> errors = context.inspector().validateArtifacts(stage);
            if (!errors.isEmpty()) {
                return ApprovedGateResults.staticValidation(stage, errors);
            }
        }
        return ApprovedGateResults.container(stage,
                container.run(RootlessContainerGateRunner.Profile.TARGETED,
                        context.worktree(), selectors, gateCache(context.job())), false);
    }

    private ApprovedGateReceipt.Result evaluateSystemTestGate(
            SystemTestContext context, FeatureStage stage, AgentScope scope) {
        if (stage == FeatureStage.SYSTEM_TEST) {
            List<String> selected = systemTestSelectors(context, stage);
            return ApprovedGateResults.container(stage, container.runSystemTest(
                    RootlessContainerGateRunner.SystemTestProfile.SELECTED,
                    context.sourceWorktree(), context.testWorktree(), selected,
                    gateCache(context.job())), false);
        }
        List<String> changed = gitPublisher.systemTestChangedFiles(context.testWorktree());
        List<String> violations = FeaturePathPolicy.violations(changed, scope.validationScopes());
        if (!violations.isEmpty()) {
            return ApprovedGateResults.policy(stage, String.join(", ", violations));
        }
        try {
            if (stage == FeatureStage.REVIEW_SYSTEM_TEST) {
                context.inspector().verdict(
                        context.inspector().reviewPath(reviewRound(context.job())));
                return ApprovedGateResults.staticValidation(stage, List.of());
            }
            return ApprovedGateResults.staticValidation(stage, List.of());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ApprovedGateResults.staticValidation(stage, List.of(safe(ex.getMessage())));
        }
    }

    private Optional<ApprovedGateReceipt.Result> evaluateSystemTestPrecondition(
            SystemTestContext context, FeatureStage stage, AgentScope scope) {
        List<String> changed = gitPublisher.systemTestChangedFiles(context.testWorktree());
        List<String> violations = FeaturePathPolicy.violations(changed, scope.validationScopes());
        if (!violations.isEmpty()) {
            return Optional.of(ApprovedGateResults.policy(stage, String.join(", ", violations)));
        }
        try {
            SystemTestArtifactInspector.Validation validation =
                    context.inspector().validateAuthor(changed);
            if (!validation.valid()) {
                return Optional.of(ApprovedGateResults.staticValidation(
                        stage, validation.errors()));
            }
            return Optional.empty();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Optional.of(ApprovedGateResults.staticValidation(
                    stage, List.of(safe(ex.getMessage()))));
        }
    }

    private List<String> featureSelectors(StageContext context, FeatureStage stage) {
        try {
            return switch (stage) {
                case IMPLEMENT_RED -> currentSelectors(context,
                        FeatureArtifactInspector.TestPhase.RED);
                case IMPLEMENT_GREEN -> currentSelectors(context,
                        FeatureArtifactInspector.TestPhase.GREEN);
                case IMPLEMENT_REFACTOR -> refactorSelectors(context);
                case SHIP -> context.inspector().allTestSelectors();
                default -> List.of();
            };
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return List.of();
        }
    }

    private static List<String> currentSelectors(StageContext context,
                                                 FeatureArtifactInspector.TestPhase phase) {
        FeatureArtifactInspector.PlanCursor task = context.inspector().nextTask();
        return task.complete() ? List.of() : context.inspector().testSelectors(task.taskId(), phase);
    }

    private static List<String> refactorSelectors(StageContext context) {
        FeatureArtifactInspector.PlanCursor task = context.inspector().nextTask();
        if (!task.complete()) {
            return context.inspector().testSelectors(task.taskId(),
                    FeatureArtifactInspector.TestPhase.REFACTOR);
        }
        return context.inspector().lastDoneTaskId().map(taskId -> context.inspector()
                .testSelectors(taskId, FeatureArtifactInspector.TestPhase.REFACTOR))
                .orElse(List.of());
    }

    private static String gateProfile(FeatureStage stage) {
        return switch (stage) {
            case IMPLEMENT_RED -> "RED";
            case IMPLEMENT_GREEN, IMPLEMENT_REFACTOR, SHIP -> "TARGETED";
            default -> "STATIC";
        };
    }

    private List<String> systemTestFingerprintPaths(SystemTestContext context,
                                                    FeatureStage stage) {
        List<String> changed = gitPublisher.systemTestChangedFiles(context.testWorktree());
        if (stage != FeatureStage.SYSTEM_TEST) {
            return changed;
        }
        String artifactPrefix = context.inspector().artifactWriteScope();
        return changed.stream().filter(path -> !path.startsWith(artifactPrefix)).toList();
    }

    private List<String> systemTestSelectors(SystemTestContext context, FeatureStage stage) {
        if (stage != FeatureStage.SYSTEM_TEST) {
            return List.of();
        }
        try {
            List<String> changed = gitPublisher.systemTestChangedFiles(context.testWorktree());
            SystemTestArtifactInspector.Validation validation =
                    context.inspector().validateAuthor(changed);
            if (validation.valid()) {
                return selectedSystemTests(config.systemTestSmokeSelectors(),
                        validation.testSelectors());
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The evaluator returns the bounded validation failure.
        }
        return config.systemTestSmokeSelectors();
    }

    private static ContainerGateResult receiptResult(ApprovedGateReceipt receipt,
                                                     boolean expectedRed) {
        ContainerGateResult.Outcome outcome;
        if (receipt.result().status() == ApprovedGateReceipt.Status.PASSED) {
            outcome = expectedRed ? ContainerGateResult.Outcome.EXPECTED_RED
                    : ContainerGateResult.Outcome.PASSED;
        } else if (receipt.result().status() == ApprovedGateReceipt.Status.DEPENDENCY_MISSING) {
            outcome = ContainerGateResult.Outcome.DEPENDENCY_MISSING;
        } else if (receipt.result().status() == ApprovedGateReceipt.Status.TRANSIENT) {
            outcome = ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED;
        } else {
            outcome = ContainerGateResult.Outcome.TEST_FAILED;
        }
        return new ContainerGateResult(outcome, receipt.result().evidence().exitCode(),
                receipt.result().evidence().outputTail(), List.of());
    }

    private Publication publish(StageContext context, List<String> scopes,
                                String commitMessage, FeatureStage reportedStage,
                                boolean readyForReview) {
        FeatureGitPublisher.Result pushed = gitPublisher.commitAndPush(
                context.job(), context.worktree(), scopes, commitMessage);
        context.cancellation().check();
        if (!pushed.success()) {
            return Publication.failure(publicationFailure(context.job(),
                    context.job().progress().stage(), pushed.error(), pushed.retryable()));
        }
        FeaturePullRequestPublisher.Result remote = pullRequests.publish(
                context.job(), pushed.headSha(), reportedStage, readyForReview);
        context.cancellation().check();
        if (!remote.success() || remote.pullRequest() == null) {
            return Publication.failure(publicationFailure(context.job(),
                    context.job().progress().stage(), remote.error(), remote.retryable()));
        }
        return Publication.success(binding(remote.pullRequest(), pushed.headSha()));
    }

    private static FeatureJob.PullRequest binding(FeaturePullRequest remote, String headSha) {
        return new FeatureJob.PullRequest(remote.number(), remote.url(), headSha,
                remote.draft(), Instant.now().toEpochMilli());
    }

    private FeatureStageOutcome failedGate(FeatureJob job, FeatureStage resume,
                                           ContainerGateResult gate, String message) {
        if (gate.outcome() == ContainerGateResult.Outcome.DEPENDENCY_MISSING) {
            return FeatureStageOutcome.transition(new FeatureJobMutation(
                    FeatureStage.DEPENDENCY_PREFETCH, resume, job.progress().gateRound(),
                    job.progress().taskAttempt(), "Dependency prefetch required"));
        }
        if (gate.outcome() == ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED
                || gate.outcome() == ContainerGateResult.Outcome.TIMED_OUT) {
            return publicationFailure(job, resume, message + ": " + gate.outcome(), true);
        }
        return retryAgentStage(job, resume, message + ": " + gate.outcome());
    }

    private FeatureStageOutcome retryAgentStage(FeatureJob job, FeatureStage stage, String error) {
        int attempt = job.progress().taskAttempt() + 1;
        if (attempt >= maxAutomatedRounds()) {
            return failedAutomation(job, "Automated stage attempt limit reached: " + error);
        }
        return transition(job, stage, job.progress().gateRound(), attempt, error);
    }

    private FeatureStageOutcome reviewRework(FeatureJob job, FeatureStage author,
                                             FeatureStage reviewer, int round, String message) {
        if (round >= maxAutomatedRounds()) {
            return failedAutomation(job, "Automated review round limit reached: " + message);
        }
        return transition(job, author, round + 1, 0, message);
    }

    private FeatureJobMutation reviewMutation(FeatureJob job,
                                              FeatureArtifactInspector.Verdict verdict,
                                              FeatureStage next, int round, String gate) {
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            if (round >= maxAutomatedRounds()) {
                return new FeatureJobMutation(FeatureStage.FAILED_AUTOMATION,
                        null, round, 0,
                        gate + " automated review round limit reached");
            }
            return new FeatureJobMutation(next, null, round + 1, 0, gate + " requested rework");
        }
        return new FeatureJobMutation(next, null, 0, 0, gate + " passed");
    }

    private FeatureStage r2NextStage(FeatureJob job, FeatureArtifactInspector.Verdict verdict,
                                     int round) {
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            return round >= maxAutomatedRounds()
                    ? FeatureStage.FAILED_AUTOMATION : FeatureStage.DESIGN;
        }
        return FeatureStage.IMPLEMENT_RED;
    }

    private FeatureStage r3NextStage(FeatureJob job, FeatureArtifactInspector.Verdict verdict,
                                     int round) {
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            return round >= maxAutomatedRounds()
                    ? FeatureStage.FAILED_AUTOMATION : FeatureStage.IMPLEMENT_RED;
        }
        return FeatureStage.SHIP;
    }

    private FeatureStageOutcome restoreRetry(FeatureJob job) {
        FeatureStage retry = job.recovery().retryStage();
        if (retry == null) {
            retry = job.progress().resumeStage();
        }
        if (retry == null) {
            return failedInternal(job, "Retryable state has no resume stage");
        }
        FeatureStage resume = retry == FeatureStage.DEPENDENCY_PREFETCH
                ? job.progress().resumeStage() : null;
        if (retry == FeatureStage.DEPENDENCY_PREFETCH
                && (resume == null || resume == FeatureStage.DEPENDENCY_PREFETCH)) {
            return failedInternal(job, "Dependency prefetch retry lost its recovery stage");
        }
        return transition(job, retry, job.progress().gateRound(),
                job.progress().taskAttempt(), "Retrying bounded stage", resume);
    }

    private FeatureStageOutcome publicationFailure(FeatureJob job, FeatureStage resume,
                                                   String error, boolean retryable) {
        FeatureFailure failure = publicationFailure(resume, error, retryable);
        if (failure.safeToReplay()) {
            throw new FeatureExecutionException(failure);
        }
        return failureOutcome(job, failure);
    }

    static FeatureFailure publicationFailure(FeatureStage stage, String error,
                                             boolean retryable) {
        String detail = safe(error);
        if (retryable) {
            return new FeatureFailure("PUBLICATION_TRANSIENT",
                    FeatureFailureCategory.TRANSIENT_GITCODE, stage, stage,
                    new FeatureFailure.Diagnostic("Publication failed transiently", detail));
        }
        String lower = detail.toLowerCase(java.util.Locale.ROOT);
        FeatureFailureCategory category = lower.contains("credential")
                || lower.contains("authorization") || lower.contains("401")
                || lower.contains("403") || lower.contains("http 400")
                || lower.contains("approver user") ? FeatureFailureCategory.CONFIGURATION
                : lower.contains("disallowed") || lower.contains("invalid owned")
                || lower.contains("does not match") ? FeatureFailureCategory.POLICY_VIOLATION
                : FeatureFailureCategory.INTERNAL;
        return new FeatureFailure("PUBLICATION_REJECTED", category, stage, null,
                new FeatureFailure.Diagnostic("Publication was rejected", detail));
    }

    private int maxAutomatedRounds() {
        return config.maxPrimaryRepairRounds() + config.maxDiagnosticRepairRounds();
    }

    private FeatureStageOutcome blockedExternal(FeatureJob job, FeatureStage resume,
                                                String message) {
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.BLOCKED_EXTERNAL,
                resume, job.progress().gateRound(), job.progress().taskAttempt(), safe(message));
        return FeatureStageOutcome.transition(mutation);
    }

    private FeatureStageOutcome failedAutomation(FeatureJob job, String message) {
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.FAILED_AUTOMATION,
                null, job.progress().gateRound(), job.progress().taskAttempt(), safe(message));
        return FeatureStageOutcome.transition(mutation);
    }

    private FeatureStageOutcome failedPolicy(StageContext context, String message) {
        restorePolicySnapshot(context.worktree(), false,
                context.job().progress().stage());
        FeatureJob job = context.job();
        return transition(job, FeatureStage.FAILED_POLICY, job.progress().gateRound(),
                job.progress().taskAttempt(), message);
    }

    private FeatureStageOutcome failedInternal(FeatureJob job, String message) {
        return transition(job, FeatureStage.FAILED_INTERNAL, job.progress().gateRound(),
                job.progress().taskAttempt(), message);
    }

    private FeatureStageOutcome configurationFailure(FeatureJob job, String message) {
        return transition(job, FeatureStage.FAILED_CONFIGURATION,
                job.progress().gateRound(), job.progress().taskAttempt(), message);
    }

    private FeatureStageOutcome waitingState(FeatureJob job) {
        return failedInternal(job,
                "Worker leased a state that is not executable");
    }

    private FeatureStageOutcome transition(FeatureJob job, FeatureStage stage,
                                           int round, int attempt, String message) {
        return FeatureStageOutcome.transition(new FeatureJobMutation(
                stage, null, round, attempt, safe(message)));
    }

    private FeatureStageOutcome transition(FeatureJob job, FeatureStage stage,
                                           int round, int attempt, String message,
                                           FeatureStage resumeStage) {
        return FeatureStageOutcome.transition(new FeatureJobMutation(
                stage, resumeStage, round, attempt, safe(message)));
    }

    private static int reviewRound(FeatureJob job) {
        return Math.max(1, job.progress().gateRound());
    }

    private static String safe(String message) {
        String value = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ').strip();
        return value.substring(0, Math.min(value.length(), 1000));
    }

    private static boolean isSystemTestStage(FeatureStage stage) {
        return stage == FeatureStage.SYSTEM_TEST || stage == FeatureStage.REVIEW_SYSTEM_TEST
                || stage == FeatureStage.PUBLISH_SYSTEM_TEST;
    }

    private static String gateEvidence(String profile, ContainerGateResult result) {
        String output = result.output().replace('\r', ' ').replace('\n', ' ').strip();
        output = output.substring(0, Math.min(output.length(), 1000));
        return profile + "=" + result.outcome() + ", exit=" + result.exitCode()
                + ", output=" + output;
    }

    private static VerificationSelection verificationSelection(
            FeatureArtifactInspector inspector, String taskId,
            FeatureArtifactInspector.TestPhase phase) {
        FeatureArtifactInspector.TestSelectorContract contract =
                inspector.verificationContract(taskId);
        List<String> testSelectors = inspector.testSelectors(taskId, phase);
        if (testSelectors.isEmpty()) {
            throw new IllegalStateException(
                    "Plan task " + taskId + " has no exact " + phase + " test selector");
        }
        return new VerificationSelection(contract, testSelectors);
    }

    private record StageContext(FeatureJob job, IssueData issueData, Path worktree,
                                FeatureArtifactInspector inspector,
                                CancellationCheckpoint cancellation) {
    }

    private record SystemTestContext(FeatureJob job, IssueData issueData,
                                     Path sourceWorktree, String sourceRevision, Path testWorktree,
                                     String branch, SystemTestArtifactInspector inspector,
                                     CancellationCheckpoint cancellation) {
    }

    private record IssueData(FeatureIssue issue, List<FeatureComment> comments) {
        private IssueData {
            issue = Objects.requireNonNull(issue, "issue must not be null");
            comments = comments == null ? List.of() : List.copyOf(comments);
        }
    }

    private record AgentScope(List<String> writeScopes, List<String> validationScopes,
                              String evidence) {
        private AgentScope {
            writeScopes = List.copyOf(writeScopes);
            validationScopes = List.copyOf(validationScopes);
            evidence = evidence == null ? "N/A" : evidence;
        }

        private static AgentScope same(List<String> scopes, String evidence) {
            return new AgentScope(scopes, scopes, evidence);
        }
    }

    private record VerificationSelection(
            FeatureArtifactInspector.TestSelectorContract contract,
            List<String> testSelectors) {
        private VerificationSelection {
            contract = Objects.requireNonNull(contract, "contract must not be null");
            testSelectors = List.copyOf(testSelectors);
        }
    }

    private record BaselineVerification(
            ContainerGateResult gate, Optional<FeatureStageOutcome> failure) {
        private BaselineVerification {
            gate = Objects.requireNonNull(gate, "gate must not be null");
            failure = Objects.requireNonNull(failure, "failure must not be null");
        }

        private static BaselineVerification passed(ContainerGateResult gate) {
            return new BaselineVerification(gate, Optional.empty());
        }

        private static BaselineVerification failed(
                ContainerGateResult gate, FeatureStageOutcome failure) {
            return new BaselineVerification(gate, Optional.of(failure));
        }
    }

    private record AgentInvocation(Optional<FeatureStageOutcome> failure,
                                   Optional<ApprovedGateReceipt> receipt) {
        private AgentInvocation {
            failure = failure == null ? Optional.empty() : failure;
            receipt = receipt == null ? Optional.empty() : receipt;
        }
    }

    private record Publication(boolean success, Optional<FeatureJob.PullRequest> binding,
                               Optional<FeatureStageOutcome> failure) {
        private static Publication success(FeatureJob.PullRequest binding) {
            return new Publication(true, Optional.of(binding), Optional.empty());
        }

        private static Publication failure(FeatureStageOutcome failure) {
            return new Publication(false, Optional.empty(), Optional.of(failure));
        }
    }

    /** Trusted infrastructure boundaries grouped to keep construction explicit. */
    public record Infrastructure(FeatureWorktreeManager worktrees,
                                 RootlessContainerGateRunner container,
                                 FeatureGitPublisher gitPublisher,
                                 FeaturePullRequestPublisher pullRequests,
                                 SystemTestInfrastructure systemTests) {
        /** Validate every privileged boundary. */
        public Infrastructure {
            worktrees = Objects.requireNonNull(worktrees, "worktrees must not be null");
            container = Objects.requireNonNull(container, "container must not be null");
            gitPublisher = Objects.requireNonNull(gitPublisher, "gitPublisher must not be null");
            pullRequests = Objects.requireNonNull(pullRequests, "pullRequests must not be null");
            systemTests = Objects.requireNonNull(systemTests, "systemTests must not be null");
        }
    }

    /** Trusted post-merge test-repository boundaries. */
    public record SystemTestInfrastructure(SystemTestWorktreeManager worktrees,
                                           SystemTestPullRequestPublisher pullRequests) {
        /** Validate both post-merge boundaries. */
        public SystemTestInfrastructure {
            worktrees = Objects.requireNonNull(worktrees,
                    "system-test worktrees must not be null");
            pullRequests = Objects.requireNonNull(pullRequests,
                    "system-test pull requests must not be null");
        }
    }

    /** Trusted request for one leased state execution. */
    public record ExecutionRequest(FeatureJob job, FeatureIssue issue,
                                   List<FeatureComment> comments,
                                   CancellationCheckpoint cancellation) {
        /** Validate and freeze the request. */
        public ExecutionRequest {
            job = Objects.requireNonNull(job, "job must not be null");
            issue = Objects.requireNonNull(issue, "issue must not be null");
            comments = comments == null ? List.of() : List.copyOf(comments);
            cancellation = Objects.requireNonNull(cancellation, "cancellation must not be null");
        }
    }

    /** Cooperative cancellation checkpoint owned by the durable worker. */
    @FunctionalInterface
    public interface CancellationCheckpoint {
        /**
         * Stop execution when the lease or persisted stage no longer matches.
         *
         * @throws CancellationException when a pause or cancellation was persisted
         */
        void check();
    }
}
