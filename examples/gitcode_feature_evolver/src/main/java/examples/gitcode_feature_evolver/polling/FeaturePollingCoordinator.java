/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.polling;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.FeatureNaming;
import examples.gitcode_feature_evolver.gitcode.FeatureComment;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.FeatureIssuePage;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueScanRequest;
import examples.gitcode_feature_evolver.gitcode.FeatureIssueSummary;
import examples.gitcode_feature_evolver.gitcode.FeaturePullRequest;
import examples.gitcode_feature_evolver.job.AdmissionResult;
import examples.gitcode_feature_evolver.job.CommandResult;
import examples.gitcode_feature_evolver.job.FeatureCommand;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureJobMutation;
import examples.gitcode_feature_evolver.job.FeatureJobRequest;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.FeatureScanCheckpoint;
import examples.gitcode_feature_evolver.job.FeatureStage;
import examples.gitcode_feature_evolver.publish.FeatureStatusComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Polls updated feature Issues, authenticated comment commands, and bound pull requests.
 *
 * @since 0.1.12
 */
public final class FeaturePollingCoordinator {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_ACTIVE_JOBS = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(FeaturePollingCoordinator.class);
    private final FeatureEvolvingConfig config;
    private final FeatureJobStore store;
    private final FeatureGitCodeClient gitCode;
    private final Clock clock;
    private final FeaturePollingStatus status = new FeaturePollingStatus();

    /**
     * Create a coordinator using the system UTC clock.
     *
     * @param config validated feature configuration
     * @param store durable feature store
     * @param gitCode configured-target GitCode API
     */
    public FeaturePollingCoordinator(FeatureEvolvingConfig config, FeatureJobStore store,
                                     FeatureGitCodeClient gitCode) {
        this(config, store, gitCode, Clock.systemUTC());
    }

