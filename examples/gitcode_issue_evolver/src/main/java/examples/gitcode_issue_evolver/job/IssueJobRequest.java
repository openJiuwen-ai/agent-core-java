/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

import java.util.Objects;

/**
 * Validated Issue data used to enqueue a durable job.
 *
 * @param deliveryId provider delivery identifier
 * @param eventType provider event type
 * @param payloadSha256 request body digest
 * @param repository repository path
 * @param issueIid Issue IID
 * @param title Issue title
 * @param issueUrl Issue URL
 * @param branch generated work branch
 * @since 0.1.12
 */
public record IssueJobRequest(
        String deliveryId,
        String eventType,
        String payloadSha256,
        String repository,
        long issueIid,
        String title,
        String issueUrl,
        String branch) {
    public IssueJobRequest(String deliveryId, String eventType, String payloadSha256,
                           String repository, long issueIid, String title,
                           String issueUrl, String branch) {
        this.deliveryId = requireText(deliveryId, "deliveryId");
        this.eventType = requireText(eventType, "eventType");
        this.payloadSha256 = requireText(payloadSha256, "payloadSha256");
        this.repository = requireText(repository, "repository");
        if (issueIid <= 0) {
            throw new IllegalArgumentException("issueIid must be positive");
        }
        this.issueIid = issueIid;
        this.title = requireText(title, "title");
        this.issueUrl = requireText(issueUrl, "issueUrl");
        this.branch = requireText(branch, "branch");
    }

    private static String requireText(String value, String name) {
        String required = Objects.requireNonNull(value, name + " must not be null");
        if (required.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return required;
    }
}
