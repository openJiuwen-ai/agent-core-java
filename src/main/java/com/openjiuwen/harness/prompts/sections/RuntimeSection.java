/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime prompt section builder.
 * <p>
 * Mirrors Python's runtime section injection at startup.
 */
public final class RuntimeSection {

    private RuntimeSection() {
    }

    private static final String CN = "# 运行时信息\n"
            + "\n"
            + "- 当前工作目录为项目根目录\n"
            + "- 优先使用项目已有工具和库\n";

    private static final String EN = "# Runtime Information\n"
            + "\n"
            + "- Current working directory is the project root\n"
            + "- Prefer existing project tools and libraries\n";

    private static final Map<String, String> RUNTIME = new LinkedHashMap<>();

    static {
        RUNTIME.put("cn", CN);
        RUNTIME.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.RUNTIME, RUNTIME, 50);
    }
}
