/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.polling;

import examples.gitcode_issue_evolver.AutoEvolvingConfig;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssuePage;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssueSummary;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.gitcode.IssueScanRequest;
import examples.gitcode_issue_evolver.job.EnqueueResult;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import examples.gitcode_issue_evolver.job.IssueJobRequest;
import examples.gitcode_issue_evolver.job.IssueScanCheckpoint;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.webhook.GitCodeWebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Polls recent GitCode Issues into the durable worker queue and reconciles review-waiting PRs.
 *
 * @since 0.1.12
 */
public final class IssuePollingCoordinator {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PR_RECONCILIATIONS = 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(IssuePollingCoordinator.class);
    private final AutoEvolvingConfig config;
    private final EvolutionJobStore store;
    private final GitCodeClient gitCode;
    private final RepositoryProfile profile;
    private final Clock clock;
    private final PollingStatus status = new PollingStatus();

    /**
     * Create a coordinator using the system UTC clock.
     *
     * @param config validated service configuration
     * @param store durable job store
     * @param gitCode configured GitCode client
     * @param profile target repository policy
     */
    public IssuePollingCoordinator(AutoEvolvingConfig config, EvolutionJobStore store,
                                   GitCodeClient gitCode, RepositoryProfile profile) {
        this(config, store, gitCode, profile, Clock.systemUTC());
    }

    IssuePollingCoordinator(AutoEvolvingConfig config, EvolutionJobStore store,
                            GitCodeClient gitCode, RepositoryProfile profile, Clock clock) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Run one bounded Issue scan followed by PR reconciliation. */
    public void runOnce() {
        Instant attempt = clock.instant();
        status.recordAttempt(attempt);
        try {
            ScanCounts counts = scanIssues(attempt);
            int reconciled = reconcilePullRequests();
            status.recordSuccess(clock.instant());
            LOGGER.info("GitCode polling completed: inspected={}, eligible={}, created={}, existing={}, prs={}",
                    counts.inspected(), counts.eligible(), counts.created(), counts.existing(), reconciled);
        } catch (RuntimeException ex) {
            status.recordFailure();
            throw ex;
        }
    }

    /** @return current non-sensitive polling status */
    public PollingStatusSnapshot status() {
        return status.snapshot();
    }

    /** @return configured fixed-delay interval in minutes */
    public int pollIntervalMinutes() {
        return config.getPollIntervalMinutes();
    }

    private ScanCounts scanIssues(Instant now) {
        String repository = profile.repository();
        String label = config.getTriggerLabel();
        Optional<IssueScanCheckpoint> stored = store.loadIssueScanCheckpoint(repository, label);
        IssueScanCheckpoint checkpoint = stored.orElseGet(() -> newCheckpoint(repository, label, now));
        if (stored.isEmpty()) {
            store.saveIssueScanCheckpoint(checkpoint);
        }
        ScanCounts counts = ScanCounts.empty();
        int page = checkpoint.nextPage();
        for (int offset = 0; offset < config.getMaxIssueScanPages(); offset++) {
            GitCodeIssuePage result = gitCode.listIssues(new IssueScanRequest(
                    checkpoint.windowStart(), checkpoint.windowEnd(), label, page, PAGE_SIZE));
            counts = counts.add(processPage(result.issues(), checkpoint));
            if (result.receivedCount() < PAGE_SIZE) {
                store.clearIssueScanCheckpoint(repository, label);
                return counts;
            }
            page++;
            store.saveIssueScanCheckpoint(new IssueScanCheckpoint(repository, label,
                    checkpoint.windowStart(), checkpoint.windowEnd(), page));
        }
        LOGGER.warn("GitCode Issue polling reached the configured page limit; next page is {}", page);
        return counts;
    }

    private IssueScanCheckpoint newCheckpoint(String repository, String label, Instant now) {
        Instant start = now.minus(Duration.ofHours(config.getIssueScanWindowHours()));
        return new IssueScanCheckpoint(repository, label, start, now, 1);
    }

