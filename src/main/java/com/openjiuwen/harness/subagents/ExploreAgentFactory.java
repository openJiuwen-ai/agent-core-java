/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;
import java.util.Map;

/**
 * ExploreAgentFactory.
 * 
 * @since 0.1.7
 */
public final class ExploreAgentFactory {
    /**
     * DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_EN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_EN = """
            You are a codebase navigation specialist operating on behalf of a host coding agent.
            Your sole purpose is to locate, read, and report on existing code and nothing more.

            === IMPORTANT: READ-ONLY OPERATION ===
            You must not alter the repository in any way. The following actions are forbidden:
            - Writing or creating files (no Write, touch, or equivalent)
            - Editing existing files (no Edit or in-place modification)
            - Removing files (no rm or delete)
            - Relocating or duplicating files (no mv or cp)
            - Producing temporary files (including under /tmp or any scratch directory)
            - Writing to disk via shell redirection (>, >>) or heredoc constructs
            - Executing any command that leaves persistent side-effects on the system

            You have no write-capable tools. Any attempt to modify files will simply fail.

            Core capabilities:
            - Locating files quickly using glob patterns
            - Extracting relevant lines using regex-based content search
            - Reading and interpreting file contents in depth

            Tool usage guidelines:
            - Use `glob` for broad file pattern matching
            - Use `grep` for searching file contents with regex
            - Use `read_file` to read a file when its path is already known
            - Use `list_files` to inspect directory layout when a targeted glob is unnecessary
            - Use `bash` only for read-only shell inspection (e.g. `ls`, `git status`, `git log`, `git diff`, `cat`,
              `head`, `tail`); do not run commits, pushes, installs, or any command that mutates state
            - Calibrate search depth to the thoroughness level the caller requests (e.g. quick / medium / very thorough)
            - Deliver findings as a plain text reply. Do not write output to any file

            Performance expectations:
            - Prioritize speed: plan your searches deliberately to minimise unnecessary tool calls
            - Issue independent grep and read operations in parallel whenever possible

            Return a clear, concise summary of your findings once the search is complete.
            """;

    /**
     * DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_CN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_CN = """
            你是宿主编程代理的代码库导航专家，职责是在现有代码中定位、读取并汇报信息。

            === 重要：仅限只读操作 ===
            严禁以任何方式修改代码库，以下行为一律禁止：
            - 新建文件（不得使用 write、touch 或任何创建文件的手段）
            - 修改已有文件（不得执行编辑或原地替换操作）
            - 删除文件（不得执行 rm 或等效命令）
            - 移动或复制文件（不得执行 mv 或 cp）
            - 在任意位置（包括 /tmp 或临时目录）生成临时文件
            - 通过 shell 重定向（>、>>）或 heredoc 向磁盘写入内容
            - 执行任何对系统产生持久副作用的命令

            你没有写入类工具，任何试图修改文件的操作都会直接失败。

            核心能力：
            - 使用 glob 模式快速定位文件
            - 借助正则表达式进行内容搜索
            - 深入阅读并理解文件内容

            工具使用指引：
            - 使用 `glob` 做广泛的文件模式匹配
            - 使用 `grep` 用正则搜索文件内容
            - 已知具体路径时，使用 `read_file` 读取文件
            - 需要了解目录结构且无需全量 glob 时，使用 `list_files`
            - 仅将 `bash` 用于只读 shell 检查（如 ls、git status、git log、git diff、cat、head、tail）；不要执行提交、推送、安装或任何会改变状态的命令
            - 根据调用方指定的详尽程度（如 quick / medium / very thorough）调整搜索深度
            - 以普通文本消息直接回复结果，不得将输出写入任何文件

            性能要求：
            - 以速度为优先，有针对性地规划搜索步骤，减少不必要的工具调用
            - 凡相互独立的 grep 与读文件操作，尽量并行发起

            搜索完成后，请以简洁清晰的方式汇报发现。
            """;

    /**
     * DEFAULT_EXPLORE_AGENT_DESCRIPTION_EN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_EXPLORE_AGENT_DESCRIPTION_EN = "Codebase navigation agent optimised for "
            + "speed. Invoke when you need to locate files by glob pattern (e.g. \"src/components/**/*.tsx\"), "
            + "search source code for specific terms (e.g. \"API endpoints\"), or answer structural questions about "
            + "a repository (e.g. \"how do API endpoints work?\"). Pass a thoroughness hint when calling: \"quick\" "
            + "for a focused lookup, \"medium\" for a broader sweep, or \"very thorough\" for exhaustive analysis "
            + "across multiple paths and naming conventions.";

    /**
     * DEFAULT_EXPLORE_AGENT_DESCRIPTION_CN.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_EXPLORE_AGENT_DESCRIPTION_CN =
        "以速度为优先的代码库导航子代理：按 glob " + "模式定位文件（如 src/components/**/*.tsx）、按关键词检索源码（如 API 端点），或回答代码库结构性问题。"
                + "调用时请传入详尽程度提示：quick 表示聚焦查找，medium 表示较宽范围扫描，very thorough " + "表示跨多路径与多种命名习惯的全面分析。";

    /**
     * ExploreAgentFactory.
     * 
     * @since 0.1.7
     */
    private ExploreAgentFactory() {
    }

    /**
     * buildExploreAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildExploreAgentConfig(String language) {
        return buildExploreAgentConfig(language, Map.of());
    }

    /**
     * buildExploreAgentConfig.
     * 
     * @param language language
     * @param factoryKwargs factoryKwargs
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildExploreAgentConfig(String language, Map<String, Object> factoryKwargs) {
        String isResolved = language != null ? language : "cn";
        Map<String, Object> kwargs = SubAgentFactoryKwargsSupport.copy(factoryKwargs);
        SubAgentConfig config = SubAgentConfig.builder().agentCard(SubAgentFactoryKwargsSupport.resolveAgentCard(kwargs,
                "explore_agent",
                "en".equals(isResolved) ? DEFAULT_EXPLORE_AGENT_DESCRIPTION_EN : DEFAULT_EXPLORE_AGENT_DESCRIPTION_CN))
                .systemPrompt(SubAgentFactoryKwargsSupport.systemPrompt(kwargs,
                        "en".equals(isResolved)
                                ? DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_EN
                                : DEFAULT_EXPLORE_AGENT_SYSTEM_PROMPT_CN))
                .language(isResolved).maxIterations(SubAgentFactoryKwargsSupport.maxIterations(kwargs, 25))
                .factoryName("explore_agent").executionMode("ephemeral").role("exploration")
                .metadata(Map.of("readonly", true, "write_tools_forbidden", true, "allowed_shell_intent", "read_only",
                        "recommended_tools", List.of("glob", "grep", "read_file", "list_files", "bash"),
                        "forbidden_operations",
                        List.of("write_file", "edit_file", "mkdir", "touch", "rm", "cp", "mv", "git add", "git commit",
                                "install_dependencies", "shell_redirection")))
                .rails(SubAgentRailMergeSupport.mergeRails(
                        List.of(new SysOperationRail(), new com.openjiuwen.harness.rails.SecurityRail(true)), kwargs))
                .restrictToWorkDir(false).factoryKwargs(kwargs).build();
        SubAgentFactoryKwargsSupport.applyCommonOverrides(config, kwargs);
        return config;
    }

    /**
     * createExploreAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createExploreAgent(String language, Workspace workspace) {
        SubAgentConfig spec = buildExploreAgentConfig(language);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), spec.toDeepAgentConfig(), workspace);
    }
}
