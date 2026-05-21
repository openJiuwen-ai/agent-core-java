/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session-tools prompt section builder.
 * <p>
 * Mirrors Python's {@code session_tools} in
 * {@code openjiuwen.harness.prompts.sections.session_tools}.
 */
public final class SessionToolsSection {

    private SessionToolsSection() {
    }

    private static final String CN =
            "## 会话工具sessions_spawn 用于创建临时子代理，独立完成复杂任务\n"
            + "说明:\n"
            + "    - 部分会话或代码类工具返回中若含 status 为 pending（或等价字段），表示请求已受理，任务正在后台执行，并非失败。\n"
            + "    - 此时不得为「催促结果」或「以为未执行」而连续、重复发起相同或等价的 function_call（相同工具、相同意图、相同关键参数）。\n"
            + "使用场景:\n"
            + "    - 任务复杂、多步骤、可独立执行\n"
            + "    - 需要并行处理、专注推理、大量上下文 / Token\n"
            + "    - 需要沙箱安全执行（代码、搜索、格式化）\n"
            + "    - 只需最终输出，不关心中间过程\n"
            + "不使用场景:\n"
            + "    - 任务简单\n"
            + "    - 需要查看中间步骤\n"
            + "    - 拆分无收益、仅增加延迟\n"
            + "使用原则:\n"
            + "    - 竬立任务尽量并行执行\n"
            + "    - 用子代理隔离复杂任务，提升效率\n"
            + "    - 若工具返回中含 status 为 pending：用简短自然语言说明任务已在后台执行，请用户稍候或等待系统后续推送/下一轮输入；不要堆叠多余工具调用\n"
            + "    - 仅当用户明确要求重试、变更参数或取消时，再发起新的 function_call\n";

    private static final String EN =
            "## Session tools sessions_spawn is used to create temporary subagents\n"
            + "that handle isolated tasks.\n\n"
            + "When to use:\n"
            + "- Tasks that are complex, multi-step, and can be executed independently\n"
            + "- Scenarios requiring parallel processing, focused reasoning, or large context/token usage\n"
            + "- Tasks that require sandboxed execution (e.g., code execution, search, formatting)\n"
            + "- When only the final output is needed and intermediate steps are not required\n\n"
            + "When NOT to use:\n"
            + "- Tasks are simple\n"
            + "- Intermediate steps need to be observed\n"
            + "- Task decomposition provides no benefit and only adds latency\n\n"
            + "Usage Guidelines:\n"
            + "- Execute independent tasks in parallel whenever possible\n"
            + "- Use sub-agents to isolate complex tasks and improve efficiency\n"
            + "- If the tool response contains a status of pending: use brief, natural language to inform the user "
            + "that the task is being executed in the background "
            + "and ask them to wait for subsequent system notifications or the next round of input; do not stack redundant tool calls.\n"
            + "- Only initiate a new function call when the user explicitly requests a retry, changes parameters, or cancels the task.\n";

    private static final Map<String, String> SESSION_TOOLS = new LinkedHashMap<>();

    static {
        SESSION_TOOLS.put("cn", CN);
        SESSION_TOOLS.put("en", EN);
    }

    /**
     * Build a session tools prompt section.
     *
     * @param language language code
     * @return PromptSection for session tools
     */
    public static PromptSection build(String language) {
        String content = SESSION_TOOLS.getOrDefault(language, CN);
        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(language, content);
        return new PromptSection(SectionName.SESSION_TOOLS, contentMap, 85);
    }

    /** Build with defaults (cn). */
    public static PromptSection build() {
        return build("cn");
    }
}