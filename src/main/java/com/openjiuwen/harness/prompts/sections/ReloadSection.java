/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reload prompt section builder — context offload hint.
 * <p>
 * Mirrors Python's {@code reload} in
 * {@code openjiuwen.harness.prompts.sections.reload}.
 */
public final class ReloadSection {

    private ReloadSection() {
    }

    private static final String CN =
            "# 上下文压缩\n\n"
            + "你的上下文在过长时会被自动压缩，"
            + "并标记为[OFFLOAD: handle=<id>, type=<type>]。\n\n"
            + "如果你认为需要读取隐藏的内容，"
            + "可随时调用reload_original_context_messages工具。\n\n"
            + "请勿猜测或编造缺失的内容。\n\n"
            + "存储类型：\"in_memory\"（会话缓存）";

    private static final String EN =
            "# Context Compression\n\n"
            + "Your context will be automatically compressed when it becomes too long "
            + "and marked with [OFFLOAD: handle=<id>, type=<type>].\n\n"
            + "Call reload_original_context_messages(offload_handle=\"<id>\", "
            + "offload_type=\"<type>\"), using the exact values from the marker.\n\n"
            + "Do not guess or fabricate missing content.\n\n"
            + "Storage types: \"in_memory\" (session cache)";

    private static final Map<String, String> RELOAD = new LinkedHashMap<>();

    static {
        RELOAD.put("cn", CN);
        RELOAD.put("en", EN);
    }

    /**
     * Build a reload prompt section.
     *
     * @param language language code
     * @return PromptSection for offload hint
     */
    public static PromptSection build(String language) {
        String content = RELOAD.getOrDefault(language, EN);
        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(language, content);
        return new PromptSection("offload", contentMap, 90);
    }

    /** Build with defaults (cn). */
    public static PromptSection build() {
        return build("cn");
    }
}