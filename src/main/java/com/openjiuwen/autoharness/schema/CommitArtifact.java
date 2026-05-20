/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class CommitArtifact used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class CommitArtifact {
    private CommitFacts facts;
    @Builder.Default
    private String statusText = "";
    @Builder.Default
    private String lastCommitStat = "";
    @Builder.Default
    private String branchName = "";
    @Builder.Default
    private boolean isCommitted = false;
    @Builder.Default
    private String error = "";

}
