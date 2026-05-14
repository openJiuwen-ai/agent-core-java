/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Minimal Java support seam for auto-harness CLI integration.
 * <p>
 * Mirrors the highest-value request/config/task resolution behavior used by
 * Python's CLI helpers in {@code openjiuwen.harness.cli.cli} and REPL entrypoints.
 */
public final class AutoHarnessCliSupport {

    private AutoHarnessCliSupport() {
    }

    public static String buildDataDir(String cliHome) {
        return Path.of(cliHome, "auto_harness").toString();
    }

    public static String buildConfigPath(String cliHome) {
        return Path.of(buildDataDir(cliHome), "config.yaml").toString();
    }

    public static List<String> resolveTasks(AutoHarnessRunRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getTask() == null || request.getTask().isBlank()) {
            return null;
        }
        return Collections.singletonList(request.getTask());
    }

    public static AutoHarnessConfig applyRequest(AutoHarnessConfig config, AutoHarnessRunRequest request) {
        if (config == null) {
            config = new AutoHarnessConfig();
        }
        return config;
    }
}
