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
import examples.gitcode_feature_evolver.infrastructure.FeatureGitPublisher;
import examples.gitcode_feature_evolver.infrastructure.FeatureWorktreeManager;
import examples.gitcode_feature_evolver.infrastructure.RootlessContainerGateRunner;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobMutation;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.publish.FeaturePullRequestPublisher;

import java.nio.file.Path;
import java.time.Instant;
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
    private static final int MAX_AUTOMATED_ATTEMPTS = 3;
    private final FeatureEvolvingConfig config;
    private final FeatureStageAgent agent;
    private final FeatureWorktreeManager worktrees;
    private final RootlessContainerGateRunner container;
    private final FeatureGitPublisher gitPublisher;
    private final FeaturePullRequestPublisher pullRequests;

    /**
     * Create the complete bounded stage executor.
     *
     * @param config validated feature configuration
     * @param agent restricted stage Agent
     * @param infrastructure trusted Worktree, container, Git, and PR boundaries
     */
    public FeatureStageExecutor(FeatureEvolvingConfig config, FeatureStageAgent agent,
                                Infrastructure infrastructure) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
        Infrastructure required = Objects.requireNonNull(infrastructure,
                "infrastructure must not be null");
        this.worktrees = required.worktrees();
        this.container = required.container();
        this.gitPublisher = required.gitPublisher();
        this.pullRequests = required.pullRequests();
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
        if (stage == FeatureStage.FAILED_RETRYABLE) {
            return restoreRetry(job);
        }
        if (stage == FeatureStage.CANCEL_REQUESTED) {
            return transition(job, FeatureStage.CANCELLED,
                    job.progress().gateRound(), job.progress().taskAttempt(), "Cancellation completed");
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
            case FAILED_RETRYABLE, CANCEL_REQUESTED -> throw new IllegalStateException(
                    "Preprocessed feature stage reached the stage dispatcher");
            default -> waitingState(job);
        };
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
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.SPECIFY, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
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
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.REVIEW_R1, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
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
        FeatureStage next = context.job().progress().mode().requiresApproval()
                ? FeatureStage.WAIT_R1_APPROVAL : FeatureStage.DESIGN;
        Publication publication = publish(context, context.inspector().artifactWriteScope(),
                "docs(devflow): complete R1 artifacts", next, false);
        if (!publication.success()) {
            return publication.failure().orElseThrow();
        }
        FeatureJobMutation mutation = new FeatureJobMutation(next, null,
                next == FeatureStage.DESIGN ? 0 : reviewRound(context.job()), 0,
                "Long-lived Draft PR created or reconciled");
        return new FeatureStageOutcome(mutation, Optional.of(publication.binding().orElseThrow()));
    }

    private FeatureStageOutcome design(StageContext context) {
        AgentScope scope = AgentScope.same(context.inspector().artifactWriteScope(),
                context.inspector().currentEvidence());
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.DESIGN, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
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
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.REVIEW_R2, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        FeatureArtifactInspector.Verdict verdict = context.inspector().verdict(reviewPath);
        if (verdict == FeatureArtifactInspector.Verdict.PASS) {
            try {
                if (context.inspector().nextTask().totalTasks() == 0) {
                    return retryAgentStage(context.job(), FeatureStage.DESIGN,
                            "R2 cannot pass because plan.md has no executable TDD tasks");
                }
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
        FeatureArtifactInspector.PlanCursor task = context.inspector().nextTask();
        if (task.complete() && context.job().progress().gateRound() <= 1) {
            return transition(context.job(), FeatureStage.REVIEW_R3, 1, 0,
                    "All planned tasks completed");
        }
        Optional<FeatureStageOutcome> baselineFailure = verifyGreenBaseline(context, task);
        if (baselineFailure.isPresent()) {
            return baselineFailure.orElseThrow();
        }
        AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.IMPLEMENT_RED, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        if (task.complete()) {
            task = context.inspector().nextTask();
            if (task.complete()) {
                return retryAgentStage(context.job(), FeatureStage.IMPLEMENT_RED,
                        "R3 rework did not create a bounded plan task");
            }
        }
        ContainerGateResult gate = container.run(RootlessContainerGateRunner.Profile.RED,
                context.worktree());
        context.cancellation().check();
        if (gate.expectedRed()) {
            context.inspector().appendEvidence("RED " + task.taskId(), gate, "pending");
            return transition(context.job(), FeatureStage.IMPLEMENT_GREEN,
                    context.job().progress().gateRound(), 0, "Trustworthy RED captured");
        }
        return failedGate(context.job(), FeatureStage.IMPLEMENT_RED, gate,
                "RED did not produce the expected test failure");
    }

    private Optional<FeatureStageOutcome> verifyGreenBaseline(
            StageContext context, FeatureArtifactInspector.PlanCursor task) {
        ContainerGateResult baseline = container.run(
                RootlessContainerGateRunner.Profile.FULL, context.worktree());
        context.cancellation().check();
        if (baseline.passed()) {
            List<String> dirty = gitPublisher.dirtyFiles(context.worktree());
            if (!dirty.isEmpty()) {
                return Optional.of(failedFinal(context.job(),
                        "Pre-RED verification modified tracked files: "
                                + String.join(", ", dirty)));
            }
            context.inspector().appendEvidence("BASELINE " + task.taskId(), baseline, "pending");
            return Optional.empty();
        }
        if (baseline.outcome() == ContainerGateResult.Outcome.DEPENDENCY_MISSING) {
            return Optional.of(failedGate(context.job(), FeatureStage.IMPLEMENT_RED,
                    baseline, "Pre-RED baseline dependency is unavailable"));
        }
        if (baseline.outcome() == ContainerGateResult.Outcome.INFRASTRUCTURE_FAILED
                || baseline.outcome() == ContainerGateResult.Outcome.TIMED_OUT) {
            return Optional.of(publicationFailure(context.job(), FeatureStage.IMPLEMENT_RED,
                    "Pre-RED baseline infrastructure failed: " + baseline.outcome(), true));
        }
        return Optional.of(waitingHuman(context.job(), FeatureStage.IMPLEMENT_RED,
                "Pre-RED baseline is not green: " + baseline.outcome()));
    }

    private FeatureStageOutcome green(StageContext context) {
        List<String> scopes = context.inspector().tddWriteScopes(FeatureStage.IMPLEMENT_GREEN);
        AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.IMPLEMENT_GREEN, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        ContainerGateResult gate = container.run(RootlessContainerGateRunner.Profile.FULL,
                context.worktree());
        context.cancellation().check();
        if (gate.passed()) {
            context.inspector().appendEvidence("GREEN", gate, "pending");
            return transition(context.job(), FeatureStage.IMPLEMENT_REFACTOR,
                    context.job().progress().gateRound(), 0, "GREEN full suite passed");
        }
        return failedGate(context.job(), FeatureStage.IMPLEMENT_GREEN, gate,
                "GREEN verification did not pass");
    }

    private FeatureStageOutcome refactor(StageContext context) {
        List<String> scopes = context.inspector().tddWriteScopes(FeatureStage.IMPLEMENT_REFACTOR);
        FeatureArtifactInspector.PlanCursor before = context.inspector().nextTask();
        String completedTask;
        if (!before.complete() && List.of("green", "refactor").contains(before.status())) {
            AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
            Optional<FeatureStageOutcome> blocked = invoke(
                    context, FeatureStage.IMPLEMENT_REFACTOR, scope);
            if (blocked.isPresent()) {
                return blocked.orElseThrow();
            }
            FeatureArtifactInspector.PlanCursor after = context.inspector().nextTask();
            Optional<String> latestDone = context.inspector().lastDoneTaskId();
            if ((!after.complete() && before.taskId().equals(after.taskId()))
                    || latestDone.isEmpty() || !before.taskId().equals(latestDone.orElseThrow())) {
                return retryAgentStage(context.job(), FeatureStage.IMPLEMENT_REFACTOR,
                        "Agent did not mark exactly the completed TDD task done in plan.md");
            }
            completedTask = before.taskId();
        } else {
            Optional<String> latestDone = context.inspector().lastDoneTaskId();
            if (latestDone.isEmpty()) {
                return waitingHuman(context.job(), FeatureStage.IMPLEMENT_REFACTOR,
                        "REFACTOR recovery cannot identify the completed task");
            }
            completedTask = latestDone.orElseThrow();
        }
        ContainerGateResult gate = container.run(RootlessContainerGateRunner.Profile.FULL,
                context.worktree());
        context.cancellation().check();
        if (!gate.passed()) {
            return failedGate(context.job(), FeatureStage.IMPLEMENT_REFACTOR, gate,
                    "REFACTOR verification did not pass");
        }
        context.inspector().appendEvidence("REFACTOR " + completedTask, gate, "pending");
        return transition(context.job(), FeatureStage.PUBLISH_TASK,
                context.job().progress().gateRound(), 0,
                "Verified task is ready for controlled publication");
    }

    private FeatureStageOutcome publishTask(StageContext context) {
        Optional<String> completed = context.inspector().lastDoneTaskId();
        if (completed.isEmpty()) {
            return failedFinal(context.job(),
                    "Task publication cannot identify the verified completed task");
        }
        List<String> scopes = context.inspector().tddWriteScopes(FeatureStage.IMPLEMENT_REFACTOR);
        FeatureArtifactInspector.PlanCursor after = context.inspector().nextTask();
        return publishCompletedTask(context, scopes, completed.orElseThrow(), after);
    }

    private FeatureStageOutcome publishCompletedTask(StageContext context, List<String> scopes,
                                                     String taskId,
                                                     FeatureArtifactInspector.PlanCursor after) {
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
        FeatureStage next = after.complete() ? FeatureStage.REVIEW_R3 : FeatureStage.IMPLEMENT_RED;
        int round = after.complete() ? Math.max(1, context.job().progress().gateRound())
                : context.job().progress().gateRound();
        Publication publication = publish(context, context.inspector().artifactWriteScope(),
                "docs(devflow): record task verification evidence", next, false);
        if (!publication.success()) {
            return publication.failure().orElseThrow();
        }
        FeatureJobMutation mutation = new FeatureJobMutation(next, null, round, 0,
                after.complete() ? "Implementation tasks complete" : "Advance to the next TDD task");
        return new FeatureStageOutcome(mutation, Optional.of(publication.binding().orElseThrow()));
    }

    private FeatureStageOutcome reviewR3(StageContext context) {
        int round = reviewRound(context.job());
        String reviewPath = context.inspector().reviewPath(FeatureStage.REVIEW_R3, round);
        AgentScope scope = new AgentScope(List.of(reviewPath),
                context.inspector().artifactWriteScope(), context.inspector().currentEvidence());
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.REVIEW_R3, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
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
        AgentScope scope = AgentScope.same(scopes, context.inspector().currentEvidence());
        Optional<FeatureStageOutcome> blocked = invoke(context, FeatureStage.SHIP, scope);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        List<String> errors = context.inspector().validateArtifacts(FeatureStage.SHIP);
        if (!errors.isEmpty()) {
            return retryAgentStage(context.job(), FeatureStage.SHIP,
                    "SHIP artifacts failed validation: " + String.join(", ", errors));
        }
        ContainerGateResult gate = container.run(RootlessContainerGateRunner.Profile.FULL,
                context.worktree());
        context.cancellation().check();
        if (!gate.passed()) {
            return failedGate(context.job(), FeatureStage.SHIP, gate,
                    "Final SHIP verification did not pass");
        }
        context.inspector().appendEvidence("FINAL", gate, "pending");
        return publishShip(context, scopes);
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

    private Optional<FeatureStageOutcome> invoke(StageContext context, FeatureStage stage,
                                                 AgentScope scope) {
        FeatureStageAgent.Assignment assignment = new FeatureStageAgent.Assignment(
                context.job(), stage, config.componentRoot(),
                context.job().progress().taskAttempt() + 1, scope.evidence());
        context.cancellation().check();
        FeatureStageAgent.Result result = agent.execute(assignment, context.issueData().issue(),
                context.issueData().comments(), context.worktree(), scope.writeScopes());
        context.cancellation().check();
        if (result.status() != FeatureStageAgent.Status.DONE) {
            return Optional.of(waitingHuman(context.job(), stage,
                    "Agent returned " + result.status() + ": " + result.summary()));
        }
        List<String> dirty = gitPublisher.dirtyFiles(context.worktree());
        List<String> violations = FeaturePathPolicy.violations(dirty, scope.validationScopes());
        if (!violations.isEmpty()) {
            return Optional.of(failedFinal(context.job(),
                    "Agent changed paths outside the stage scope: " + String.join(", ", violations)));
        }
        return Optional.empty();
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
                    FeatureStage.WAITING_DEPENDENCY_PREFETCH, resume, job.progress().gateRound(),
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
        if (attempt >= MAX_AUTOMATED_ATTEMPTS) {
            return waitingHuman(job, stage, "Automated stage attempt limit reached: " + error);
        }
        return transition(job, stage, job.progress().gateRound(), attempt, error);
    }

    private FeatureStageOutcome reviewRework(FeatureJob job, FeatureStage author,
                                             FeatureStage reviewer, int round, String message) {
        if (round >= MAX_AUTOMATED_ATTEMPTS) {
            return waitingHuman(job, reviewer, "Automated review round limit reached: " + message);
        }
        return transition(job, author, round + 1, 0, message);
    }

    private FeatureJobMutation reviewMutation(FeatureJob job,
                                              FeatureArtifactInspector.Verdict verdict,
                                              FeatureStage next, int round, String gate) {
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            if (round >= MAX_AUTOMATED_ATTEMPTS) {
                return new FeatureJobMutation(FeatureStage.WAITING_HUMAN,
                        job.progress().stage(), round, 0,
                        gate + " automated review round limit reached");
            }
            return new FeatureJobMutation(next, null, round + 1, 0, gate + " requested rework");
        }
        int nextRound = next.isApprovalWait() ? round : 0;
        return new FeatureJobMutation(next, null, nextRound, 0, gate + " passed");
    }

    private FeatureStage r2NextStage(FeatureJob job, FeatureArtifactInspector.Verdict verdict,
                                     int round) {
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            return round >= MAX_AUTOMATED_ATTEMPTS ? FeatureStage.WAITING_HUMAN : FeatureStage.DESIGN;
        }
        return job.progress().mode().requiresApproval()
                ? FeatureStage.WAIT_R2_APPROVAL : FeatureStage.IMPLEMENT_RED;
    }

    private FeatureStage r3NextStage(FeatureJob job, FeatureArtifactInspector.Verdict verdict,
                                     int round) {
        if (verdict == FeatureArtifactInspector.Verdict.REWORK) {
            return round >= MAX_AUTOMATED_ATTEMPTS
                    ? FeatureStage.WAITING_HUMAN : FeatureStage.IMPLEMENT_RED;
        }
        return job.progress().mode().requiresApproval()
                ? FeatureStage.WAIT_R3_APPROVAL : FeatureStage.SHIP;
    }

    private FeatureStageOutcome restoreRetry(FeatureJob job) {
        FeatureStage resume = job.progress().resumeStage();
        if (resume == null) {
            return failedFinal(job, "Retryable state has no resume stage");
        }
        return transition(job, resume, job.progress().gateRound(),
                job.progress().taskAttempt(), "Retrying bounded stage");
    }

    private FeatureStageOutcome publicationFailure(FeatureJob job, FeatureStage resume,
                                                   String error, boolean retryable) {
        if (!retryable) {
            return failedFinal(job, error);
        }
        int attempt = job.progress().taskAttempt() + 1;
        if (attempt >= MAX_AUTOMATED_ATTEMPTS) {
            FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.WAITING_HUMAN,
                    resume, job.progress().gateRound(), attempt, safe(
                    "Automated publication/infrastructure retry limit reached: " + error));
            return FeatureStageOutcome.transition(mutation);
        }
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.FAILED_RETRYABLE,
                resume, job.progress().gateRound(), attempt, safe(error));
        return FeatureStageOutcome.transition(mutation);
    }

    private FeatureStageOutcome waitingHuman(FeatureJob job, FeatureStage resume, String message) {
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.WAITING_HUMAN,
                resume, job.progress().gateRound(), job.progress().taskAttempt(), safe(message));
        return FeatureStageOutcome.transition(mutation);
    }

    private FeatureStageOutcome failedFinal(FeatureJob job, String message) {
        FeatureJobMutation mutation = new FeatureJobMutation(FeatureStage.FAILED_FINAL,
                null, job.progress().gateRound(), job.progress().taskAttempt(), safe(message));
        return FeatureStageOutcome.transition(mutation);
    }

    private FeatureStageOutcome waitingState(FeatureJob job) {
        return waitingHuman(job, job.progress().stage(),
                "Worker leased a state that is not executable");
    }

    private FeatureStageOutcome transition(FeatureJob job, FeatureStage stage,
                                           int round, int attempt, String message) {
        return FeatureStageOutcome.transition(new FeatureJobMutation(
                stage, null, round, attempt, safe(message)));
    }

    private static int reviewRound(FeatureJob job) {
        return Math.max(1, job.progress().gateRound());
    }

    private static String safe(String message) {
        String value = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ').strip();
        return value.substring(0, Math.min(value.length(), 1000));
    }

    private record StageContext(FeatureJob job, IssueData issueData, Path worktree,
                                FeatureArtifactInspector inspector,
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
                                 FeaturePullRequestPublisher pullRequests) {
        /** Validate every privileged boundary. */
        public Infrastructure {
            worktrees = Objects.requireNonNull(worktrees, "worktrees must not be null");
            container = Objects.requireNonNull(container, "container must not be null");
            gitPublisher = Objects.requireNonNull(gitPublisher, "gitPublisher must not be null");
            pullRequests = Objects.requireNonNull(pullRequests, "pullRequests must not be null");
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
