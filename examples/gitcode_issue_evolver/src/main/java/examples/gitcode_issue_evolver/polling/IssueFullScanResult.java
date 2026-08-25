/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.polling;

/**
 * Non-sensitive outcome of one manually requested full repository Issue scan.
 *
 * @param pages fetched GitCode pages
 * @param inspected parsed Issue summaries
 * @param eligible open summaries carrying the exact configured label
 * @param created newly admitted Jobs
 * @param existing summaries rejected by lifetime Issue admission
 * @param reconciledPullRequests review-waiting pull requests reconciled in the same iteration
 * @since 0.1.12
 */
public record IssueFullScanResult(int pages, int inspected, int eligible, int created,
                                  int existing, int reconciledPullRequests) {
}
