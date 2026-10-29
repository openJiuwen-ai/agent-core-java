/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.job;

/** Durable bounded Approved Gate receipt. */
public record IssueGateReceipt(String fingerprint, String status, String profile,
                               String code, String category, boolean cached,
                               int exitCode, String outputTail, long completedAt) {
}
