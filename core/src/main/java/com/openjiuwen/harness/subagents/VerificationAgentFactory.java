/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.subagent.VerificationRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory helpers for Verification subagents.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.subagents.verification_agent} in
 * {@code openjiuwen/harness/subagents/verification_agent.py}.</p>
 */
public final class VerificationAgentFactory {

    public static final String VERIFICATION_AGENT_FACTORY_NAME = "verification_agent";
    public static final String FACTORY_NAME = VERIFICATION_AGENT_FACTORY_NAME;
    public static final Map<String, String> VERIFICATION_AGENT_DESC = Map.of(
            "cn",
            "对抗性验证专家。在实现工作完成后对其进行独立测试，尝试发现边界情况、回归问题和未经测试的失败路径。"
                    + "以 VERDICT: PASS、VERDICT: FAIL 或 VERDICT: PARTIAL 结尾。",
            "en",
            "Adversarial verification specialist. Independently tests implementation work after it is complete, "
                    + "actively trying to find edge cases, regressions, and untested failure paths. "
                    + "Ends with VERDICT: PASS, VERDICT: FAIL, or VERDICT: PARTIAL."
    );

    public static final String VERIFICATION_AGENT_SYSTEM_PROMPT_EN = """
            You are an adversarial verification specialist. Your job is NOT to confirm that implementation work
            looks correct - it is to try to BREAK it. You are the last line of defense before results are reported
            to the user.

            === CRITICAL CONSTRAINTS ===
            - You CANNOT create, modify, or delete project files. /tmp is allowed for ephemeral test scripts.
            - Every check MUST have a "Command run" block with actual terminal output copied verbatim.
            - You MUST end your final response with exactly one of:
                VERDICT: PASS
                VERDICT: FAIL
                VERDICT: PARTIAL
              No markdown bold, no punctuation after the verdict word, no variation in format.

            === TWO FAILURE MODES TO RESIST ===
            1. Verification avoidance - reading code, narrating what you would test, then writing PASS without
               running anything. Reading is NOT verification. Every claim requires a command and its output.
            2. Seduced by the first 80% - seeing a passing test suite or clean output and stopping without probing
               edge cases.

            === REQUIRED BASELINE (no exceptions) ===
            1. Read AGENTS.md / README / pyproject.toml / Makefile for build and test commands.
            2. Run the build - a broken build is an automatic FAIL.
            3. Run the project test suite - failing tests are an automatic FAIL.
            4. Run linters and type-checkers (ruff, mypy, etc.).
            5. Check for regressions in code paths related to the changed files.

            Test suite results are context, not evidence. The implementer is also an LLM, so tests may rely on
            mocks, circular assertions, or happy-path coverage that proves nothing end-to-end.

            === REQUIRED ADVERSARIAL PROBES ===
            Before issuing PASS, run at least one of: boundary values, idempotency, orphan operations, or
            concurrency where applicable.

            === MANDATORY OUTPUT FORMAT ===
            Every check must use this exact structure:

            ### Check: [what you are verifying]
            **Command run:**
              [exact command executed]
            **Output observed:**
              [verbatim terminal output - do not paraphrase]
            **Result: PASS**

            or

            **Result: FAIL**
            Expected: [what should have happened]
            Actual: [what actually happened]

            A check WITHOUT a "Command run" block is treated as a SKIP, not a PASS.

            === FINAL VERDICT ===
            VERDICT: PASS    - all checks passed, adversarial probes survived
            VERDICT: FAIL    - include what failed, exact error output, and reproduction steps
            VERDICT: PARTIAL - environmental limitation only (tool unavailable, service cannot start)

            Use the literal string VERDICT: followed by exactly one of PASS, FAIL, PARTIAL.
            No markdown. No punctuation after the word. No variation.
            """.strip();

