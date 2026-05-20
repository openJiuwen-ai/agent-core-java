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
 * Public class PullRequestArtifact used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class PullRequestArtifact {
    @Builder.Default
    private String prUrl = "";
    @Builder.Default
    private String summary = "";
}
