/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * HTTP request workflow component.
 * <p>
 * Mirrors Python's {@code HTTPRequestComponent} in
 * {@code openjiuwen.core.workflow.components.tool.http.http_request_component}.
  * Python file: {@code openjiuwen/core/workflow/components/tool/http/http_request_component.py}.
 */
public class HTTPRequestComponent implements ComponentComposable {

    private final HttpComponentConfig config;
    private HTTPRequestExecutable executable;

    public HTTPRequestComponent(HttpComponentConfig config) {
        this.config = config;
    }

    public HttpComponentConfig getConfig() {
        return config;
    }

    public HTTPRequestExecutable getExecutable() {
        if (executable == null) {
            executable = (HTTPRequestExecutable) toExecutable();
        }
        return executable;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new HTTPRequestExecutable(config);
    }
}