    public static final String VERIFICATION_AGENT_SYSTEM_PROMPT_CN = """
            你是一位对抗性验证专家。你的职责不是确认实现看起来正确，而是尝试将其破坏。
            你是在结果上报用户之前的最后一道防线。

            === 关键约束 ===
            - 你不能创建、修改或删除项目文件。/tmp 可用于临时测试脚本。
            - 每项检查必须包含"执行命令"块，并逐字粘贴实际终端输出。
            - 你必须以以下之一结束最终回复：
                VERDICT: PASS
                VERDICT: FAIL
                VERDICT: PARTIAL
              不得加粗，不得在判决词后加标点，不得有任何格式变体。

            === 必须抵制的两种失败模式 ===
            1. 验证规避：阅读代码、描述本应测试什么，然后在未实际运行任何内容的情况下写下 PASS。
               阅读代码不等于验证。每项断言都需要一条命令及其输出为证。
            2. 被前 80% 迷惑：看到测试通过或输出整洁就停下，而不深入探测边界情况。

            === 必要基准步骤（不得省略）===
            1. 阅读 AGENTS.md / README / pyproject.toml / Makefile，获取构建和测试命令。
            2. 运行构建，构建失败即自动 FAIL。
            3. 运行项目测试套件，测试失败即自动 FAIL。
            4. 运行代码检查和类型检查（ruff、mypy 等）。
            5. 检查与已更改文件相关的代码路径是否存在回归。

            测试套件结果只是背景，不是证据。实现者也是 LLM，其测试可能依赖 mock、循环断言或仅覆盖正常路径。

            === 必要的对抗性探测 ===
            在发出 PASS 之前，至少运行边界值、幂等性、孤立操作或适用场景下的并发探测之一。

            === 强制输出格式 ===
            每项检查必须使用以下结构：

            ### 检查：[正在验证的内容]
            **执行命令：**
              [实际执行的确切命令]
            **观察到的输出：**
              [逐字粘贴的终端输出，不得转述]
            **结果：PASS**

            或

            **结果：FAIL**
            预期：[应发生的情况]
            实际：[实际发生的情况]

            没有"执行命令"块的检查被视为跳过，而非 PASS。

            === 最终判决 ===
            VERDICT: PASS    - 所有检查通过，对抗性探测均通过
            VERDICT: FAIL    - 包括失败内容、确切错误输出和复现步骤
            VERDICT: PARTIAL - 仅限环境限制（工具不可用、服务无法启动）

            使用字面字符串 VERDICT: 后接 PASS、FAIL 或 PARTIAL 之一。
            不加 Markdown 格式，判决词后不加标点，不得有任何格式变体。
            """.strip();

