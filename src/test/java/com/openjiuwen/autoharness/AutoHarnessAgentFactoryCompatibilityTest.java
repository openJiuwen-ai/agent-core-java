
package com.openjiuwen.autoharness;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.rails.AutoHarnessContextRail;
import com.openjiuwen.autoharness.rails.AutoHarnessExperienceRail;
import com.openjiuwen.autoharness.rails.EditSafetyRail;
import com.openjiuwen.autoharness.rails.SecurityRail;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.LspRail;
import com.openjiuwen.harness.rails.SkillUseRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.ToolTrackingRail;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.WebFetchWebpageTool;
import com.openjiuwen.harness.tools.WebFreeSearchTool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AutoHarnessAgentFactoryCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void createAutoHarnessAgentShouldInstallWritableDefaultRailsAndSubagents() {
        DeepAgent agent = AutoHarnessFactory.createAutoHarnessAgent(AutoHarnessConfig.builder()
                .workspace(tempDir.toString()).experienceDir(tempDir.resolve("experience").toString()).build());

        assertThat(agent.getCard().getName()).isEqualTo("auto-harness");
        assertThat(agent.getConfig().isEnableTaskLoop()).isTrue();
        assertThat(agent.getConfig().isEnableTaskPlanning()).isTrue();
        assertThat(agent.getConfig().isEnableAsyncSubagent()).isTrue();
        assertThat(agent.getConfig().getSysOperation()).isNotNull();
        assertTrustedLocalSysOperation(agent, tempDir);
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(ToolTrackingRail.class));
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(SysOperationRail.class));
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(AutoHarnessContextRail.class));
        assertThat(agent.getConfig().getRails()).anySatisfy(rail -> assertThat(rail).isInstanceOf(LspRail.class));
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(AutoHarnessExperienceRail.class));
        assertThat(agent.getConfig().getRails()).anySatisfy(rail -> assertThat(rail).isInstanceOf(SecurityRail.class));
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(EditSafetyRail.class));
        assertThat(agent.getConfig().getRails())
                .anySatisfy(rail -> assertThat(rail).isInstanceOf(TaskPlanningRail.class));
        assertThat(singleTaskPlanningRail(agent).isEnableProgressRepeat()).isTrue();
        SkillUseRail skills = singleSkillRail(agent);
        assertThat(skills.enabledSkills()).containsExactlyInAnyOrder("implement", "verify", "communicate");
        agent.ensureInitialized();
        assertThat(skills.registeredSkillNames()).containsExactlyInAnyOrder("implement", "verify", "communicate");
        assertThat(agent.getConfig().getSystemPrompt()).contains("Auto Harness Agent");
        assertThat(agent.getConfig().getSystemPrompt()).contains("## CI 门控规则");
        assertThat(agent.getConfig().getSystemPrompt()).contains("make check COMMITS=1");
        assertThat(agent.getConfig().getSubagents()).anySatisfy(spec -> {
            SubAgentConfig sub = (SubAgentConfig) spec;
            assertThat(sub.getAgentCard().getName()).isEqualTo("explore_agent");
            assertThat(sub.getWorkspacePath()).isEqualTo(tempDir.toString());
        });
    }

    @Test
    void createAutoHarnessAgentShouldPropagateWorkspaceOverrideToSubagents() {
        String defaultWorkspace = tempDir.resolve("default").toString();
        String taskWorkspace = tempDir.resolve("worktrees").resolve("task-1").toString();

        DeepAgent agent = AutoHarnessFactory.createAutoHarnessAgent(
                AutoHarnessConfig.builder().workspace(defaultWorkspace)
                        .experienceDir(tempDir.resolve("experience").toString()).build(),
                taskWorkspace, null, null, null, null, true, true, true);

        assertThat(agent.getWorkspace().root()).isEqualTo(Path.of(taskWorkspace).toAbsolutePath().normalize());
        assertThat(agent.getConfig().getWorkspacePath()).isEqualTo(taskWorkspace);
        assertThat(agent.getConfig().getSubagents()).isNotEmpty()
                .allSatisfy(spec -> assertThat(((SubAgentConfig) spec).getWorkspacePath()).isEqualTo(taskWorkspace));
    }

    @Test
    void createCommitAgentShouldOnlyExposeCommitAndCommunicateSkills() {
        DeepAgent agent =
            AutoHarnessFactory.createCommitAgent(AutoHarnessConfig.builder().workspace(tempDir.toString()).build(),
                    tempDir.resolve("task-1").toString());

        assertThat(agent.getConfig().isEnableTaskLoop()).isFalse();
        assertThat(agent.getConfig().isEnableTaskPlanning()).isFalse();
        assertThat(agent.getWorkspace().root()).isEqualTo(tempDir.resolve("task-1").toAbsolutePath().normalize());
        assertThat(agent.getConfig().getRails()).noneMatch(TaskPlanningRail.class::isInstance);
        assertThat(agent.getConfig().getSystemPrompt()).contains("Auto Harness Agent");
        SkillUseRail skillRail = singleSkillRail(agent);
        assertThat(skillRail.enabledSkills()).containsExactlyInAnyOrder("commit", "communicate");
        agent.ensureInitialized();
        assertThat(skillRail.registeredSkillNames()).containsExactlyInAnyOrder("commit", "communicate");
    }

    @Test
    void readonlyAgentsShouldUseReadonlyRailsAndStageSpecificSkills() {
        DeepAgent assess =
            AutoHarnessFactory.createAssessAgent(AutoHarnessConfig.builder().workspace(tempDir.toString()).build());
        DeepAgent plan =
            AutoHarnessFactory.createPlanAgent(AutoHarnessConfig.builder().workspace(tempDir.toString()).build());
        DeepAgent selector = AutoHarnessFactory
                .createSelectPipelineAgent(AutoHarnessConfig.builder().workspace(tempDir.toString()).build());

        assertReadonly(assess, "assess");
        assertReadonly(plan, "plan");
        assertReadonly(selector, "select_pipeline");
        assertAsyncSubagents(assess);
        assertAsyncSubagents(plan);
        assertAsyncSubagents(selector);
        assertTrustedLocalSysOperation(assess, tempDir);
        assertThat(assess.getConfig().getSystemPrompt()).contains("评估代理");
        assertThat(plan.getConfig().getSystemPrompt()).contains("规划代理");
        assertThat(selector.getConfig().getSystemPrompt()).contains("pipeline 选择代理");
        assertResearchToolsUseEnglish(assess);
        assertResearchToolsUseEnglish(plan);
        assertResearchToolsUseEnglish(selector);
    }

    @Test
    void evalAndLearningsAgentsShouldUseReadonlyRailsAndPythonPrompts() {
        DeepAgent eval =
            AutoHarnessFactory.createEvalAgent(AutoHarnessConfig.builder().workspace(tempDir.toString()).build());
        DeepAgent learnings = AutoHarnessFactory.createLearningsAgent(
                AutoHarnessConfig.builder().workspace(tempDir.toString()).build(), "session ok", "existing memory");

        assertReadonly(eval, "verify");
        assertReadonly(learnings, "communicate");
        assertThat(eval.getCard().getName()).isEqualTo("auto-harness-eval");
        assertThat(eval.getConfig().getSystemPrompt()).contains("评审代理");
        assertThat(learnings.getCard().getName()).isEqualTo("auto-harness-learnings");
        assertThat(learnings.getConfig().getTools()).isEmpty();
        assertThat(learnings.getConfig().isEnableAsyncSubagent()).isFalse();
        assertThat(learnings.getConfig().getSubagents()).isEmpty();
        assertThat(learnings.getConfig().getSystemPrompt()).contains("session ok");
        assertThat(learnings.getConfig().getSystemPrompt()).contains("existing memory");
    }

    @Test
    void prDraftAgentShouldOnlyExposeCommunicateSkill() {
        DeepAgent agent =
            AutoHarnessFactory.createPrDraftAgent(AutoHarnessConfig.builder().workspace(tempDir.toString()).build(),
                    tempDir.resolve("task-1").toString());

        assertReadonly(agent, "communicate");
        assertThat(agent.getCard().getName()).isEqualTo("auto-harness-pr-draft");
        assertThat(agent.getWorkspace().root()).isEqualTo(tempDir.resolve("task-1").toAbsolutePath().normalize());
        assertThat(agent.getConfig().getTools()).isEmpty();
        assertThat(agent.getConfig().isEnableAsyncSubagent()).isFalse();
        assertThat(agent.getConfig().getSubagents()).isEmpty();
        assertThat(agent.getConfig().isEnableTaskLoop()).isFalse();
        assertThat(agent.getConfig().isEnableTaskPlanning()).isFalse();
        assertThat(agent.getConfig().getSystemPrompt()).contains("GitCode PR");
    }

    @Test
    void configShouldCarryModelLanguageAndAdditionalSkillDirs() throws Exception {
        ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("test-key")
                .apiBase("https://example.invalid/v1").build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("gpt-test").temperature(0.2).build();
        Model model = new Model(clientConfig, requestConfig);
        Path extraSkills = tempDir.resolve("skills");
        Files.createDirectories(extraSkills.resolve("implement"));
        Files.writeString(extraSkills.resolve("implement").resolve("SKILL.md"), "# implement\n");
        DeepAgent agent =
            AutoHarnessFactory.createAutoHarnessAgent(AutoHarnessConfig.builder().workspace(tempDir.toString())
                    .model(model).language("en").skillsDirs(List.of(extraSkills.toString())).build());

        assertThat(agent.getConfig().getModel()).isSameAs(model);
        assertThat(agent.getConfig().getLanguage()).isEqualTo("en");
        assertThat(agent.getWorkspace().getLanguage()).isEqualTo("en");
        ReActAgentConfig runtimeConfig = (ReActAgentConfig) agent.getAgent().getConfig();
        assertThat(runtimeConfig.getModelClientConfig()).isSameAs(clientConfig);
        assertThat(runtimeConfig.getModelConfigObj()).isSameAs(requestConfig);
        assertThat(runtimeConfig.getModelProvider()).isEqualTo("OpenAI");
        assertThat(runtimeConfig.getModelName()).isEqualTo("gpt-test");
        assertThat(singleSkillRail(agent).configuredSkillDirectories())
                .contains(extraSkills.toAbsolutePath().normalize().toString());
    }

    @Test
    void skillRailShouldOnlyEnableSkillsThatExistUnderAvailableRoots() throws Exception {
        Path skillsRoot = tempDir.resolve("custom-skills");
        Path existingSkill = skillsRoot.resolve("custom");
        Files.createDirectories(existingSkill);
        Files.writeString(existingSkill.resolve("SKILL.md"), "# custom\n");
        Path missingRoot = tempDir.resolve("missing-skills");

        DeepAgent agent = AutoHarnessFactory.createAutoHarnessAgent(
                AutoHarnessConfig.builder().workspace(tempDir.toString())
                        .skillsDirs(List.of(skillsRoot.toString(), missingRoot.toString())).build(),
                null, null, List.of("custom", "missing"), null, null, true, true, true);

        SkillUseRail rail = singleSkillRail(agent);
        assertThat(rail.configuredSkillDirectories())
                .containsExactly(skillsRoot.toAbsolutePath().normalize().toString());
        assertThat(rail.enabledSkills()).containsExactly("custom");
    }

    @Test
    void factoryShouldHonorConfiguredAgentIterations() {
        AutoHarnessConfig config = AutoHarnessConfig.builder().workspace(tempDir.toString())
                .agentIterations(Map.of("implement", 31, "assess", 32, "plan", 16, "eval", 11, "select_pipeline", 12,
                        "pr_draft", 6, "learnings", 7, "explore_subagent", 21, "browser_subagent", 22))
                .build();

        DeepAgent main = AutoHarnessFactory.createAutoHarnessAgent(config);
        DeepAgent assess = AutoHarnessFactory.createAssessAgent(config);
        DeepAgent plan = AutoHarnessFactory.createPlanAgent(config);
        DeepAgent eval = AutoHarnessFactory.createEvalAgent(config);
        DeepAgent selector = AutoHarnessFactory.createSelectPipelineAgent(config);
        DeepAgent prDraft = AutoHarnessFactory.createPrDraftAgent(config, tempDir.toString());
        DeepAgent learnings = AutoHarnessFactory.createLearningsAgent(config, "results", "memories");

        assertThat(main.getConfig().getMaxIterations()).isEqualTo(31);
        assertThat(assess.getConfig().getMaxIterations()).isEqualTo(32);
        assertThat(plan.getConfig().getMaxIterations()).isEqualTo(16);
        assertThat(eval.getConfig().getMaxIterations()).isEqualTo(11);
        assertThat(selector.getConfig().getMaxIterations()).isEqualTo(12);
        assertThat(prDraft.getConfig().getMaxIterations()).isEqualTo(6);
        assertThat(learnings.getConfig().getMaxIterations()).isEqualTo(7);
        Map<String, Integer> subagentIterations =
            main.getConfig().getSubagents().stream().map(SubAgentConfig.class::cast).collect(java.util.stream.Collectors
                    .toMap(sub -> sub.getAgentCard().getName(), SubAgentConfig::getMaxIterations));
        assertThat(subagentIterations).containsEntry("explore_agent", 21);
        assertThat(subagentIterations).containsEntry("browser_agent", 22);
    }

    @Test
    void autoHarnessSystemPromptShouldRenderOptionalWisdomSection() {
        String withoutWisdom =
            AutoHarnessFactory.buildAutoHarnessSystemPrompt(AutoHarnessConfig.builder().language("cn").build());
        String withWisdom = AutoHarnessFactory.buildAutoHarnessSystemPrompt(
                AutoHarnessConfig.builder().language("cn").build(), "keep verify logs short");

        assertThat(withoutWisdom).doesNotContain("## 经验库");
        assertThat(withWisdom).contains("Auto Harness Agent");
        assertThat(withWisdom).contains("## CI 门控规则");
        assertThat(withWisdom).contains("## 经验库");
        assertThat(withWisdom).contains("keep verify logs short");
        assertThat(withWisdom.indexOf("Auto Harness Agent")).isLessThan(withWisdom.indexOf("## CI 门控规则"));
        assertThat(withWisdom.indexOf("## CI 门控规则")).isLessThan(withWisdom.indexOf("## 经验库"));
    }

    @Test
    void autoHarnessSystemPromptShouldUseConfiguredCiGateRules() throws Exception {
        Path ciGate = tempDir.resolve("custom-ci.yaml");
        Files.writeString(ciGate, "ci_gates:\n  - name: custom\n    command: \"rtk mvn test\"\n");

        String prompt = AutoHarnessFactory.buildAutoHarnessSystemPrompt(
                AutoHarnessConfig.builder().language("cn").ciGateConfig(ciGate.toString()).build());

        assertThat(prompt).contains("rtk mvn test");
        assertThat(prompt).doesNotContain("make check COMMITS=1");
    }

    @Test
    void packagePromptsShouldKeepPythonPromptStrategyGuidance() throws Exception {
        String assess = readResource("openjiuwen/auto_harness/prompts/assess.md");
        String plan = readResource("openjiuwen/auto_harness/prompts/plan.md");
        String identity = readResource("openjiuwen/auto_harness/prompts/identity.md");

        assertThat(assess).contains("优先通过 bash 工具使用");
        assertThat(assess).contains("`gh repo view`");
        assertThat(assess).contains("`gh api`");
        assertThat(assess).contains("网页搜索和页面抓取作为补充");
        assertThat(assess).contains("make check COMMITS=1");
        assertThat(assess).contains("不要运行");
        assertThat(assess).contains("No Python files selected");
        assertThat(assess).contains("uv run ruff check <files>");
        assertThat(assess).contains("uv run mypy <files>");
        assertThat(plan).contains("优先通过 bash 工具使用 `gh repo view`");
        assertThat(plan).contains("`gh api`");
        assertThat(plan).contains("网页搜索和页面抓取仅作补充");
        assertThat(plan).contains("本轮只输出 1 个任务");
        assertThat(plan).contains("数组中只能有 1 个任务对象");
        for (String content : List.of(assess, plan)) {
            assertThat(content).contains("`openjiuwen/harness/**`");
            assertThat(content).contains("`openjiuwen/core/**`");
            assertThat(content).contains("`openjiuwen/harness/cli/README.md`");
            assertThat(content).contains("`tests/**`");
            assertThat(content).contains("`examples/**`");
            assertThat(content).contains("`docs/en/`");
            assertThat(content).contains("`docs/zh/`");
            assertThat(content).contains("`openjiuwen/auto_harness/**`");
        }
        assertThat(identity).contains("优先用 `gh` 查看官方仓库");
        assertThat(identity).contains("网页搜索只作补充核对");
    }

    @Test
    void packageSkillsShouldKeepPythonPromptStrategyGuidance() throws Exception {
        String planSkill = readResource("openjiuwen/auto_harness/skills/plan/SKILL.md");
        String implementSkill = readResource("openjiuwen/auto_harness/skills/implement/SKILL.md");

        assertThat(planSkill).containsAnyOf("直接依赖关系", "直接代码依赖");
        assertThat(planSkill).containsAnyOf("同一个 worktree", "同一个 worktree 内");
        assertThat(planSkill).contains("不要拆成多个任务");
        assertThat(planSkill).containsAnyOf("链式任务组", "A -> B -> C");
        assertThat(planSkill).contains("本轮只允许输出 1 个 task");
        assertThat(planSkill).contains("JSON 数组中只能有 1 个任务对象");
        assertThat(implementSkill).contains("`openjiuwen/harness/**`");
        assertThat(implementSkill).contains("`openjiuwen/core/**`");
        assertThat(implementSkill).contains("`openjiuwen/harness/cli/README.md`");
        assertThat(implementSkill).contains("`tests/**`");
        assertThat(implementSkill).contains("`examples/**`");
        assertThat(implementSkill).contains("`docs/en/`");
        assertThat(implementSkill).contains("`docs/zh/`");
        assertThat(implementSkill).contains("`openjiuwen/auto_harness/**`");
        assertThat(implementSkill).contains("范围冲突");
    }

    @Test
    void toolTrackingRailShouldEmitToolCallAndResultChunks() {
        ToolTrackingRail rail = new ToolTrackingRail();
        RecordingSession session = new RecordingSession("s1");
        ToolCallInputs inputs =
            ToolCallInputs
                    .builder().toolName("read_file").toolArgs("{\"file_path\":\"README.md\"}").toolResult(ToolOutput
                            .builder().isSuccess(true).data(Map.of("content", "hello", "line_count", "2")).build())
                    .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().session(session).inputs(inputs).build();

        rail.beforeToolCall(ctx);
        rail.afterToolCall(ctx);

        assertThat(session.stream).hasSize(2);
        OutputSchema call = (OutputSchema) session.stream.get(0);
        OutputSchema result = (OutputSchema) session.stream.get(1);
        assertThat(call.getType()).isEqualTo("tool_call");
        Map<?, ?> callPayload = (Map<?, ?>) call.getPayload();
        Map<?, ?> toolArgs = (Map<?, ?>) callPayload.get("tool_args");
        assertThat(callPayload.get("tool_name")).isEqualTo("read_file");
        assertThat(toolArgs.get("file_path")).isEqualTo("README.md");
        assertThat(result.getType()).isEqualTo("tool_result");
        Map<?, ?> resultPayload = (Map<?, ?>) result.getPayload();
        assertThat(resultPayload.get("tool_name")).isEqualTo("read_file");
        assertThat(resultPayload.get("tool_result")).isEqualTo("hello");
        assertThat(resultPayload.get("line_count")).isEqualTo(2);
    }

    private static void assertReadonly(DeepAgent agent, String skill) {
        assertThat(agent.getConfig().getRails()).anyMatch(ToolTrackingRail.class::isInstance);
        assertThat(agent.getConfig().getRails()).anyMatch(SysOperationRail.class::isInstance);
        assertThat(agent.getConfig().getRails()).anyMatch(AutoHarnessContextRail.class::isInstance);
        assertThat(agent.getConfig().getRails()).anyMatch(LspRail.class::isInstance);
        assertThat(agent.getConfig().getRails()).anyMatch(AutoHarnessExperienceRail.class::isInstance);
        assertThat(agent.getConfig().getRails()).noneMatch(EditSafetyRail.class::isInstance);
        assertThat(agent.getConfig().getRails()).noneMatch(SecurityRail.class::isInstance);
        SkillUseRail skillRail = singleSkillRail(agent);
        assertThat(skillRail.enabledSkills()).containsExactly(skill);
        assertThat(skillRail.configuredSkillDirectories())
                .anySatisfy(dir -> assertThat(Path.of(dir).getFileName().toString()).isEqualTo("skills"));
    }

    private static SkillUseRail singleSkillRail(DeepAgent agent) {
        List<SkillUseRail> rails = agent.getConfig().getRails().stream().filter(SkillUseRail.class::isInstance)
                .map(SkillUseRail.class::cast).toList();
        assertThat(rails).hasSize(1);
        return rails.get(0);
    }

    private static TaskPlanningRail singleTaskPlanningRail(DeepAgent agent) {
        List<TaskPlanningRail> rails = agent.getConfig().getRails().stream().filter(TaskPlanningRail.class::isInstance)
                .map(TaskPlanningRail.class::cast).toList();
        assertThat(rails).hasSize(1);
        return rails.get(0);
    }

    private static void assertAsyncSubagents(DeepAgent agent) {
        assertThat(agent.getConfig().isEnableAsyncSubagent()).isTrue();
        assertThat(agent.getConfig().getSubagents().stream().map(SubAgentConfig.class::cast)
                .map(subagent -> subagent.getAgentCard().getName())).contains("explore_agent", "browser_agent");
    }

    private static void assertResearchToolsUseEnglish(DeepAgent agent) {
        assertThat(agent.getConfig().getTools()).hasAtLeastOneElementOfType(WebFreeSearchTool.class);
        assertThat(agent.getConfig().getTools()).hasAtLeastOneElementOfType(WebFetchWebpageTool.class);
        assertThat(agent.getConfig().getTools().stream().filter(WebFreeSearchTool.class::isInstance)
                .map(WebFreeSearchTool.class::cast).map(WebFreeSearchTool::getLanguage)).containsExactly("en");
        assertThat(agent.getConfig().getTools().stream().filter(WebFetchWebpageTool.class::isInstance)
                .map(WebFetchWebpageTool.class::cast).map(WebFetchWebpageTool::getLanguage)).containsExactly("en");
    }

    private static void assertTrustedLocalSysOperation(DeepAgent agent, Path expectedWorkspace) {
        SysOperation sysOperation = agent.getConfig().getSysOperation();
        assertThat(sysOperation).isNotNull();
        var result = sysOperation.shell().executeCmd("printf trusted", ".", 30, null, null);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().getStdout()).contains("trusted");
        assertThat(result.getData().getCwd()).isEqualTo(expectedWorkspace.toAbsolutePath().normalize().toString());
    }

    private static String readResource(String path) throws Exception {
        try (InputStream stream =
            AutoHarnessAgentFactoryCompatibilityTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class RecordingSession implements Session {
        private final String sessionId;
        private final List<Object> stream = new ArrayList<>();

        private RecordingSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }
    }
}
