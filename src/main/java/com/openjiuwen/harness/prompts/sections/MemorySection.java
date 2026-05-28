/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memory prompt section builder — proactive, passive, and read-only modes.
 * <p>
 * Mirrors Python's {@code memory} in
 * {@code openjiuwen.harness.prompts.sections.memory}.
 */
public final class MemorySection {

    private MemorySection() {
    }

    private static String getBeijingDate() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    // ── Read-only mode ───────────────────────────────────────────────

    private static final String CN_READ_ONLY =
            "# 持久化存储体系（只读模式）\n"
            + "\n"
            + "### 存储层级划分\n"
            + "\n"
            + "- **会话日志：** `YYYY-MM-DD.md`（存储当日有参考价值的交互记录，包括情景记忆和任务指令。支持增量追加，确保每次操作、用户指令和情景变化都被记录。）\n"
            + "- **用户画像：** `USER.md`（稳定的身份属性与偏好信息）\n"
            + "- **知识沉淀：** `MEMORY.md`（经筛选提炼的长期背景知识，非原始流水账）\n"
            + "\n"
            + "#### 历史检索机制\n"
            + "\n"
            + " — 仅在回答**关于历史事件、日期、人物、过去对话的问题前，先调用 `memory_search` 工具检索相关记忆**\n"
            + "    - 搜索查询应包含问题中的关键信息（人名、日期、事件关键词）\n"
            + "    - 如果搜索结果不足，尝试用不同的关键词再次搜索\n"
            + "    - 基于检索到的记忆信息回答问题，不要依赖预训练知识\n"
            + "    - 对于不涉及上述历史事件、日期、人物、过去对话的问题，不要调用工具来检索记忆\n";

    private static final String EN_READ_ONLY =
            "# Persistent Storage System (Read-Only Mode)\n"
            + "\n"
            + "### Storage Hierarchy\n"
            + "\n"
            + "- **Session Log:** `YYYY-MM-DD.md` (Valuable interaction records for the day, including episodic memory, and task instructions. Supports incremental appending to ensure every operation, user instruction, and contextual change is recorded.)\n"
            + "- **User Profile:** `USER.md` (Stable identity attributes and preference information.)\n"
            + "- **Knowledge Repository:** `MEMORY.md` (Filtered and refined long-term background knowledge, not raw logs.)\n"
            + "\n"
            + "#### History Retrieval Mechanism\n"
            + "\n"
            + "- Only before answering question about historical events, dates, people, or past conversations, you can call `memory_search` first\n"
            + "    - Search query should include key information from the question (names, dates, event keywords)\n"
            + "    - If results are insufficient, retry with different keywords\n"
            + "    - Answer based on retrieved memory results, not pretraining knowledge\n"
            + "    - If question is not about historical events, dates, people, or past conversations, you should not call any memory tool to search memory\n"
            + "\n"
            + "**Note:** In cron job mode, only reading and searching memories is supported. Writing or modifying memory files is not allowed.\n";

    // ── Proactive (full) mode ────────────────────────────────────────