    public static final Map<String, String> DEFAULT_VERIFICATION_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", VERIFICATION_AGENT_SYSTEM_PROMPT_CN,
            "en", VERIFICATION_AGENT_SYSTEM_PROMPT_EN
    );

    private VerificationAgentFactory() {
    }

    public static List<String> exports() {
        return List.of(
                "DEFAULT_VERIFICATION_AGENT_SYSTEM_PROMPT",
                "VERIFICATION_AGENT_DESC",
                "VERIFICATION_AGENT_SYSTEM_PROMPT_CN",
                "VERIFICATION_AGENT_SYSTEM_PROMPT_EN",
                "build_verification_agent_config",
                "create_verification_agent"
        );
    }

    public static String defaultSystemPrompt(String language) {
        return DEFAULT_VERIFICATION_AGENT_SYSTEM_PROMPT.get(ExploreAgent.resolveLanguage(language));
    }

    public static String defaultDescription(String language) {
        return VERIFICATION_AGENT_DESC.get(ExploreAgent.resolveLanguage(language));
    }

    public static DeepAgentConfig.SubAgentConfig buildVerificationAgentConfig() {
        return buildVerificationAgentConfig(null);
    }

    public static DeepAgentConfig.SubAgentConfig buildVerificationAgentConfig(Object model) {
        return buildVerificationAgentConfig(model, null, null, null, null, null,
                null, null, null, null, null, null, false, 40);
    }

    public static DeepAgentConfig.SubAgentConfig buildVerificationAgentConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentRail> rails,
            List<String> skills,
            Object backend,
            Object workspace,
            Object sysOperation,
            String language,
            String promptMode,
            boolean enableTaskLoop,
            int maxIterations
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        AgentCard finalCard = card == null
                ? new AgentCard(VERIFICATION_AGENT_FACTORY_NAME, VERIFICATION_AGENT_FACTORY_NAME,
                defaultDescription(resolvedLanguage))
                : card;
        List<DeepAgentRail> finalRails = rails == null
                ? List.of(new SysOperationRail(), new VerificationRail())
                : List.copyOf(rails);
        DeepAgentConfig config = baseConfig(
                model,
                finalCard,
                systemPrompt == null ? defaultSystemPrompt(resolvedLanguage) : systemPrompt,
                tools,
                finalRails,
                enableTaskLoop,
                maxIterations,
                workspace,
                skills,
                backend,
                sysOperation,
                resolvedLanguage,
                promptMode,
                mcps
        );

        DeepAgentConfig.SubAgentConfig spec = new DeepAgentConfig.SubAgentConfig(
                finalCard.getName(),
                finalCard.getDescription(),
                config.getSystemPrompt()
        );
        spec.setCard(finalCard);
        spec.setConfig(config);
        spec.setTools(tools);
        spec.setMcps(toObjectList(mcps));
        spec.setModel(model);
        spec.setRails(finalRails);
        spec.setSkills(skills);
        spec.setBackend(backend);
        spec.setWorkspace(workspace);
        spec.setSysOperation(sysOperation);
        spec.setLanguage(resolvedLanguage);
        spec.setPromptMode(promptMode);
        spec.setEnableTaskLoop(enableTaskLoop);
        spec.setMaxIterations(maxIterations);
        spec.setFactoryName(VERIFICATION_AGENT_FACTORY_NAME);
        spec.setMetadata(ExploreAgent.metadata(VERIFICATION_AGENT_FACTORY_NAME, maxIterations, mcps));
        return spec;
    }

    public static DeepAgent createVerificationAgent(Object model) {
        return createVerificationAgent(model, null, null, null, null, null,
                null, false, 40, null, null, null, null, null, null);
    }

    public static DeepAgent createVerificationAgent(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<McpServerConfig> mcps,
            List<DeepAgentConfig.SubAgentConfig> subagents,
            List<DeepAgentRail> rails,
            boolean enableTaskLoop,
            int maxIterations,
            Object workspace,
            List<String> skills,
            Object backend,
            Object sysOperation,
            String language,
            String promptMode
    ) {
        String resolvedLanguage = ExploreAgent.resolveLanguage(language);
        DeepAgentConfig.SubAgentConfig spec = buildVerificationAgentConfig(
                model,
                card,
                systemPrompt,
                tools,
                mcps,
                rails,
                skills,
                backend,
                workspace,
                sysOperation,
                resolvedLanguage,
                promptMode,
                enableTaskLoop,
                maxIterations
        );
        spec.getConfig().setSubagents(toSubagentMap(subagents));
        DeepAgent agent = new DeepAgent(spec.getCard());
        agent.configure(spec.getConfig());
        return agent;
    }

    private static DeepAgentConfig baseConfig(
            Object model,
            AgentCard card,
            String systemPrompt,
            List<Tool> tools,
            List<DeepAgentRail> rails,
            boolean enableTaskLoop,
            int maxIterations,
            Object workspace,
            List<String> skills,
            Object backend,
            Object sysOperation,
            String language,
            String promptMode,
            List<McpServerConfig> mcps
    ) {
        DeepAgentConfig config = ExploreAgent.baseConfig(
                model,
                card,
                systemPrompt,
                tools,
                rails,
                language,
                enableTaskLoop
        );
        config.setCard(card);
        config.setMaxIterations(maxIterations);
        config.setWorkspace(workspace);
        config.setSkills(skills);
        config.setBackend(backend);
        config.setSysOperation(sysOperation);
        config.setPromptMode(promptMode);
        config.setMcps(toObjectList(mcps));
        return config;
    }

    private static Map<String, DeepAgentConfig.SubAgentConfig> toSubagentMap(
            List<DeepAgentConfig.SubAgentConfig> subagents
    ) {
        Map<String, DeepAgentConfig.SubAgentConfig> result = new LinkedHashMap<>();
        if (subagents == null) {
            return result;
        }
        for (DeepAgentConfig.SubAgentConfig spec : subagents) {
            if (spec != null && spec.getName() != null && !spec.getName().isBlank()) {
                result.put(spec.getName(), spec);
            }
        }
        return result;
    }

    private static List<Object> toObjectList(List<?> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
