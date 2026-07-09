/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebToolFactory.
 * 
 * @since 0.1.7
 */
public final class WebToolFactory {
    /**
     * WebToolFactory.
     * 
     * @since 0.1.7
     */
    private WebToolFactory() {
    }

    /**
     * isFreeSearchEnabled.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static boolean isFreeSearchEnabled() {
        return WebFreeSearchTool.isFreeSearchEnabled(System.getenv());
    }

    /**
     * isPaidSearchEnabled.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static boolean isPaidSearchEnabled() {
        return WebPaidSearchTool.isPaidSearchEnabled(System.getenv());
    }

    /**
     * createWebTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> createWebTools() {
        return createWebTools(System.getenv());
    }

    /**
     * createWebTools.
     * 
     * @param env env
     * @return the result
     * @since 0.1.7
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