    private static final String CN_PROACTIVE =
            "# 持久化存储体系\n"
            + "\n"
            + "每轮对话均从空白状态启动。跨会话的信息持久化依赖于工作区文件系统。记录悄悄进行就好，不需要让用户感知到。\n"
            + "\n"
            + "### 存储层级划分\n"
            + "\n"
            + "- **会话日志：** `YYYY-MM-DD.md`（存储当日有参考价值的交互记录，包括情景记忆和任务指令。支持增量追加，确保每次操作、用户指令和情景变化都被记录。）\n"
            + "- **用户画像：** `USER.md`（稳定的身份属性与偏好信息）\n"
            + "- **知识沉淀：** `MEMORY.md`（经筛选提炼的长期背景知识，非原始流水账）\n"
            + "\n"
            + "### 核心操作规范\n"
            + "\n"
            + "- 会话本身不具备记忆能力，文件系统是唯一的信息载体。需持久化的内容务必写入文件\n"
            + "- **路径限制：** 记忆工具（write_memory/edit_memory/read_memory）操作文件时，直接给出文件名\n"
            + "- 更新 USER.md 或 MEMORY.md 时，必须先读取现有内容再执行修改\n"
            + "- **字段唯一性约束：** 每个字段仅允许出现一次。已存在字段通过 `edit_memory` 更新，新字段通过 `write_memory` 追加\n"
            + "\n"
            + "### 信息采集、存储操作与记录\n"
            + "\n"
            + "对话过程中，发现有价值的信息时，应该立即进行分类、存储，并及时记录，确保不拖延记录过程：\n"
            + "\n"
            + "1. **用户画像信息（user_profile）**：记录用户的身份信息、偏好、习惯等稳定属性，比如用户的职业、兴趣、工作模式、喜好、不满等。\n"
            + "    - **存储**：写入 `USER.md`。\n"
            + "\n"
            + "2. **情景记忆信息（episodic_memory）**：记录用户经历的具体事件或重要决策，比如用户要求完成的任务、描述的项目进展、某次事件等。\n"
            + "    - **存储**：写入 `YYYY-MM-DD.md`。\n"
            + "\n"
            + "3. **语义记忆信息（semantic_memory）**：存储背景知识、技术细节、工具相关的本地配置（SSH、摄像头等）等长期有效信息，比如项目技术栈、工具的配置等。\n"
            + "    - **存储**：写入 `MEMORY.md`。\n"
            + "\n"
            + "4. **摘要记忆（summary_memory）**：提炼对话中的关键信息，帮助后续快速回顾，比如对话中形成的重要决策、核心结论、讨论的要点等。\n"
            + "    - **存储**：写入 `YYYY-MM-DD.md`。\n"
            + "\n"
            + "5. **用户请求记录（request_memory）**：记录用户明确请求的信息，帮助后续服务，比如用户要求记住某个信息、用户要求某个动作等。\n"
            + "    - **存储**：写入 `YYYY-MM-DD.md`。\n"
            + "\n"
            + "6. **其他信息（others）**：当用户提到有价值的细节或信息时，或每次文件操作后，需要调用 write_memory 使用 append=true 参数追加记录至 YYYY-MM-DD.md。\n"
            + "    - 注意：进行信息筛选，仅需要记录有价值的信息。有价值的信息包括但不限于：用户提供的联系人信息、项目细节、任务指令、偏好、文件路径、存储位置、任何可提高效率的信息等。发现的项目背景、技术细节、工作流程等也要写入相关文件。\n"
            + "\n"
            + "#### 历史检索机制\n"
            + "\n"
            + " — 仅在回答**关于历史事件、日期、人物、过去对话的问题前，先调用 `memory_search` 工具检索相关记忆**\n"
            + "    - 搜索查询应包含问题中的关键信息（人名、日期、事件关键词）\n"
            + "    - 如果搜索结果不足，尝试用不同的关键词再次搜索\n"
            + "    - 基于检索到的记忆信息回答问题，不要依赖预训练知识\n"
            + "    - 对于不涉及上述历史事件、日期、人物、过去对话的问题，不要调用工具来检索记忆\n";

