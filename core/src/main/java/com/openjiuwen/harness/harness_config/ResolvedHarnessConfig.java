/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ResolvedSection} in
 * {@code openjiuwen/harness/harness_config/loader.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResolvedSection {

    private String name;

    private int priority;

    private Map<String, String> content;
}

/**
 * Mirrors Python's {@code ResolvedFileSection} in
 * {@code openjiuwen/harness/harness_config/loader.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResolvedFileSection {

    private String filename;

    private Map<String, String> content;
}

/**
 * Mirrors Python's {@code ResolvedHarnessConfig} in
 * {@code openjiuwen/harness/harness_config/loader.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedHarnessConfig {

    private HarnessConfig config;

    private String systemPrompt;

    @Builder.Default
    private List<ResolvedSection> extraSections = new ArrayList<>();

    @Builder.Default
    private List<ResolvedFileSection> fileSections = new ArrayList<>();

    @Builder.Default
    private Path sourcePath = Path.of(".");
}
