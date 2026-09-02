/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.List;
import java.util.Map;

/**
 * Public class LoadToolsTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class LoadToolsTool {
    private final LoadHandler handler;

    /**
     * Public interface LoadHandler used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface LoadHandler {
        /**
         * load.
         * 
         * @param toolNames toolNames
         * @param isReplace isReplace
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        Map<String, Object> load(List<String> toolNames, boolean isReplace) throws Exception;
    }

    /**
     * LoadToolsTool.
     * 
     * @param handler handler
     * @since 0.1.7
     */
    public LoadToolsTool(LoadHandler handler) {
        this.handler = handler;
    }

    /**
     * invoke.
     * 
     * @param toolNames toolNames
     * @param isReplace isReplace
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput invoke(List<String> toolNames, boolean isReplace) {
        try {
            Map<String, Object> result = handler.load(toolNames != null ? toolNames : List.of(), isReplace);
            return ToolOutput.builder().success(true).data(result).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
