/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual description and input params for sessions_list tool.
 * <p>
 * Mirrors Python's {@code SessionsListMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.session_tools}.
 */
public class SessionsListMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "查看当前所有后台异步子任务(包括运行中、已完成、失败、已取消)及其元数据");
        DESCRIPTIONS.put("en", "List all background async tasks (running, completed, failed, canceled) and its metadata");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        cnSchema.put("properties", new LinkedHashMap<>());
        cnSchema.put("required", Collections.emptyList());
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        enSchema.put("properties", new LinkedHashMap<>());
        enSchema.put("required", Collections.emptyList());
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "sessions_list";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTIONS.getOrDefault(language, DESCRIPTIONS.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return INPUT_PARAMS.getOrDefault(language, INPUT_PARAMS.get("cn"));
    }
}