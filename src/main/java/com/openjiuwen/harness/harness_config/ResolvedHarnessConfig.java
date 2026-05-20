/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record ResolvedHarnessConfig(
    HarnessConfig config,
    String systemPrompt,
    List<ResolvedSection> extraSections,
    List<ResolvedFileSection> fileSections,
    Path sourcePath) {
  /** Auto-generated for codecheck compliance. */
  public ResolvedHarnessConfig {
    extraSections = extraSections == null ? List.of() : List.copyOf(new ArrayList<>(extraSections));
    fileSections = fileSections == null ? List.of() : List.copyOf(new ArrayList<>(fileSections));
    sourcePath =
        sourcePath == null
            ? Path.of(".").toAbsolutePath().normalize()
            : sourcePath.toAbsolutePath().normalize();
  }
}