    private static final String EN_PROACTIVE =
            "# Persistent Storage System\n"
            + "\n"
            + "Each conversation session starts from a blank state. Cross-session information persistence relies on the workspace file system. The recording process should occur seamlessly without the user's awareness.\n"
            + "\n"
            + "### Storage Hierarchy\n"
            + "\n"
            + "- **Session Log:** `YYYY-MM-DD.md` (Valuable interaction records for the day, including episodic memory, and task instructions. Supports incremental appending to ensure every operation, user instruction, and contextual change is recorded.)\n"
            + "- **User Profile:** `USER.md` (Stable identity attributes and preference information.)\n"
            + "- **Knowledge Repository:** `MEMORY.md` (Filtered and refined long-term background knowledge, not raw logs.)\n"
            + "\n"
            + "### Core Operation Guidelines\n"
            + "\n"
            + " - The session itself has no memory; the file system is the only carrier. Content requiring persistence must be written to files.\n"
            + " - **Path Restriction:** Memory tools (write_memory/edit_memory/read_memory) should give file name directly when using.\n"
            + " - When updating USER.md or MEMORY.md, existing content must be read first before making modifications.\n"
            + " - **Field Uniqueness Constraint:** Each field can appear only once. Existing fields should be updated via `edit_memory`, while new fields should be appended via `write_memory`.\n"
            + "\n"
            + "### Information Collection, Storage Operations, and Recording\n"
            + "\n"
            + "When valuable information appears during the conversation, classify it and store it immediately. Do not delay recording:\n"
            + "\n"
            + "1. **User Profile Information (`user_profile`)**: Stable user attributes such as identity, preferences, habits, work style, likes/dislikes.\n"
            + "    - **Storage**: Write to `USER.md`.\n"
            + "\n"
            + "2. **Episodic Memory (`episodic_memory`)**: Specific events or important decisions, such as assigned tasks, project progress, or notable incidents.\n"
            + "    - **Storage**: Write to `YYYY-MM-DD.md`.\n"
            + "\n"
            + "3. **Semantic Memory (`semantic_memory`)**: Long-term background knowledge, technical details, and tool-related local configs (SSH, camera, etc.).\n"
            + "    - **Storage**: Write to `MEMORY.md`.\n"
            + "\n"
            + "4. **Summary Memory (`summary_memory`)**: Distilled key points from the conversation (important decisions, core conclusions, discussion highlights).\n"
            + "    - **Storage**: Write to `YYYY-MM-DD.md`.\n"
            + "\n"
            + "5. **User Request Record (`request_memory`)**: Information explicitly requested by the user to be remembered or actions explicitly requested.\n"
            + "    - **Storage**: Write to `YYYY-MM-DD.md`.\n"
            + "\n"
            + "6. **Other Information (`others`)**: Whenever the user mentions any valuable detail, or after each file operation, you need to call `write_memory` with `append=true` to append to `YYYY-MM-DD.md` immediately\n"
            + "    - Attention: You need to filter the information. Only Valuable information needs to be recorded. Valuable information include but not limited to project details, task instructions, preferences, file paths, storage locations, and any efficiency-improving details. Discovered project background, technical details, and workflows should also be written to relevant files.\n"
            + "\n"
            + "#### History Retrieval Mechanism\n"
            + "\n"
            + "- Only before answering question about historical events, dates, people, or past conversations, you can call `memory_search` first\n"
            + "    - Search query should include key information from the question (names, dates, event keywords)\n"
            + "    - If results are insufficient, retry with different keywords\n"
            + "    - Answer based on retrieved memory results, not pretraining knowledge\n"
            + "    - If question is not about historical events, dates, people, or past conversations, you should not call any memory tool to search memory\n";

    // ── Storage Management Guidelines ──────────────────────────────────

    private static final String CN_MGMT =
            "### 存储管理规范\n"
            + "\n"
            + "#### 更新规则\n"
            + "1. 更新前必须先读取现有内容\n"
            + "2. 合并新信息，避免全量覆盖\n"
            + "3. MEMORY.md 条目仅记录精炼事实，不含日期/时间戳\n"
            + "4. **USER.md 字段去重：** 已存在字段通过 `edit_memory` 更新，不存在字段通过 `write_memory` 追加\n";

    private static final String EN_MGMT =
            "### Storage Management Guidelines\n"
            + "\n"
            + "#### Update Rules\n"
            + "1. Must read existing content before updating\n"
            + "2. Merge new information, avoid full overwrites\n"
            + "3. MEMORY.md entries should only record refined facts, without dates/timestamps\n"
            + "4. **USER.md Field Deduplication:** Existing fields should be updated via `edit_memory`, non-existing fields should be appended via `write_memory`\n";

    // ── Inactive (passive) mode ──────────────────────────────────────

