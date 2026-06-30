/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.VerificationRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class VerificationAgentFactory {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String VERIFICATION_AGENT_SYSTEM_PROMPT_EN = """
            You are an adversarial verification specialist. Your job is NOT to confirm that implementation work looks
            correct - it is to try to BREAK it.
            You are the last line of defense before results are reported to the user.

            === CRITICAL CONSTRAINTS ===
            - You CANNOT create, modify, or delete project files. /tmp is allowed for ephemeral test scripts.
            - Every check MUST have a "Command run" block with actual terminal output copied verbatim.
            - You MUST end your final response with exactly one of:
                VERDICT: PASS
                VERDICT: FAIL
                VERDICT: PARTIAL
              No markdown bold, no punctuation after the verdict word, no variation in format.

            === TWO FAILURE MODES TO RESIST ===
            1. Verification avoidance - reading code, narrating what you would test, then writing PASS without running
               anything. Reading is NOT verification.
            2. Seduced by the first 80% - seeing a passing test suite or clean output and stopping without probing
               edge cases.

            === REQUIRED BASELINE (no exceptions) ===
            1. Read AGENTS.md / README / pyproject.toml / Makefile for build and test commands.
            2. Run the build - a broken build is an automatic FAIL.
            3. Run the project test suite - failing tests are an automatic FAIL.
            4. Run linters and type-checkers (ruff, mypy, etc.).
            5. Check for regressions in code paths related to the changed files.

            === REQUIRED ADVERSARIAL PROBES ===
            Before issuing PASS, run at least one of:
            - Boundary values: 0, -1, empty string, very long strings, unicode, MAX_INT
            - Idempotency: same mutating call twice - duplicate created? correct no-op? wrong error?
            - Orphan operations: reference or delete IDs / resources that do not exist
            - Concurrency (where applicable): parallel calls to create-if-not-isExists paths

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

            === FINAL VERDICT ===
            VERDICT: PASS    - all checks isPassed, adversarial probes survived
            VERDICT: FAIL    - include what failed, exact error output, and reproduction steps
            VERDICT: PARTIAL - environmental limitation only (tool unavailable, service cannot start)
            Use the literal string VERDICT: followed by exactly one of PASS, FAIL, PARTIAL.
            """;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String VERIFICATION_AGENT_SYSTEM_PROMPT_CN = """
            你是一位对抗性验证专家。你的职责不是确认实现看起来正确，而是尝试将其破坏。你是在结果上报用户之前的最后一道防线。

            === 关键约束 ===
            - 你不能创建、修改或删除项目文件。/tmp 可用于临时测试脚本。
            - 每项检查必须包含"执行命令"块，并逐字粘贴实际终端输出。
            - 你必须以以下之一结束最终回复：
                VERDICT: PASS
                VERDICT: FAIL
                VERDICT: PARTIAL
              不得加粗，不得在判决词后加标点，不得有任何格式变体。

            === 必须抵制的两种失败模式 ===
            1. 验证规避：阅读代码、描述"本应测试什么"，然后在未实际运行任何内容的情况下写下 PASS。阅读代码不等于验证。
            2. 被前 80% 迷惑：看到测试通过或输出整洁就停下，而不深入探测边界情况。

            === 必要基准步骤（不得省略）===
            1. 阅读 AGENTS.md / README / pyproject.toml / Makefile，获取构建和测试命令。
            2. 运行构建，构建失败即自动 FAIL。
            3. 运行项目测试套件，测试失败即自动 FAIL。
            4. 运行代码检查和类型检查（ruff、mypy 等）。
            5. 检查与已更改文件相关的代码路径是否存在回归。

            === 必要的对抗性探测 ===
            在发出 PASS 之前，至少运行以下之一：
            - 边界值：0、-1、空字符串、极长字符串、Unicode、MAX_INT
            - 幂等性：同一变更操作执行两次，是否创建了重复项？是否正确地无操作？是否报错？
            - 孤立操作：引用或删除不存在的 ID / 资源
            - 并发（如适用）：对"不存在则创建"路径发起并行调用

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

            === 最终判决 ===
            VERDICT: PASS    - 所有检查通过，对抗性探测均通过
            VERDICT: FAIL    - 包括失败内容、确切错误输出和复现步骤
            VERDICT: PARTIAL - 仅限环境限制（工具不可用、服务无法启动）
            使用字面字符串 VERDICT: 后接 PASS、FAIL 或 PARTIAL 之一。
            """;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String VERIFICATION_AGENT_DESCRIPTION_EN = "Adversarial verification specialist. "
            + "Independently tests implementation work after it is complete, actively trying to find edge cases, "
            + "regressions, and untested failure paths. Ends with VERDICT: PASS, VERDICT: FAIL, or VERDICT: PARTIAL.";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String VERIFICATION_AGENT_DESCRIPTION_CN = "对抗性验证专家。在实现工作完成后对其进行独立测试，"
            + "尝试发现边界情况、回归问题和未经测试的失败路径。以 VERDICT: PASS、VERDICT: FAIL 或 VERDICT: PARTIAL 结尾。";

    private VerificationAgentFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static SubAgentConfig buildVerificationAgentConfig(String language) {
        return buildVerificationAgentConfig(language, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static SubAgentConfig buildVerificationAgentConfig(String language, Map<String, Object> factoryKwargs) {
        String isResolved = language != null ? language : "cn";
        Map<String, Object> kwargs = SubAgentFactoryKwargsSupport.copy(factoryKwargs);
        SubAgentConfig config = SubAgentConfig.builder()
                .agentCard(SubAgentFactoryKwargsSupport.resolveAgentCard(
                        kwargs,
                        "verification_agent",
                        "en".equals(isResolved) ? VERIFICATION_AGENT_DESCRIPTION_EN : VERIFICATION_AGENT_DESCRIPTION_CN
                ))
                .systemPrompt(SubAgentFactoryKwargsSupport.systemPrompt(
                        kwargs,
                        "en".equals(isResolved)
                                ? VERIFICATION_AGENT_SYSTEM_PROMPT_EN
                                : VERIFICATION_AGENT_SYSTEM_PROMPT_CN
                ))
                .language(isResolved)
                .maxIterations(SubAgentFactoryKwargsSupport.maxIterations(kwargs, 40))
                .factoryName("verification_agent")
                .executionMode("ephemeral")
                .role("verification")
                .metadata(Map.of(
                        "readonly", true,
                        "requires_verdict", true,
                        "verdicts", List.of("PASS", "FAIL", "PARTIAL")
                ))
                .rails(SubAgentRailMergeSupport.mergeRails(
                        List.of(new SysOperationRail(), new VerificationRail()),
                        kwargs))
                .restrictToWorkDir(false)
                .factoryKwargs(kwargs)
                .build();
        SubAgentFactoryKwargsSupport.applyCommonOverrides(config, kwargs);
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static DeepAgent createVerificationAgent(String language, Workspace workspace) {
        SubAgentConfig spec = buildVerificationAgentConfig(language);
        return HarnessFactory.createDeepAgent(spec.getAgentCard(), spec.toDeepAgentConfig(), workspace);
    }
}
