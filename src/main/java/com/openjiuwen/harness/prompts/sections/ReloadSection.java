/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.Map;

/**
 * Context offload hint prompt section helpers.
 *
 * <p>Mirrors Python's {@code build_reload_section} in
 * {@code openjiuwen/harness/prompts/sections/reload.py}.
 */
public final class ReloadSection {

    private static final String RELOAD_HINT_CN = """
            # 上下文压缩

            你的上下文在过长时会被自动压缩，并标记为[OFFLOAD: handle=<id>, type=<type>]。

            如果你认为需要读取隐藏的内容，可随时调用reload_original_context_messages工具。

            请勿猜测或编造缺失的内容。

            存储类型："in_memory"（会话缓存）
            """;

    private static final String RELOAD_HINT_EN = """
            # Context Compression

            Your context will be automatically compressed when it becomes too long and marked with
            [OFFLOAD: handle=<id>, type=<type>].

            Call reload_original_context_messages(offload_handle="<id>", offload_type="<type>"),
            using the exact values from the marker.

            Do not guess or fabricate missing content.

            Storage types: "in_memory" (session cache)
            """;

    private ReloadSection() {
    }

    public static PromptSection buildReloadSection(String language) {
        String hint = "cn".equals(language) ? RELOAD_HINT_CN : RELOAD_HINT_EN;
        return new PromptSection(
                "offload",
                Map.of(language, hint),
                90
        );
    }
}
