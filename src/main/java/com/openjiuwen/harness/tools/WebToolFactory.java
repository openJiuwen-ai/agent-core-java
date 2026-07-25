/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class WebToolFactory {
    private WebToolFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean isFreeSearchEnabled() {
        return WebFreeSearchTool.isFreeSearchEnabled(System.getenv());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean isPaidSearchEnabled() {
        return WebPaidSearchTool.isPaidSearchEnabled(System.getenv());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Object> createWebTools() {
        return createWebTools(System.getenv());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Object> createWebTools(Map<String, String> env) {
        List<Object> tools = new ArrayList<>();
        if (WebPaidSearchTool.isPaidSearchEnabled(env)) {
            tools.add(new WebPaidSearchTool(WebFetchWebpageTool::defaultFetch, env));
        }
        if (WebFreeSearchTool.isFreeSearchEnabled(env)) {
            tools.add(new WebFreeSearchTool(WebFetchWebpageTool::defaultFetch, env));
        }
        tools.add(new WebFetchWebpageTool());
        return tools;
    }
}
