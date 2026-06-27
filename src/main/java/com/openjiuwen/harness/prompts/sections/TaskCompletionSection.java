/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.Map;

/**
 * Task-completion system prompt section helpers.
 *
 * <p>Mirrors Python's {@code build_completion_signal_section} in
 * {@code openjiuwen/harness/prompts/sections/task_completion.py}.
 */
public final class TaskCompletionSection {

    private static final String CN_TEMPLATE =
            "\n\n## 完成信号\n"
                    + "任务完全完成后，在回复的最后一行输出 <promise>{promise}</promise>。\n"
                    + "在确认任务完成前，不要输出此标签。";
    private static final String EN_TEMPLATE =
            "\n\n## Completion Signal\n"
                    + "When the task is fully completed, output "
                    + "<promise>{promise}</promise> as the final "
                    + "line of your response. Do not output this "
                    + "tag until you are confident the task is "
                    + "complete.";
    private static final int COMPLETION_SIGNAL_PRIORITY = 85;

    private TaskCompletionSection() {
    }

    public static PromptSection buildCompletionSignalSection(String language, String completionPromise) {
        String template = "en".equals(language) ? EN_TEMPLATE : CN_TEMPLATE;
        return new PromptSection(
                SectionName.COMPLETION_SIGNAL,
                Map.of(language, template.replace("{promise}", completionPromise)),
                COMPLETION_SIGNAL_PRIORITY
        );
    }
}
