/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolved data structures produced by HarnessConfigLoader.
 * <p>
 * Mirrors Python's {@code ResolvedSection}, {@code ResolvedFileSection},
 * and {@code ResolvedHarnessConfig} dataclasses.
 */

/** An inline (non-file) prompt section ready for add_section(). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResolvedSection {
    private String name;
    private int priority;
    private Map<String, String> content;
}

/** A file-backed prompt section: content to write into workspace/{filename}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResolvedFileSection {
    private String filename;
    private Map<String, String> content;
}

/**
 * Output of HarnessConfigLoader.load().
 * <p>
 * Mirrors Python's {@code ResolvedHarnessConfig}.
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
