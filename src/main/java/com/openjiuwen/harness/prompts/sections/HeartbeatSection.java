/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heartbeat prompt section builder.
 * <p>
 * Mirrors Python's {@code heartbeat} in
 * {@code openjiuwen.harness.prompts.sections.heartbeat}.
 */
public final class HeartbeatSection {

    private HeartbeatSection() {
    }

    private static final String CN = "# 心跳模式\n"
            + "\n"
            + "- 当前处于心跳运行模式\n"
            + "- 保持简短响应，专注于核心任务\n"
            + "- 避免冗长输出和不必要操作\n";

    private static final String EN = "# Heartbeat Mode\n"
            + "\n"
            + "- Currently in heartbeat run mode\n"
            + "- Keep responses brief and focus on the core task\n"
            + "- Avoid verbose output and unnecessary operations\n";

    private static final Map<String, String> HEARTBEAT = new LinkedHashMap<>();

    static {
        HEARTBEAT.put("cn", CN);
        HEARTBEAT.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.HEARTBEAT, HEARTBEAT, 80);
    }
}
