/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.publish;

import examples.gitcode_issue_evolver.gitcode.CreatePullRequestRequest;
import examples.gitcode_issue_evolver.gitcode.GitCodeApiException;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.gitcode.GitCodePullRequest;
import examples.gitcode_issue_evolver.profile.ChangeValidation;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Performs remote publication only after deterministic policy and CI checks.
 *
 * @since 0.1.12
 */
public final class PullRequestPublisher {
    private static final Pattern BRANCH_PATTERN =
            Pattern.compile("auto-evolving/issue-[0-9]+-[a-z0-9-]+");
    private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
    private final GitCodeClient gitCode;
    private final ForkPushGateway pushGateway;
    private final RepositoryProfile profile;
    private final List<String> assignees;

    /**
     * Create a publisher that owns all GitCode and Fork side effects.
     *
     * @param gitCode configured-target GitCode client
     * @param pushGateway robot Fork push boundary
     * @param profile repository policy
     * @param assignees required PR reviewers
     */
    public PullRequestPublisher(GitCodeClient gitCode, ForkPushGateway pushGateway,
                                RepositoryProfile profile, List<String> assignees) {
        this.gitCode = Objects.requireNonNull(gitCode, "gitCode must not be null");
        this.pushGateway = Objects.requireNonNull(pushGateway, "pushGateway must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.assignees = assignees == null ? List.of() : List.copyOf(assignees);
    }

    /**
     * Validate, push, create or reconcile a PR, and notify the original Issue.
     *
     * @param request verified publication request
     * @return typed publication result; remote failures are not reported as success
     */
    public PublishResult publish(PublishRequest request) {
        String validationError = validate(request);
        if (!validationError.isBlank()) {
            return PublishResult.failed(validationError, false);
        }
        GitCodeIssue issue;
        try {
            issue = gitCode.getIssue(request.issueIid());
        } catch (GitCodeApiException ex) {
            return PublishResult.failed(ex.getMessage(), isRetryable(ex));
        }
        if (!issue.isOpen()) {
            return PublishResult.failed("Issue is no longer open", false);
        }

        Optional<GitCodePullRequest> existing;
        try {
            existing = gitCode.findOpenPullRequest(request.issueIid(), request.branch());
        } catch (GitCodeApiException ex) {
            return PublishResult.failed(ex.getMessage(), isRetryable(ex));
        }
        if (existing.isPresent()) {
            if (!hasHeadSha(existing.get())) {
                return PublishResult.failed("existing PR head SHA is not yet available", true);
            }
            if (!sameHead(request, existing.get())) {
                return PublishResult.failed("existing PR head SHA does not match verified commit", false);
            }
            return notifyIssue(request.issueIid(), existing.get(), true);
        }

        ForkPushGateway.PushResult push = pushGateway.push(
                request.worktree(), request.branch(), request.expectedHeadSha());
        if (!push.success()) {
            return PublishResult.failed("push failed: " + push.error(), true);
        }
        if (push.headSha() == null || !push.headSha().equalsIgnoreCase(request.expectedHeadSha())) {
            return PublishResult.failed("push result did not preserve the verified commit SHA", false);
        }

        boolean draft = profile.isHighImpact(request.changedFiles());
        GitCodePullRequest pullRequest;
        try {
            pullRequest = gitCode.createPullRequest(new CreatePullRequestRequest(
                    request.issueIid(), request.title(), request.body(), request.branch(), draft, assignees));
        } catch (GitCodeApiException ex) {
            if (!ex.isUncertain()) {
                return PublishResult.failed(ex.getMessage(), isRetryable(ex));
            }
            Optional<GitCodePullRequest> reconciled;
            try {
                reconciled = gitCode.findOpenPullRequest(request.issueIid(), request.branch());
            } catch (GitCodeApiException reconcileFailure) {
                ex.addSuppressed(reconcileFailure);
                return PublishResult.failed(ex.getMessage(), true);
            }
            if (reconciled.isEmpty() || !sameHead(request, reconciled.get())) {
                return PublishResult.failed(ex.getMessage(), true);
            }
            pullRequest = reconciled.get();
        }
        if (!hasHeadSha(pullRequest)) {
            Optional<GitCodePullRequest> reconciled;
            try {
                reconciled = gitCode.findOpenPullRequest(request.issueIid(), request.branch());
            } catch (GitCodeApiException ex) {
                return PublishResult.failed(ex.getMessage(), isRetryable(ex));
            }
            if (reconciled.isEmpty() || !hasHeadSha(reconciled.get())) {
                return PublishResult.failed("created PR head SHA is not yet available", true);
            }
            if (!sameHead(request, reconciled.get())) {
                return PublishResult.failed("created PR head SHA does not match verified commit", false);
            }
            pullRequest = reconciled.get();
        } else if (!sameHead(request, pullRequest)) {
            return PublishResult.failed("created PR head SHA does not match verified commit", false);
        }
        return notifyIssue(request.issueIid(), pullRequest, false);
    }

    private PublishResult notifyIssue(long issueIid, GitCodePullRequest pullRequest, boolean reused) {
        try {
            gitCode.commentIssue(issueIid, "Automated pull request ready for review: " + pullRequest.url());
            return new PublishResult(true, false, reused, true, Optional.of(pullRequest), "");
        } catch (GitCodeApiException ex) {
            return new PublishResult(true, isRetryable(ex), reused, false,
                    Optional.of(pullRequest), ex.getMessage());
        }
    }

    private String validate(PublishRequest request) {
        if (request == null || request.jobId() == null || request.jobId().isBlank()) {
            return "jobId is required";
        }
        if (!request.ciPassed()) {
            return "CI verification did not pass";
        }
        if (request.issueIid() <= 0) {
            return "issueIid must be positive";
        }
        if (request.title() == null || request.title().isBlank()) {
            return "pull request title is required";
        }
        if (request.body() == null || request.body().isBlank()) {
            return "pull request body is required";
        }
        if (request.worktree() == null) {
            return "worktree is required";
        }
        String expectedPrefix = "auto-evolving/issue-" + request.issueIid() + "-";
        if (request.branch() == null || !request.branch().startsWith(expectedPrefix)
                || !BRANCH_PATTERN.matcher(request.branch()).matches()) {
            return "invalid automatic branch name";
        }
        if (request.expectedHeadSha() == null || !SHA_PATTERN.matcher(request.expectedHeadSha()).matches()) {
            return "expected commit SHA is invalid";
        }
        if (assignees.isEmpty()) {
            return "at least one PR assignee is required";
        }
        if (request.changedFiles().isEmpty()) {
            return "at least one changed file is required";
        }
        ChangeValidation validation = profile.validateChanges(request.changedFiles());
        return validation.allowed() ? "" : "change set contains disallowed paths: "
                + String.join(", ", validation.violations());
    }

    private static boolean sameHead(PublishRequest request, GitCodePullRequest pullRequest) {
        return pullRequest.headSha() != null
                && pullRequest.headSha().equalsIgnoreCase(request.expectedHeadSha());
    }

    private static boolean hasHeadSha(GitCodePullRequest pullRequest) {
        return pullRequest != null && pullRequest.headSha() != null && !pullRequest.headSha().isBlank();
    }

    private static boolean isRetryable(GitCodeApiException exception) {
        int status = exception.getStatusCode();
        return status == 0 || status == 429 || status >= 500;
    }
}
