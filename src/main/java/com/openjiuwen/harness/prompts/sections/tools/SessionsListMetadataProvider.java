/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class SessionsListMetadataProvider implements ToolMetadataProvider {
    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "sessions_list";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "List all background async tasks and their metadata.";
        }
        return "查看当前所有后台异步子任务及其元数据。";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }
}
