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

/**
 * Harness config info metadata for registry entries.
 * <p>
 * Mirrors Python's {@code HarnessConfigInfo} dataclass and
 * {@code HarnessConfigRegistry} in
 * {@code openjiuwen.harness.harness_config.registry}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HarnessConfigInfo {
    private String id;
    private String name;
    private String version;
    private String packageName;
    private Path configPath;
    @Builder.Default
    private boolean enabled = true;

    // ---- Static registry ----

    private static final List<HarnessConfigInfo> CACHE = new ArrayList<>();

    /** Discover all installed harness configs (stub — no entry-point scanning in Java). */
    public static List<HarnessConfigInfo> discover() {
        return List.copyOf(CACHE);
    }

    /** Register a harness config info. */
    public static void register(HarnessConfigInfo info) {
        CACHE.removeIf(i -> i.getId().equals(info.getId()));
        CACHE.add(info);
    }

    /** Get a config by id. */
    public static HarnessConfigInfo get(String configId) {
        return CACHE.stream().filter(i -> i.getId().equals(configId)).findFirst().orElse(null);
    }
}
