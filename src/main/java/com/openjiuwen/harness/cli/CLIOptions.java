/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bundle of root CLI options.
 *
 * <p>Mirrors Python's {@code CLIOptions} in
 * {@code openjiuwen/harness/cli/cli.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CLIOptions {
    private String provider;
    private String model;
    private String apiKey;
    private String apiBase;
    private String remote;
    @Builder.Default
    private boolean verbose = false;
    private String workspace;
    private String tenantId;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
