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
            鍦?normal 涓?plan 妯″紡闂村垏鎹㈠綋鍓嶄細璇濇ā寮忋€?

            浣曟椂浣跨敤锛?
            - 鐢ㄦ埛鏄庣‘瑕佹眰鍙仛瑙勫垝銆佷笉鍋氬疄鐜版椂锛岋紙e.g.鍒囧埌 plan 妯″紡锛夈€?
            - 浣犲垽鏂綋鍓嶆ā寮忎笉閫傚悎璇ヤ换鍔°€?
            - 浠诲姟鐨勫鏉傚害鎴栭渶姹傚彂鐢熸樉钁楀彉鍖栥€?

            妯″紡璇存槑锛?
            - plan锛氳鍒掍紭鍏堛€傞櫎 plan 鏂囦欢澶栦粎鍏佽鍙鎿嶄綔銆?
            - normal锛氬畬鏁寸殑寮€鍙戞潈闄愶紝鍙慨鏀规枃浠跺苟鎵ц鍛戒护銆?

            娉ㄦ剰锛?
            - 鍦ㄦ剰鍥句笉鏄庣‘鏃跺厛鐢?ask_user 婢勬竻锛屽啀鍒囨崲妯″紡銆?
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
            鍒濆鍖?plan 鏂囦欢骞惰繑鍥炴枃浠惰矾寰勩€傚湪 plan 妯″紡涓嬶紝杩欏繀椤绘槸浣犵殑绗竴涓搷浣溿€?
            璇ュ伐鍏蜂細鍒涘缓涓€涓柊鐨?plan 鏂囦欢锛堝箓绛夛細鑻ュ凡瀛樺湪鍒欑洿鎺ヨ繑鍥炶矾寰勶級銆?
            """;

    private static final String ENTER_PLAN_MODE_DESCRIPTION_EN = """
            Initialize the plan file and return its path. In plan mode this must be your very first action.
            Creates a new plan file (idempotent: returns the existing path if already created).
            """;

    private static final String EXIT_PLAN_MODE_DESCRIPTION_CN = """
            璇诲彇 plan 鏂囦欢鍏ㄦ枃骞剁洿鎺ヨ繑鍥炵粰鐢ㄦ埛锛岀粨鏉熻鍒掗樁娈碉紝璇锋眰鐢ㄦ埛瀹℃壒鏄惁瑕佸垏鎹㈠埌 normal 妯″紡鎵ц銆?
            褰撲綘瀵规渶缁?plan 鏂囦欢婊℃剰鏃讹紝蹇呴』璋冪敤姝ゅ伐鍏风粨鏉熻鍒掗樁娈点€?
            tool_result 涓寘鍚畬鏁磋鍒掑唴瀹广€?
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
                : "鐩爣妯″紡锛歯ormal 鎴?plan";
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
