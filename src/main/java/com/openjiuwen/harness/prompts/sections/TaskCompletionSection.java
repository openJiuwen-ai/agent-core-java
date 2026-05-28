/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Task-completion prompt section builder — completion-signal guidance.
 * <p>
 * Mirrors Python's {@code task_completion} in
 * {@code openjiuwen.harness.prompts.sections.task_completion}.
 */
public final class TaskCompletionSection {

    private static final int COMPLETION_SIGNAL_PRIORITY = 85;

    private TaskCompletionSection() {
    }

    private static final String CN_TEMPLATE =
            "\n\n## 完成信号\n"
            + "任务完全完成后，在回复的最后一行输出 "
            + "<promise>{promise}</promise>。\n"
            + "在确认任务完成前，不要输出此标签。";

    private static final String EN_TEMPLATE =
            "\n\n## Completion Signal\n"
            + "When the task is fully completed, output "
            + "<promise>{promise}</promise> as the final "
            + "line of your response. Do not output this "
            + "tag until you are confident the task is "
            + "complete.";

    private static final Map<String, String> TEMPLATE_MAP = new LinkedHashMap<>();

    static {
        TEMPLATE_MAP.put("cn", CN_TEMPLATE);
        TEMPLATE_MAP.put("en", EN_TEMPLATE);
    }

    /**
     * Build the completion-signal prompt section.
     *
     * @param language          language code, usually "cn" or "en"
     * @param completionPromise token the model must emit inside promise tag
     * @return PromptSection ready to inject into the system prompt
     */
    public static PromptSection build(String language, String completionPromise) {
        String template = TEMPLATE_MAP.getOrDefault(language, CN_TEMPLATE);
        String content = template.replace("{promise}", completionPromise);
        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(language, content);
        return new PromptSection(SectionName.COMPLETION_SIGNAL, contentMap, COMPLETION_SIGNAL_PRIORITY);
    }

    /** Build with defaults. */
    public static PromptSection build() {
        return build("cn", "TASK_DONE");
    }
}