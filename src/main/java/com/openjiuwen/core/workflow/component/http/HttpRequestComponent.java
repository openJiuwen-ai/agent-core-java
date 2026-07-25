/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.http;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * HTTP request workflow component (composable wrapper).
 * <p>
 * Binds an {@link HttpComponentConfig} and creates an {@link HttpRequestExecutable}
 * for graph execution.
 * <p>
 * Mirrors Python's {@code HTTPRequestComponent}.
 *
 * @since 1.0.0
 */
public class HttpRequestComponent implements ComponentComposable {
    private final HttpComponentConfig config;

    public HttpRequestComponent(HttpComponentConfig config) {
        this.config = config;
    }

    public HttpComponentConfig getConfig() {
        return config;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new HttpRequestExecutable(config);
    }
}