    FeaturePollingCoordinator(FeatureEvolvingConfig config, FeatureJobStore store,
                              FeatureGitCodeClient gitCode, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Run one bounded Issue scan, command scan, and PR reconciliation cycle. */
    public void runOnce() {
        Instant attempt = clock.instant();
        status.recordAttempt(attempt);
        try {
            ScanCounts issues = scanIssues(attempt);
            int commands = pollCommands();
            int pullRequests = reconcilePullRequests();
            String summary = "issues=" + issues.inspected() + ",admitted=" + issues.admitted()
                    + ",commands=" + commands + ",prs=" + pullRequests;
            status.recordSuccess(clock.instant(), summary);
            LOGGER.info("GitCode feature polling completed: {}", summary);
        } catch (RuntimeException ex) {
            status.recordFailure(ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** @return current non-sensitive polling status */
    public FeaturePollingStatusSnapshot status() {
        return status.snapshot();
    }

    /** @return configured fixed-delay interval */
    public int pollIntervalMinutes() {
        return config.pollIntervalMinutes();
    }

    private ScanCounts scanIssues(Instant now) {
        String repository = config.coordinates().targetRepository();
        String label = config.triggerLabel();
        Optional<FeatureScanCheckpoint> stored = store.loadCheckpoint(repository, label);
        FeatureScanCheckpoint checkpoint = stored.orElseGet(() -> newCheckpoint(repository, label, now));
        if (stored.isEmpty()) {
            store.saveCheckpoint(checkpoint);
        }
        ScanCounts counts = ScanCounts.empty();
        int page = checkpoint.nextPage();
        for (int offset = 0; offset < config.maxIssueScanPages(); offset++) {
            FeatureIssuePage result = gitCode.listIssues(new FeatureIssueScanRequest(
                    checkpoint.window(), checkpoint.label(), page, PAGE_SIZE));
            counts = counts.add(processPage(result.issues(), checkpoint));
            if (result.receivedCount() < PAGE_SIZE) {
                store.clearCheckpoint(repository, label);
                return counts;
            }
            page++;
            store.saveCheckpoint(new FeatureScanCheckpoint(repository, label, checkpoint.window(), page));
        }
        LOGGER.warn("Feature Issue polling reached the page limit; next page is {}", page);
        return counts;
    }

    private FeatureScanCheckpoint newCheckpoint(String repository, String label, Instant now) {
        FeatureScanCheckpoint.Window window = new FeatureScanCheckpoint.Window(
                now.minus(Duration.ofHours(config.issueScanWindowHours())), now);
        return new FeatureScanCheckpoint(repository, label, window, 1);
    }

    private ScanCounts processPage(List<FeatureIssueSummary> issues,
                                   FeatureScanCheckpoint checkpoint) {
        ScanCounts counts = ScanCounts.empty();
        for (FeatureIssueSummary issue : issues) {
            counts = counts.inspectedOne();
            if (!isEligible(issue, checkpoint)) {
                continue;
            }
            AdmissionResult result = store.admit(jobRequest(issue));
            counts = counts.eligibleOne(result.status() == AdmissionResult.Status.CREATED);
        }
        return counts;
    }

    private boolean isEligible(FeatureIssueSummary issue, FeatureScanCheckpoint checkpoint) {
        Instant updatedAt = issue.status().updatedAt();
        return issue.isOpen() && issue.hasLabel(checkpoint.label())
                && !updatedAt.isBefore(checkpoint.window().start())
                && !updatedAt.isAfter(checkpoint.window().end());
    }

    private FeatureJobRequest jobRequest(FeatureIssueSummary issue) {
        String repository = config.coordinates().targetRepository();
        String branch = FeatureNaming.branch(issue.iid(), issue.title());
        String artifactRoot = FeatureNaming.artifactRoot(
                config.componentRoot(), issue.iid(), issue.title());
        String canonical = repository + "\n" + issue.iid() + "\n" + issue.status().updatedAt();
        FeatureJobRequest.Delivery delivery = new FeatureJobRequest.Delivery(
                "feature-poll:" + repository.replace('/', ':') + ":" + issue.iid(),
                "feature_issue_poll", FeatureNaming.sha256(canonical));
        FeatureJob.IssueReference reference = new FeatureJob.IssueReference(
                issue.iid(), issue.title(), issue.url());
        FeatureJobRequest.Settings settings = new FeatureJobRequest.Settings(
                config.defaultWorkflowMode(), artifactRoot, clock.instant());
        return new FeatureJobRequest(delivery, repository, reference, branch, settings);
    }

    private int pollCommands() {
        int applied = 0;
        for (FeatureJob job : store.listJobsForCommandPolling(MAX_ACTIVE_JOBS)) {
            for (FeatureComment comment : gitCode.listIssueComments(job.identity().issue().iid())) {
                if (!config.approverLogins().contains(comment.authorLogin())
                        || !comment.body().strip().toLowerCase(Locale.ROOT).startsWith("/feature")) {
                    continue;
                }
                Optional<CommandResult> parsed = applyComment(job, comment);
                if (parsed.isPresent()
                        && parsed.orElseThrow().status() != CommandResult.Status.ALREADY_SEEN) {
                    acknowledge(job.identity().issue().iid(), parsed.orElseThrow());
                    applied++;
                }
            }
        }
        return applied;
    }

    private Optional<CommandResult> applyComment(FeatureJob job, FeatureComment comment) {
        try {
            FeatureCommand.Parsed parsed = FeatureCommand.Action.parse(comment.body());
            FeatureCommand.Identity identity = new FeatureCommand.Identity(
                    comment.id(), job.identity().repository(), job.identity().issue().iid());
            FeatureCommand command = new FeatureCommand(identity, comment.authorLogin(),
                    parsed.action(), parsed.reason(), comment.createdAt());
            return Optional.of(store.applyCommand(command));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void acknowledge(long issueIid, CommandResult result) {
        gitCode.commentIssue(issueIid, FeatureStatusComment.format(result));
    }

    private int reconcilePullRequests() {
        int reconciled = 0;
        for (FeatureJob job : store.listPullRequestsForReconciliation(MAX_ACTIVE_JOBS)) {
            FeaturePullRequest pullRequest = gitCode.getPullRequest(job.pullRequest().number());
            if (pullRequest.isMerged()) {
                transitionTerminal(job, FeatureStage.MERGED);
            } else if (pullRequest.isClosed()) {
                transitionTerminal(job, FeatureStage.CLOSED);
            } else {
                store.markPullRequestChecked(job.identity().id(), clock.millis());
                if (!pullRequest.isOpen()) {
                    LOGGER.warn("Ignored unsupported feature PR state for PR {}", pullRequest.number());
                }
            }
            reconciled++;
        }
        return reconciled;
    }

    private void transitionTerminal(FeatureJob job, FeatureStage terminal) {
        FeatureJob current = job;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (current.progress().stage().isTerminal()) {
                return;
            }
            try {
                store.transition(current.identity().id(), current.record().version(),
                        FeatureJobMutation.transition(
                                current, terminal, "PR polling reconciliation"));
                return;
            } catch (IllegalStateException ex) {
                Optional<FeatureJob> latest = store.findById(current.identity().id());
                if (latest.isEmpty()) {
                    throw ex;
                }
                current = latest.orElseThrow();
            }
        }
        if (!current.progress().stage().isTerminal()) {
            throw new IllegalStateException("Feature PR reconciliation changed concurrently");
        }
    }

    private record ScanCounts(int inspected, int eligible, int admitted) {
        private static ScanCounts empty() {
            return new ScanCounts(0, 0, 0);
        }

        private ScanCounts inspectedOne() {
            return new ScanCounts(inspected + 1, eligible, admitted);
        }

        private ScanCounts eligibleOne(boolean isAdmitted) {
            return new ScanCounts(inspected, eligible + 1, admitted + (isAdmitted ? 1 : 0));
        }

        private ScanCounts add(ScanCounts other) {
            return new ScanCounts(inspected + other.inspected, eligible + other.eligible,
                    admitted + other.admitted);
        }
    }
}
