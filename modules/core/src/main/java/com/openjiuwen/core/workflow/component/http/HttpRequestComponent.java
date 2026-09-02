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
 * @since 0.1.7
 */
public class HttpRequestComponent implements ComponentComposable {
    private final HttpComponentConfig config;

    /**
     * HttpRequestComponent.
     * 
     * @param config config
     * @since 0.1.7
     */
    public HttpRequestComponent(HttpComponentConfig config) {
        this.config = config;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public HttpComponentConfig getConfig() {
        return config;
    }

    /**
     * toExecutable.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Executable<?, ?> toExecutable() {
        return new HttpRequestExecutable(config);
    }
}