    private static final String CN_INACTIVE =
            "## 持久化存储体系（被动模式）\n"
            + "\n"
            + "### 存储层级划分\n"
            + "\n"
            + "- **会话日志：** `YYYY-MM-DD.md`\n"
            + "- **用户画像：** `USER.md`\n"
            + "- **知识沉淀：** `MEMORY.md`\n"
            + "\n"
            + "### 核心操作规范\n"
            + "\n"
            + "- 使用记忆工具（write_memory/edit_memory/read_memory）操作文件时，直接给出文件名\n"
            + "- 更新 USER.md 或 MEMORY.md 时，必须先读取现有内容再执行修改\n"
            + "- 已存在字段通过 `edit_memory` 更新，新字段通过 `write_memory` 追加\n"
            + "\n"
            + "### 使用原则\n"
            + "\n"
            + "- **仅在用户明确要求时记录**：当用户说\"记住\"、\"记录\"、\"保存\"或其他相同含义的关键词时，调用 write_memory 或 edit_memory 完成存储\n"
            + "- **仅在用户询问历史时搜索**：当用户要求\"回忆\"、\"查找\"以前的内容，或明确询问历史信息时，调用 memory_search 检索\n"
            + "- **仅在需要时读取记忆文件**：当回答确实依赖历史上下文时才读取 USER.md、MEMORY.md 等文件\n"
            + "- 当用户的对话信息中不包括上述关键词和场景时，不要调用任何相关的记忆工具\n"
            + "- 记录信息时，根据内容类型选择存储位置：\n"
            + "  - 用户身份/偏好 → `USER.md`\n"
            + "  - 长期知识/配置 → `MEMORY.md`\n"
            + "  - 事件/日常记录 → `YYYY-MM-DD.md`\n";

    private static final String EN_INACTIVE =
            "## Persistent Storage System (Passive Mode)\n"
            + "\n"
            + "### Storage Hierarchy\n"
            + "\n"
            + "- **Session Log:** `memory/YYYY-MM-DD.md`\n"
            + "- **User Profile:** `USER.md`\n"
            + "- **Knowledge Repository:** `MEMORY.md`\n"
            + "\n"
            + "### Core Operation Guidelines\n"
            + "\n"
            + "- Provide the file name directly when using tools (write_memory/edit_memory/read_memory) to operate memory files\n"
            + "- When updating USER.md or MEMORY.md, existing content must be read first before making modifications\n"
            + "- Existing fields should be updated via `edit_memory`, new fields via `write_memory`\n"
            + "\n"
            + "### Usage Principles\n"
            + "\n"
            + "- **Record only when the user explicitly asks**: When the user says \"remember\", \"record\", or \"save\", or other similar keywords, call write_memory or edit_memory to persist the information\n"
            + "- **Search only when the user asks about history**: When the user requests to \"recall\" or \"find\" past content, or explicitly asks about historical information, call memory_search to retrieve it\n"
            + "- **Read memory files only when needed**: Read USER.md, MEMORY.md, etc. only when the answer genuinely depends on historical context\n"
            + "- Do not call any relevant memory tool, if user's conversation content does not contain any keywords or situation mentioned above.\n"
            + "- When recording information, choose storage by content type:\n"
            + "  - User identity/preferences → `USER.md`\n"
            + "  - Long-term knowledge/config → `MEMORY.md`\n"
            + "  - Events/daily records → `YYYY-MM-DD.md`\n";

    // ── Builder ──────────────────────────────────────────────────────

    /**
     * Build a memory prompt section.
     *
     * @param language    language code
     * @param readOnly    true for cron/read-only mode
     * @param isProactive true for proactive mode, false for passive
     * @return PromptSection for memory
     */
    public static PromptSection build(String language, boolean readOnly, boolean isProactive) {
        String content;
        String today = getBeijingDate();

        if (readOnly) {
            content = "cn".equals(language) ? CN_READ_ONLY : EN_READ_ONLY;
        } else if (!isProactive) {
            content = "cn".equals(language) ? CN_INACTIVE : EN_INACTIVE;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("cn".equals(language) ? CN_PROACTIVE : EN_PROACTIVE);
            // Append management guidelines
            sb.append("\n").append("cn".equals(language) ? CN_MGMT : EN_MGMT);
            // Append date hint
            if ("cn".equals(language)) {
                sb.append("\n在操作当天的会话日志时，请使用 `").append(today).append(".md` 作为文件名。\n");
            } else {
                sb.append("\nWhen operating today's session logs file, please use `").append(today).append(".md` as the filename.\n");
            }
            content = sb.toString();
        }

        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(language, content);
        return new PromptSection(SectionName.MEMORY, contentMap, 50);
    }

    /** Build with defaults (proactive, cn). */
    public static PromptSection build() {
        return build("cn", false, true);
    }
}