/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import examples.gitcode_issue_evolver.publish.PublishResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of running AutoHarness through publication for one Issue.
 *
 * @since 0.1.12
 */
public record IssueExecutionResult(boolean success, boolean retryable,
                                   Optional<PublishResult> publishResult, String error,
                                   IssueExecutionErrorCode errorCode) {
    /**
     * Create a compatibility result with a derived error category.
     *
     * @param success whether execution and publication succeeded
     * @param retryable whether infrastructure retry is allowed
     * @param publishResult optional publication result
     * @param error safe error text
     */
    public IssueExecutionResult(boolean success, boolean retryable,
                                Optional<PublishResult> publishResult, String error) {
        this(success, retryable, publishResult, error,
                success ? IssueExecutionErrorCode.NONE : IssueExecutionErrorCode.EXECUTION_FAILED);
    }

    /**
     * Create a result with an explicit stable error category.
     *
     * @param success whether execution and publication succeeded
     * @param retryable whether infrastructure retry is allowed
     * @param publishResult optional publication result
     * @param error safe error text
     * @param errorCode stable error category
     */
    public IssueExecutionResult(boolean success, boolean retryable,
                                Optional<PublishResult> publishResult, String error,
                                IssueExecutionErrorCode errorCode) {
        this.success = success;
        this.retryable = retryable;
        this.publishResult = Objects.requireNonNull(publishResult, "publishResult must not be null");
        IssueExecutionErrorCode requiredCode = Objects.requireNonNull(
                errorCode, "errorCode must not be null");
        this.errorCode = !success && requiredCode == IssueExecutionErrorCode.NONE
                ? IssueExecutionErrorCode.EXECUTION_FAILED : requiredCode;
        this.error = this.errorCode.format(error);
    }

    /**
     * Create a failed execution result before publication.
     *
     * @param error safe error text
     * @param retryable whether infrastructure retry is allowed
     * @return failed execution result
     */
    public static IssueExecutionResult failed(String error, boolean retryable) {
        return failed(IssueExecutionErrorCode.EXECUTION_FAILED, error, retryable);
    }

    /**
     * Create a failed result with an explicit stable category.
     *
     * @param errorCode stable error category
     * @param error safe error text
     * @param retryable whether infrastructure retry is allowed
     * @return typed failed result
     */
    public static IssueExecutionResult failed(IssueExecutionErrorCode errorCode,
                                              String error, boolean retryable) {
        return new IssueExecutionResult(false, retryable, Optional.empty(), error, errorCode);
    }

    /**
     * Convert a Publisher outcome without losing its failure category.
     *
     * @param result typed Publisher outcome
     * @return Issue execution outcome
     */
    public static IssueExecutionResult fromPublishResult(PublishResult result) {
        PublishResult requiredResult = Objects.requireNonNull(result, "result must not be null");
        IssueExecutionErrorCode errorCode;
        if (!requiredResult.success()) {
            errorCode = IssueExecutionErrorCode.PUBLISH_FAILED;
        } else if (!requiredResult.notificationSucceeded()) {
            errorCode = IssueExecutionErrorCode.PUBLISH_NOTIFICATION_FAILED;
        } else {
            errorCode = IssueExecutionErrorCode.NONE;
        }
        return new IssueExecutionResult(requiredResult.success(), requiredResult.retryable(),
                Optional.of(requiredResult), requiredResult.error(), errorCode);
    }

    /**
     * Stop an Issue that explicitly requests files outside the sparse checkout.
     *
     * @param paths excluded repository paths named by the Issue
     * @return non-retryable typed failure
     */
    public static IssueExecutionResult outsideSparseCheckoutScope(Iterable<String> paths) {
        StringBuilder details = new StringBuilder();
        for (String path : paths) {
            if (!details.isEmpty()) {
                details.append(", ");
            }
            details.append(path);
        }
        String suffix = details.isEmpty() ? "" : ": " + details;
        return failed(IssueExecutionErrorCode.OUTSIDE_SPARSE_CHECKOUT_SCOPE,
                "Issue request exceeds the current sparse checkout scope" + suffix, false);
    }

    /**
     * Stop an Issue whose explicitly named file does not exist in the baseline.
     *
     * @param paths missing repository-relative paths named by the Issue
     * @return non-retryable typed failure
     */
    public static IssueExecutionResult targetPathNotFound(Iterable<String> paths) {
        StringBuilder details = new StringBuilder();
        for (String path : paths) {
            if (!details.isEmpty()) {
                details.append(", ");
            }
            details.append(path);
        }
        String suffix = details.isEmpty() ? "" : ": " + details;
        return failed(IssueExecutionErrorCode.TARGET_PATH_NOT_FOUND,
                "explicit target does not exist in the baseline" + suffix, false);
    }

    /**
     * Stop an early-E2E Issue that does not name a bounded target test.
     *
     * @param detail safe target-selection failure
     * @return non-retryable typed failure
     */
    public static IssueExecutionResult earlyE2ETestTargetRequired(String detail) {
        return failed(IssueExecutionErrorCode.EARLY_E2E_TEST_TARGET_REQUIRED, detail, false);
    }
}
