/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.agent_mode} in
 * {@code openjiuwen/harness/prompts/tools/agent_mode.py}.
 */
public final class AgentModePromptToolProviders {

    private static final String SWITCH_MODE_DESCRIPTION_CN = """
            在 normal 与 plan 模式间切换当前会话模式。

            何时使用：
            - 用户明确要求只做规划、不做实现时，（e.g.切到 plan 模式）。
            - 你判断当前模式不适合该任务。
            - 任务的复杂度或需求发生显著变化。

            模式说明：
            - plan：规划优先。除 plan 文件外仅允许只读操作。
            - normal：完整的开发权限，可修改文件并执行命令。

            注意：
            - 在意图不明确时先用 ask_user 澄清，再切换模式。
            """;

    private static final String SWITCH_MODE_DESCRIPTION_EN = """
            Switch the current session between normal and plan modes.

            When to use:
            - Switch to plan when the user explicitly wants planning only and no implementation.
            - You determine the current mode is inappropriate for the task
            - A task's complexity or requirements have changed significantly.

            Mode characteristics:
            - plan: Structured planning before execution, read-only with plan file writing only.
            - normal: Full development actions are allowed (editing files, running commands, etc.).

            Note:
            - If intent is ambiguous, call ask_user first before switching mode.
            """;

    private static final String ENTER_PLAN_MODE_DESCRIPTION_CN = """
            初始化 plan 文件并返回文件路径。在 plan 模式下，如需生成正式计划可调用此工具；只读操作（如 gh/git 只读命令、read_file、grep 等）可直接执行，无需先调用本工具。该工具会创建一个新的 plan 文件（幂等：若已存在则直接返回路径）。
            """;

    private static final String ENTER_PLAN_MODE_DESCRIPTION_EN = """
            Initialize the plan file and return its path. In plan mode, call this tool when you need to produce a formal plan; read-only actions (e.g. read-only gh/git commands, read_file, grep) can be run directly without calling this first. Creates a new plan file (idempotent: returns the existing path if already created).
            """;

    private static final String EXIT_PLAN_MODE_DESCRIPTION_CN = """
            读取 plan 文件全文并直接返回给用户，结束规划阶段，请求用户审批是否要切换到 normal 模式执行。当你对最终 plan 文件满意时，必须调用此工具结束规划阶段。tool_result 中包含完整计划内容。
            """;

    private static final String EXIT_PLAN_MODE_DESCRIPTION_EN = """
            Read the full plan file and return the plan directly, ending the planning phase.
            Request user approval before switching to normal mode for execution.
            Call this when you are satisfied with the final plan.
            The tool result contains the complete plan content.
            """;

    private AgentModePromptToolProviders() {
    }

    public static Map<String, Object> getSwitchModeInputParams(String language) {
        String description = "en".equals(language)
                ? "Target mode: normal or plan"
                : "目标模式：normal 或 plan";
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("type", "string");
        mode.put("enum", List.of("normal", "plan"));
        mode.put("description", description);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", mode);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("mode"));
        return schema;
    }

    public static Map<String, Object> getNoArgInputParams() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        schema.put("required", List.of());
        return schema;
    }

    /**
     * Mirrors Python's {@code SwitchModeMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/agent_mode.py}.
     */
    public static final class SwitchModeMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "switch_mode";
        }

        @Override
        public String getDescription(String language) {
            return "en".equals(language) ? SWITCH_MODE_DESCRIPTION_EN : SWITCH_MODE_DESCRIPTION_CN;
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getSwitchModeInputParams(language);
        }
    }

    /**
     * Mirrors Python's {@code EnterPlanModeMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/agent_mode.py}.
     */
    public static final class EnterPlanModeMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "enter_plan_mode";
        }

        @Override
        public String getDescription(String language) {
            return "en".equals(language) ? ENTER_PLAN_MODE_DESCRIPTION_EN : ENTER_PLAN_MODE_DESCRIPTION_CN;
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getNoArgInputParams();
        }
    }

    /**
     * Mirrors Python's {@code ExitPlanModeMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/agent_mode.py}.
     */
    public static final class ExitPlanModeMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "exit_plan_mode";
        }

        @Override
        public String getDescription(String language) {
            return "en".equals(language) ? EXIT_PLAN_MODE_DESCRIPTION_EN : EXIT_PLAN_MODE_DESCRIPTION_CN;
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getNoArgInputParams();
        }
    }
}
