/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

/**
 * Configuration for the process-global session controller.
 *
 * <p>Mirrors Python's {@code GlobalSessionConfig} in
 * {@code openjiuwen/core/session/session_controller/global_controller.py}.</p>
 */
public class GlobalSessionConfig {

    private String basePath = "./agents";

    public GlobalSessionConfig() {
    }

    public GlobalSessionConfig(String basePath) {
        this.basePath = basePath == null || basePath.isBlank() ? "./agents" : basePath;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath == null || basePath.isBlank() ? "./agents" : basePath;
    }
}
