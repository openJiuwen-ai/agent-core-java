/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class CommitFacts used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class CommitFacts {
    @Builder.Default
    private String branchName = "";
    @Builder.Default
    private List<String> taskDeclaredFiles = new ArrayList<>();
    @Builder.Default
    private List<String> preexistingDirtyFiles = new ArrayList<>();
    @Builder.Default
    private List<String> currentDirtyFiles = new ArrayList<>();
    @Builder.Default
    private List<String> trackedModifiedFiles = new ArrayList<>();
    @Builder.Default
    private List<String> untrackedFiles = new ArrayList<>();
    @Builder.Default
    private List<String> editedFiles = new ArrayList<>();
    @Builder.Default
    private List<String> allowedFiles = new ArrayList<>();
    @Builder.Default
    private List<String> derivedTestFiles = new ArrayList<>();
    @Builder.Default
    private List<String> legacyRelatedTestFiles = new ArrayList<>();
    @Builder.Default
    private List<String> verifyRelatedFiles = new ArrayList<>();
    @Builder.Default
    private String diffStat = "";
}
