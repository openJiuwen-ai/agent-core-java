/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Coding Memory prompt section builder for DeepAgent.
 * <p>
 * Mirrors Python's {@code coding_memory} in
 * {@code openjiuwen.harness.prompts.sections.coding_memory}.
 */
public final class CodingMemorySection {

    private CodingMemorySection() {
    }

    private static final String CODING_MEMORY_READ_ONLY_CN =
            "# coding memory（只读）\n"
            + "位于 `{memory_dir}`。用 coding_memory_read 读取。不允许写入。\n";

    private static final String CODING_MEMORY_READ_ONLY_EN =
            "# coding memory (read-only)\n"
            + "At `{memory_dir}`. Use coding_memory_read to read. No writing allowed.\n";

    private static final String CODING_MEMORY_PROMPT_CN =
            "# coding memory\n"
            + "\n"
            + "你有一个基于文件的持久化记忆系统，位于 `{memory_dir}`。该目录已存在，直接用 coding_memory_write 写入。\n"
            + "用户要求记住则立即保存；要求忘记则找到并删除。\n"
            + "\n"
            + "## 记忆类型\n"
            + "\n"
            + "| 类型 | 存什么 | 何时保存 |\n"
            + "|------|--------|---------|\n"
            + "| user | 用户的角色、目标、技术背景、偏好 | 了解到用户身份或偏好时 |\n"
            + "| feedback | 用户对工作方式的纠正或认可 | 用户说\\\"不要这样做\\\"或确认某做法有效时。包含原因 |\n"
            + "| project | 项目的截止日期、决策背景等不能从代码推导的信息 | 了解到谁在做什么、为什么。相对日期转绝对日期 |\n"
            + "| reference | 外部系统的指针（Jira、Grafana、Slack 等） | 了解到外部资源位置时 |\n"
            + "\n"
            + "feedback 和 project 类型的内容结构：规则/事实 → **原因：** → **如何应用：**\n"
            + "\n"
            + "**示例：**\n"
            + "> user: 测试不要 mock 数据库，上次被坑了\n"
            + "> → [保存 feedback：集成测试必须连真实数据库。原因：mock/prod 差异掩盖了迁移问题]\n"
            + ">\n"
            + "> user: 周四后冻结非关键合并，移动端要切 release\n"
            + "> → [保存 project：合并冻结从 2026-04-10 开始。原因：移动端 release 切分支]\n"
            + "\n"
            + "## 不应保存的内容\n"
            + "\n"
            + "- 代码模式、架构、文件路径、项目结构（从代码可推导）\n"
            + "- Git 历史、最近改动（git log/blame 是权威来源）\n"
            + "- 调试方案（修复在代码中，上下文在 commit message 中）\n"
            + "- 已在项目文档中记录的内容\n"
            + "- 临时任务细节、当前会话上下文\n"
            + "\n"
            + "## 如何保存和更新记忆\n"
            + "\n"
            + "- **新建记忆**：用 `coding_memory_write` 写入独立 .md 文件，必须包含 frontmatter：\n"
            + "\n"
            + "      ---\n"
            + "      name: 记忆名称\n"
            + "      description: 一行描述，要具体\n"
            + "      type: user | feedback | project | reference\n"
            + "      ---\n"
            + "\n"
            + "      记忆内容\n"
            + "\n"
            + "- **编辑已有记忆**：用 `coding_memory_edit` 精确替换记忆文件中的指定文本（old_text → new_text）\n"
            + "- 写入前先查看上方\\\"已加载的相关记忆\\\"中是否已有可更新的条目，避免重复\n"
            + "- 系统自动索引，无需手动维护\n"
            + "\n"
            + "## 写入冲突处理\n"
            + "\n"
            + "写入时系统会自动检测与已有记忆的语义冲突：\n"
            + "\n"
            + "- `conflict_detected: true` + `conflicting_files: [\\\"file.md\\\"]` 表示与其他记忆有语义冲突\n"
            + "- 冲突时文件仍会写入（追加模式）或创建（创建模式），但返回冲突信息\n"
            + "- **解决步骤**：\n"
            + "  1. 用 `coding_memory_read` 读取冲突文件内容\n"
            + "  2. 用 `coding_memory_edit` 修改冲突内容（或删除过时记忆）\n"
            + "\n"
            + "## 访问记忆\n"
            + "\n"
            + "- 记忆可能相关时，或用户提及之前的工作时，主动检索\n"
            + "- 用户要求回忆时**必须**访问\n"
            + "- 记忆标题标注了 updated 日期，日期较早或引用文件/函数时先验证；当前用户指令始终优先于记忆\n"
            + "- 用户要求忽略记忆时，当作无记忆处理\n";

