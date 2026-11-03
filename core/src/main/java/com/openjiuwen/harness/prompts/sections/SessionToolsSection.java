/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.Map;

/**
 * Mirrors Python's {@code session_tools} in
 * {@code openjiuwen/harness/prompts/sections/session_tools.py}.
 */
public final class SessionToolsSection {

    private static final String DEFAULT_LANGUAGE = "cn";
    private static final int PRIORITY = 85;

    private static final String SESSION_SYSTEM_PROMPT_CN = """
## 会话工具sessions_spawn 用于创建临时子代理，独立完成复杂任务
说明:
    - 部分会话或代码类工具返回中若含 status 为 pending（或等价字段），表示请求已受理，任务正在后台执行，并非失败。
    - 此时不得为「催促结果」或「以为未执行」而连续、重复发起相同或等价的 function_call（相同工具、相同意图、相同关键参数）。
使用场景:
    - 任务复杂、多步骤、可独立执行
    - 需要并行处理、专注推理、大量上下文 / Token
    - 需要沙箱安全执行（代码、搜索、格式化）
    - 只需最终输出，不关心中间过程
不使用场景:
    - 任务简单
    - 需要查看中间步骤
    - 拆分无收益、仅增加延迟
使用原则:
    - 独立任务尽量并行执行
    - 用子代理隔离复杂任务，提升效率
    - 若工具返回中含 status 为 pending：用简短自然语言说明任务已在后台执行，请用户稍候或等待系统后续推送/下一轮输入；不要堆叠多余工具调用
    - 仅当用户明确要求重试、变更参数或取消时，再发起新的 function_call
""";

    private static final String SESSION_SYSTEM_PROMPT_EN = """
## Session tools sessions_spawn is used to create temporary subagents
that handle isolated tasks.

When to use:
- Tasks that are complex, multi-step, and can be executed independently
- Scenarios requiring parallel processing, focused reasoning, or large context/token usage
- Tasks that require sandboxed execution (e.g., code execution, search, formatting)
- When only the final output is needed and intermediate steps are not required

When NOT to use:
- Tasks are simple
- Intermediate steps need to be observed
- Task decomposition provides no benefit and only adds latency

Usage Guidelines:
- Execute independent tasks in parallel whenever possible
- Use sub-agents to isolate complex tasks and improve efficiency
- If the tool response contains a status of pending: use brief, natural language to inform the user
that the task is being executed in the background
and ask them to wait for subsequent system notifications or the next round of input; do not stack redundant tool calls.
- Only initiate a new function call when the user explicitly requests a retry, changes parameters, or cancels the task.
""";

    private static final Map<String, String> SESSION_SYSTEM_PROMPT = Map.of(
            "cn", SESSION_SYSTEM_PROMPT_CN,
            "en", SESSION_SYSTEM_PROMPT_EN
    );

    private SessionToolsSection() {
    }

    public static String buildSessionToolsSystemPrompt(String language) {
        return SESSION_SYSTEM_PROMPT.getOrDefault(language, SESSION_SYSTEM_PROMPT_CN);
    }

    public static PromptSection buildSessionToolsSection(String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : DEFAULT_LANGUAGE;
        return new PromptSection(
                SectionName.SESSION_TOOLS,
                Map.of(resolvedLanguage, buildSessionToolsSystemPrompt(resolvedLanguage)),
                PRIORITY
        );
    }

    public static PromptSection build(String language) {
        return buildSessionToolsSection(language);
    }

    public static PromptSection build() {
        return buildSessionToolsSection(DEFAULT_LANGUAGE);
    }
}