    private ScanCounts processPage(List<GitCodeIssueSummary> issues, IssueScanCheckpoint checkpoint) {
        ScanCounts counts = ScanCounts.empty();
        for (GitCodeIssueSummary issue : issues) {
            counts = counts.inspectedOne();
            if (!isEligible(issue, checkpoint)) {
                continue;
            }
            EnqueueResult result = store.enqueueIssue(jobRequest(issue));
            counts = counts.eligibleOne(result.status() == EnqueueResult.Status.CREATED);
        }
        return counts;
    }

    private boolean isEligible(GitCodeIssueSummary issue, IssueScanCheckpoint checkpoint) {
        if (!issue.isOpen() || !issue.hasLabel(checkpoint.label())) {
            return false;
        }
        return !issue.createdAt().isBefore(checkpoint.windowStart())
                && !issue.createdAt().isAfter(checkpoint.windowEnd());
    }

    private IssueJobRequest jobRequest(GitCodeIssueSummary issue) {
        String canonical = profile.repository() + System.lineSeparator() + issue.iid()
                + System.lineSeparator() + issue.createdAt();
        String deliveryId = "poll:" + profile.repository().replace('/', ':') + ":" + issue.iid();
        return new IssueJobRequest(deliveryId, "issue_poll",
                GitCodeWebhookVerifier.sha256(canonical.getBytes(StandardCharsets.UTF_8)),
                profile.repository(), issue.iid(), issue.title(), issue.url(),
                profile.branchName(issue.iid(), issue.title()));
    }

    private int reconcilePullRequests() {
        List<EvolutionJob> jobs = store.listPullRequestsForReconciliation(MAX_PR_RECONCILIATIONS);
        int reconciled = 0;
        for (EvolutionJob job : jobs) {
            GitCodePullRequest pullRequest = gitCode.getPullRequest(job.pullRequestNumber());
            reconcilePullRequest(job, pullRequest);
            reconciled++;
        }
        return reconciled;
    }

    private void reconcilePullRequest(EvolutionJob job, GitCodePullRequest pullRequest) {
        if (pullRequest.isMerged()) {
            transitionTerminal(job, EvolutionJobState.MERGED);
            return;
        }
        if (pullRequest.isClosed()) {
            transitionTerminal(job, EvolutionJobState.CLOSED);
            return;
        }
        store.markPullRequestChecked(job.id(), clock.instant().toEpochMilli());
        if (!pullRequest.isOpen()) {
            LOGGER.warn("Ignored unsupported GitCode PR state for PR {}", pullRequest.number());
        }
    }

    private void transitionTerminal(EvolutionJob job, EvolutionJobState state) {
        try {
            store.transition(job.id(), job.version(), state, "PR polling reconciliation");
        } catch (IllegalStateException ex) {
            Optional<EvolutionJob> latest = store.findById(job.id());
            if (latest.isPresent() && latest.get().state() == EvolutionJobState.WAITING_REVIEW) {
                throw ex;
            }
            LOGGER.debug("PR reconciliation observed a concurrent terminal transition for job {}", job.id());
        }
    }

    private record ScanCounts(int inspected, int eligible, int created, int existing) {
        private static ScanCounts empty() {
            return new ScanCounts(0, 0, 0, 0);
        }

        private ScanCounts inspectedOne() {
            return new ScanCounts(inspected + 1, eligible, created, existing);
        }

        private ScanCounts eligibleOne(boolean isCreated) {
            int createdCount = isCreated ? created + 1 : created;
            int existingCount = isCreated ? existing : existing + 1;
            return new ScanCounts(inspected, eligible + 1, createdCount, existingCount);
        }

        private ScanCounts add(ScanCounts other) {
            return new ScanCounts(inspected + other.inspected, eligible + other.eligible,
                    created + other.created, existing + other.existing);
        }
    }
}