    private static final String CODING_MEMORY_PROMPT_EN =
            "# coding memory\n"
            + "\n"
            + "You have a persistent, file-based memory system at `{memory_dir}`. Write directly with coding_memory_write.\n"
            + "User asks to remember → save immediately. User asks to forget → find and remove.\n"
            + "\n"
            + "## Types of memory\n"
            + "\n"
            + "| Type | What to store | When to save |\n"
            + "|------|--------------|--------------|\n"
            + "| user | Role, goals, technical background, preferences | When you learn about user identity or preferences |\n"
            + "| feedback | Corrections or confirmations of your approach | \"don't do X\" or confirms approach worked. Include why |\n"
            + "| project | Deadlines, decisions not derivable from code | Who does what, why. Relative dates → absolute |\n"
            + "| reference | Pointers to external systems (Jira, Grafana, Slack) | When you learn about external resource locations |\n"
            + "\n"
            + "feedback/project content structure: rule/fact → **Why:** → **How to apply:**\n"
            + "\n"
            + "**Examples:**\n"
            + "> user: don't mock DB in tests, got burned last time\n"
            + "> → [save feedback: must hit real DB. Why: mock/prod divergence masked broken migration]\n"
            + ">\n"
            + "> user: freeze merges after Thursday, mobile cutting release\n"
            + "> → [save project: merge freeze 2026-04-10. Why: mobile release branch cut]\n"
            + "\n"
            + "## What NOT to save\n"
            + "- Code patterns, architecture, file paths (derivable from code)\n"
            + "- Git history (git log/blame is authoritative)\n"
            + "- Debug solutions (fix in code, context in commit msg)\n"
            + "- Already documented content; ephemeral task details\n"
            + "\n"
            + "## How to save and update memories\n"
            + "- **Create**: Write .md file via `coding_memory_write` with frontmatter:\n"
            + "\n"
            + "      ---\n"
            + "      name: memory name\n"
            + "      description: one-line, be specific\n"
            + "      type: user | feedback | project | reference\n"
            + "      ---\n"
            + "      memory content\n"
            + "\n"
            + "- **Edit existing**: Use `coding_memory_edit` to replace specific text (old_text → new_text)\n"
            + "- Before writing, check the \"Loaded relevant memories\" section above for existing entries to update\n"
            + "- Auto-indexed, no manual maintenance needed\n"
            + "\n"
            + "## Write Conflict Resolution\n"
            + "\n"
            + "The system automatically detects semantic conflicts with existing memories:\n"
            + "\n"
            + "- `conflict_detected: true` + `conflicting_files: [\\\"file.md\\\"]` means semantic conflict with existing memories\n"
            + "- When conflict is detected, the file will still be written (append mode) or created (create mode), but conflict info is returned\n"
            + "- **Resolution steps**:\n"
            + "  1. Use `coding_memory_read` to review the conflicting file\n"
            + "  2. Use `coding_memory_edit` to update the conflicting content (or remove outdated memories)\n"
            + "\n"
            + "## Accessing memories\n"
            + "- Proactively search when relevant or user references prior work\n"
            + "- **Must** access when user asks to recall\n"
            + "- Titles show updated date — verify old ones before acting; user instructions always override memories\n"
            + "- If user says ignore memories, proceed as if none exist\n";

    /**
     * Build a coding memory section with default settings.
     *
     * @return PromptSection for coding memory
     */
    public static PromptSection build() {
        return build("cn", false, "coding_memory/");
    }

    /**
     * Build a coding memory section with specified parameters.
     *
     * @param language  language code (cn or en)
     * @param readOnly  whether to use read-only mode
     * @param memoryDir memory directory path
     * @return PromptSection for coding memory
     */
    public static PromptSection build(String language, boolean readOnly, String memoryDir) {
        String template;
        if (readOnly) {
            template = "cn".equals(language) ? CODING_MEMORY_READ_ONLY_CN : CODING_MEMORY_READ_ONLY_EN;
        } else {
            template = "cn".equals(language) ? CODING_MEMORY_PROMPT_CN : CODING_MEMORY_PROMPT_EN;
        }

        // Replace {memory_dir} placeholder
        String content = template.replace("{memory_dir}", memoryDir);

        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(language, content);

        return new PromptSection(SectionName.MEMORY, contentMap, 85);
    }
}