/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.single_agent.AbilityManager;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.ContextSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.prompts.sections.WorkspaceSection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.context_engineer.ContextAssembleRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Supplemental parity tests for context assembly rail behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.test_context_assemble_rail} in
 * {@code tests/unit_tests/harness/test_context_assemble_rail.py}.</p>
 */
class ContextAssembleRailPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/test_context_assemble_rail.py";
    private static final String TIMEZONE = "Asia/Shanghai";

    @TestFactory
    Collection<DynamicTest> pythonContextAssembleRailCases() {
        return pythonTestNodes()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonTestNodes() {
        return Stream.of(
                SOURCE + "::test_build_workspace_section",
                SOURCE + "::test_build_workspace_section_returns_none_when_workspace_is_none",
                SOURCE + "::test_build_context_section",
                SOURCE + "::test_build_context_section_returns_none_when_workspace_is_none",
                SOURCE + "::test_build_context_section_skips_empty_daily_memory_dir",
                SOURCE + "::test_build_context_section_skips_when_today_daily_memory_missing",
                SOURCE + "::test_build_context_section_can_exclude_daily_memory",
                SOURCE + "::test_build_tools_content",
                SOURCE + "::test_build_context_section_with_tools_content",
                SOURCE + "::test_build_context_section_without_tools",
                SOURCE + "::test_before_model_call_injects_sections",
                SOURCE + "::test_before_model_call_heartbeat_uses_lightweight_context",
                SOURCE + "::test_before_model_call_removes_sections_when_workspace_is_none",
                SOURCE + "::test_uninit_removes_sections",
                SOURCE + "::test_rail_init_captures_system_prompt_builder",
                SOURCE + "::test_rail_init_with_missing_attributes",
                SOURCE + "::test_rail_priority",
                SOURCE + "::test_before_model_call_returns_early_when_builder_is_none",
                SOURCE + "::test_before_model_call_with_empty_workspace",
                SOURCE + "::test_before_model_call_with_only_readme",
                SOURCE + "::test_before_model_call_adds_tools_section",
                SOURCE + "::test_before_model_call_removes_tools_when_ability_manager_empty",
                SOURCE + "::test_uninit_handles_none_builder",
                SOURCE + "::test_before_model_call_with_chinese_language",
                SOURCE + "::test_before_model_call_with_english_language"
        );
    }

    private static void runPythonCase(String nodeId) throws Exception {
        switch (nodeId) {
            case SOURCE + "::test_build_workspace_section" -> testBuildWorkspaceSection();
            case SOURCE + "::test_build_workspace_section_returns_none_when_workspace_is_none" ->
                    testBuildWorkspaceSectionReturnsNoneWhenWorkspaceIsNone();
            case SOURCE + "::test_build_context_section" -> testBuildContextSection();
            case SOURCE + "::test_build_context_section_returns_none_when_workspace_is_none" ->
                    testBuildContextSectionReturnsNoneWhenWorkspaceIsNone();
            case SOURCE + "::test_build_context_section_skips_empty_daily_memory_dir" ->
                    testBuildContextSectionSkipsEmptyDailyMemoryDir();
            case SOURCE + "::test_build_context_section_skips_when_today_daily_memory_missing" ->
                    testBuildContextSectionSkipsWhenTodayDailyMemoryMissing();
            case SOURCE + "::test_build_context_section_can_exclude_daily_memory" ->
                    testBuildContextSectionCanExcludeDailyMemory();
            case SOURCE + "::test_build_tools_content" -> testBuildToolsContent();
            case SOURCE + "::test_build_context_section_with_tools_content" ->
                    testBuildContextSectionWithToolsContent();
            case SOURCE + "::test_build_context_section_without_tools" -> testBuildContextSectionWithoutTools();
            case SOURCE + "::test_before_model_call_injects_sections" -> testBeforeModelCallInjectsSections();
            case SOURCE + "::test_before_model_call_heartbeat_uses_lightweight_context" ->
                    testBeforeModelCallHeartbeatUsesLightweightContext();
            case SOURCE + "::test_before_model_call_removes_sections_when_workspace_is_none" ->
                    testBeforeModelCallRemovesSectionsWhenWorkspaceIsNone();
            case SOURCE + "::test_uninit_removes_sections" -> testUninitRemovesSections();
            case SOURCE + "::test_rail_init_captures_system_prompt_builder" ->
                    testRailInitCapturesSystemPromptBuilder();
            case SOURCE + "::test_rail_init_with_missing_attributes" -> testRailInitWithMissingAttributes();
            case SOURCE + "::test_rail_priority" -> testRailPriority();
            case SOURCE + "::test_before_model_call_returns_early_when_builder_is_none" ->
                    testBeforeModelCallReturnsEarlyWhenBuilderIsNone();
            case SOURCE + "::test_before_model_call_with_empty_workspace" ->
                    testBeforeModelCallWithEmptyWorkspace();
            case SOURCE + "::test_before_model_call_with_only_readme" -> testBeforeModelCallWithOnlyReadme();
            case SOURCE + "::test_before_model_call_adds_tools_section" -> testBeforeModelCallAddsToolsSection();
            case SOURCE + "::test_before_model_call_removes_tools_when_ability_manager_empty" ->
                    testBeforeModelCallRemovesToolsWhenAbilityManagerEmpty();
            case SOURCE + "::test_uninit_handles_none_builder" -> testUninitHandlesNoneBuilder();
            case SOURCE + "::test_before_model_call_with_chinese_language" ->
                    testBeforeModelCallWithChineseLanguage();
            case SOURCE + "::test_before_model_call_with_english_language" ->
                    testBeforeModelCallWithEnglishLanguage();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void testBuildWorkspaceSection() throws IOException {
        Path root = workspaceRoot("workspace-section");
        Workspace workspace = new Workspace(root.toString(), "cn");

        PromptSection sectionCn = WorkspaceSection.buildWorkspaceSection(null, workspace, "cn");
        assertThat(sectionCn.render("cn")).contains("# 工作空间");
        assertThat(sectionCn.render("cn")).contains("你的工作目录是：`" + root + "`");
        assertThat(sectionCn.render("en")).contains("# 工作空间");

        PromptSection sectionEn = WorkspaceSection.buildWorkspaceSection(null, workspace, "en");
        assertThat(sectionEn.render("en")).contains("# Workspace");
        assertThat(sectionEn.render("en")).contains("Your working directory is: `" + root + "`");
        assertThat(sectionEn.render("cn")).contains("# Workspace");
    }

    private static void testBuildWorkspaceSectionReturnsNoneWhenWorkspaceIsNone() {
        assertThat(WorkspaceSection.buildWorkspaceSection(null, null, "cn")).isNull();
    }

    private static void testBuildContextSection() throws Exception {
        withWorkspace("context-section", root -> {
            write(root, "AGENT.md", "# Agent Config\nreal body");
            write(root, "SOUL.md", "# Soul Content\nreal body");
            write(root, "memory/daily_memory/" + today() + ".md", "# Today");

            SysOperation sysOperation = localSysOperation(root);
            Workspace workspace = new Workspace(root.toString(), "cn");
            PromptSection sectionCn = ContextSection.buildContextSection(
                    sysOperation,
                    workspace,
                    "cn",
                    null,
                    TIMEZONE,
                    true
            );
            assertThat(sectionCn.getPriority()).isEqualTo(80);
            String cnContent = sectionCn.render("cn");
            assertThat(cnContent).contains("## AGENT.md - 智能体配置");
            assertThat(cnContent).contains("以下文件已加载到上下文中，无需再次读取。");
            assertThat(cnContent).contains("# Agent Config");
            assertThat(cnContent).contains("## SOUL.md");
            assertThat(cnContent).contains("## daily_memory/" + today());

            PromptSection sectionEn = ContextSection.buildContextSection(
                    sysOperation,
                    workspace,
                    "en",
                    null,
                    TIMEZONE,
                    true
            );
            assertThat(sectionEn.render("en")).contains("## AGENT.md - Agent Configuration");
            assertThat(sectionEn.render("en")).contains("already loaded into context");
        });
    }

    private static void testBuildContextSectionReturnsNoneWhenWorkspaceIsNone() {
        assertThat(ContextSection.buildContextSection(null, null, "cn", null, null, true)).isNull();
    }

    private static void testBuildContextSectionSkipsEmptyDailyMemoryDir() throws Exception {
        withWorkspace("empty-daily-memory", root -> {
            write(root, "AGENT.md", "# Agent Config\nreal body");
            Files.createDirectories(root.resolve("memory").resolve("daily_memory"));

            String content = ContextSection.buildContextSection(
                    localSysOperation(root),
                    new Workspace(root.toString(), "cn"),
                    "cn",
                    null,
                    TIMEZONE,
                    true
            ).render("cn");

            assertThat(content).contains("# Agent Config");
            assertThat(content).doesNotContain("## daily_memory/");
        });
    }

    private static void testBuildContextSectionSkipsWhenTodayDailyMemoryMissing() throws Exception {
        withWorkspace("missing-today-memory", root -> {
            write(root, "AGENT.md", "# Agent Config\nreal body");
            write(root, "memory/daily_memory/2026-04-02.md", "# Yesterday");

            String content = ContextSection.buildContextSection(
                    localSysOperation(root),
                    new Workspace(root.toString(), "cn"),
                    "cn",
                    null,
                    TIMEZONE,
                    true
            ).render("cn");

            assertThat(content).contains("# Agent Config");
            assertThat(content).doesNotContain("# Yesterday");
            assertThat(content).doesNotContain("## daily_memory/");
        });
    }

    private static void testBuildContextSectionCanExcludeDailyMemory() throws Exception {
        withWorkspace("exclude-daily-memory", root -> {
            write(root, "AGENT.md", "# Agent Config\nreal body");
            write(root, "memory/daily_memory/" + today() + ".md", "# Today");

            String content = ContextSection.buildContextSection(
                    localSysOperation(root),
                    new Workspace(root.toString(), "cn"),
                    "cn",
                    null,
                    TIMEZONE,
                    false
            ).render("cn");

            assertThat(content).contains("# Agent Config");
            assertThat(content).doesNotContain("# Today");
            assertThat(content).doesNotContain("## daily_memory/");
        });
    }

    private static void testBuildToolsContent() {
        AbilityManager manager = new AbilityManager();
        manager.add(List.of(
                ToolCard.builder().name("free_search").description("verbose desc").build(),
                ToolCard.builder().name("paid_search").description("paid verbose desc").build(),
                ToolCard.builder().name("read_file").description("read").build(),
                ToolCard.builder().name("write_file").description("write").build(),
                ToolCard.builder().name("edit_file").description("edit").build(),
                ToolCard.builder().name("bash").description("执行 Shell 命令并返回输出。").build(),
                ToolCard.builder().name("code").description("执行代码（Python 或 JavaScript）。").build(),
                ToolCard.builder().name("list_skill").description("list").build(),
                ToolCard.builder().name("task_tool").description("""
                        启动临时子代理。

                        可用代理类型及对应工具：
                        "browser_agent": 专用浏览器子代理，使用 Playwright 执行网页任务

                        重要：使用时必须指定参数。
                        """).build(),
                ToolCard.builder().name("cron_list_jobs").description("legacy").build(),
                ToolCard.builder().name("").description("skip - no name").build(),
                ToolCard.builder().name("t2").description("").build()
        ));

        assertThat(ContextSection.buildToolsContent(null, "cn")).isNull();
        assertThat(ContextSection.buildToolsContent(new AbilityManager(), "cn")).isNull();

        String cn = ContextSection.buildToolsContent(manager, "cn");
        assertThat(cn).contains("# 可用工具\n");
        assertThat(cn.indexOf("- paid_search:")).isLessThan(cn.indexOf("- free_search:"));
        assertThat(cn).contains("- free_search: 免费搜索（DuckDuckGo 等）");
        assertThat(cn).contains("- read_file / write_file / edit_file: 文件读写编辑");
        assertThat(cn).contains("- bash: 执行 Shell 命令");
        assertThat(cn).contains("- code: 执行 Python 或 JavaScript 代码");
        assertThat(cn).contains("- list_skill: 列出可用技能");
        assertThat(cn).contains("## bash 使用原则");
        assertThat(cn).contains("不要用 bash 替代 `glob` / `grep` / `read_file` / `edit_file` / `write_file`");
        assertThat(cn).contains("## task_tool 使用原则");
        assertThat(cn).contains("可用代理类型：");
        assertThat(cn).contains("- \"browser_agent\": 专用浏览器子代理，使用 Playwright 执行网页任务");
        assertThat(cn).doesNotContain("cron_list_jobs").doesNotContain("t2").doesNotContain("skip");
        assertThat(cn).endsWith("\n");

        String en = ContextSection.buildToolsContent(manager, "en");
        assertThat(en).contains("# Available Tools\n");
        assertThat(en).contains("- paid_search: Paid web search (preferred when configured)");
        assertThat(en).contains("- free_search: Free web search");
        assertThat(en.indexOf("- paid_search:")).isLessThan(en.indexOf("- free_search:"));
        assertThat(en).contains("- read_file / write_file / edit_file: Read, write, and edit files");
        assertThat(en).contains("- bash: Run shell commands");
        assertThat(en).contains("- code: Run Python or JavaScript code");
        assertThat(en).contains("## bash Guidelines");
        assertThat(en).contains("## task_tool Guidelines");
    }

    private static void testBuildContextSectionWithToolsContent() throws Exception {
        withWorkspace("context-with-tools", root -> {
            AbilityManager manager = new AbilityManager();
            manager.add(ToolCard.builder().name("MyTool").description("My desc.").build());

            PromptSection sectionCn = ContextSection.buildContextSection(
                    localSysOperation(root),
                    new Workspace(root.toString(), "cn"),
                    "cn",
                    ContextSection.buildToolsContent(manager, "cn"),
                    TIMEZONE,
                    true
            );
            assertThat(sectionCn.render("cn")).contains("# 可用工具");
            assertThat(sectionCn.render("cn")).contains("MyTool");

            PromptSection sectionEn = ContextSection.buildContextSection(
                    localSysOperation(root),
                    new Workspace(root.toString(), "en"),
                    "en",
                    ContextSection.buildToolsContent(manager, "en"),
                    TIMEZONE,
                    true
            );
            assertThat(sectionEn.render("en")).contains("# Available Tools");
            assertThat(sectionEn.render("en")).contains("MyTool");
        });
    }

    private static void testBuildContextSectionWithoutTools() throws Exception {
        withWorkspace("context-without-tools", root -> {
            write(root, "AGENT.md", "# AGENT\nreal body");

            String content = ContextSection.buildContextSection(
                    localSysOperation(root),
                    new Workspace(root.toString(), "cn"),
                    "cn",
                    null,
                    TIMEZONE,
                    true
            ).render("cn");

            assertThat(content).contains("## AGENT.md");
            assertThat(content).doesNotContain("# 可用工具");
            assertThat(content).doesNotContain("# Available Tools");
        });
    }

    private static void testBeforeModelCallInjectsSections() throws Exception {
        withWorkspace("before-model-call", root -> {
            write(root, "AGENT.md", "# Agent Config\nreal body");
            AgentHarness harness = harness(root, "cn");
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of("run_kind", "normal")));

            assertThat(harness.reactAgent.getSystemPromptBuilder().getSection(SectionName.WORKSPACE))
                    .map(section -> section.render("cn"))
                    .hasValueSatisfying(content -> assertThat(content).contains("# 工作空间"));
            assertThat(harness.reactAgent.getSystemPromptBuilder().getSection(SectionName.CONTEXT))
                    .map(section -> section.render("cn"))
                    .hasValueSatisfying(content -> assertThat(content).contains("## AGENT.md"));
            assertThat(harness.reactAgent.getSystemPromptBuilder().hasSection(SectionName.TOOLS)).isFalse();
        });
    }

    private static void testBeforeModelCallHeartbeatUsesLightweightContext() throws Exception {
        withWorkspace("heartbeat-context", root -> {
            write(root, "AGENT.md", "# Agent Config\nreal body");
            write(root, "SOUL.md", "# Soul Content\nreal body");
            write(root, "HEARTBEAT.md", "# Heartbeat Tasks\nreal body");
            write(root, "memory/daily_memory/" + today() + ".md", "# Today");
            AgentHarness harness = harness(root, "cn");
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of("run_kind", "heartbeat")));

            String content = harness.reactAgent.getSystemPromptBuilder()
                    .getSection(SectionName.CONTEXT)
                    .orElseThrow()
                    .render("cn");
            assertThat(content).contains("## AGENT.md");
            assertThat(content).contains("# Agent Config");
            assertThat(content).contains("## SOUL.md");
            assertThat(content).contains("# Soul Content");
            assertThat(content).contains("## HEARTBEAT.md");
            assertThat(content).contains("# Heartbeat Tasks");
            assertThat(content).doesNotContain("# Today");
            assertThat(content).doesNotContain("## daily_memory/");
        });
    }

    private static void testBeforeModelCallRemovesSectionsWhenWorkspaceIsNone() {
        AgentHarness harness = harness(null, "cn");
        harness.reactAgent.getSystemPromptBuilder().addSection(
                new PromptSection(SectionName.WORKSPACE, Map.of("cn", "workspace"), 70));
        harness.reactAgent.getSystemPromptBuilder().addSection(
                new PromptSection(SectionName.CONTEXT, Map.of("cn", "context"), 80));
        ContextAssembleRail rail = initializedRail(harness.agent);

        rail.beforeModelCall(new CallbackContext(harness.agent, Map.of()));

        assertThat(harness.reactAgent.getSystemPromptBuilder().hasSection(SectionName.WORKSPACE)).isFalse();
        assertThat(harness.reactAgent.getSystemPromptBuilder().hasSection(SectionName.CONTEXT)).isFalse();
    }

    private static void testUninitRemovesSections() throws Exception {
        withWorkspace("uninit-sections", root -> {
            AgentHarness harness = harness(root, "cn");
            harness.reactAgent.getSystemPromptBuilder().addSection(
                    new PromptSection(SectionName.WORKSPACE, Map.of("cn", "workspace"), 70));
            harness.reactAgent.getSystemPromptBuilder().addSection(
                    new PromptSection(SectionName.CONTEXT, Map.of("cn", "context"), 80));
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.uninit(harness.agent);

            assertThat(harness.reactAgent.getSystemPromptBuilder().hasSection(SectionName.WORKSPACE)).isFalse();
            assertThat(harness.reactAgent.getSystemPromptBuilder().hasSection(SectionName.CONTEXT)).isFalse();
            assertThat(rail.getSystemPromptBuilder()).isNull();
            assertThat(rail.getAbilityManager()).isNull();
        });
    }

    private static void testRailInitCapturesSystemPromptBuilder() throws Exception {
        withWorkspace("rail-init", root -> {
            AgentHarness harness = harness(root, "en");
            ContextAssembleRail rail = initializedRail(harness.agent);

            assertThat(rail.getPriority()).isEqualTo(85);
            assertThat(rail.getSystemPromptBuilder()).isSameAs(harness.reactAgent.getSystemPromptBuilder());
            assertThat(rail.getAbilityManager()).isSameAs(harness.agent.getAbilityManager());
        });
    }

    private static void testRailInitWithMissingAttributes() {
        DeepAgent agent = new DeepAgent(new AgentCard("test", "test", "test"));
        agent.configure(new DeepAgentConfig());
        ContextAssembleRail rail = new ContextAssembleRail();

        rail.init(agent);

        assertThat(rail.getSystemPromptBuilder()).isNull();
        assertThat(rail.getAbilityManager()).isSameAs(agent.getAbilityManager());
    }

    private static void testRailPriority() {
        assertThat(new ContextAssembleRail().getPriority()).isEqualTo(85);
    }

    private static void testBeforeModelCallReturnsEarlyWhenBuilderIsNone() throws Exception {
        withWorkspace("missing-builder", root -> {
            ContextAssembleRail rail = new ContextAssembleRail();
            rail.beforeModelCall(new CallbackContext(harness(root, "cn").agent, Map.of()));
            assertThat(rail.getSystemPromptBuilder()).isNull();
        });
    }

    private static void testBeforeModelCallWithEmptyWorkspace() throws Exception {
        withWorkspace("empty-workspace", root -> {
            AgentHarness harness = harness(root, "cn");
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of()));

            assertThat(harness.reactAgent.getSystemPromptBuilder().getSection(SectionName.WORKSPACE)).isPresent();
        });
    }

    private static void testBeforeModelCallWithOnlyReadme() throws Exception {
        withWorkspace("only-readme", root -> {
            write(root, "README.md", "# Test Project");
            AgentHarness harness = harness(root, "cn");
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of()));

            assertThat(harness.reactAgent.getSystemPromptBuilder().getSection(SectionName.WORKSPACE)).isPresent();
        });
    }

    private static void testBeforeModelCallAddsToolsSection() throws Exception {
        withWorkspace("adds-tools", root -> {
            AgentHarness harness = harness(root, "cn");
            harness.agent.getAbilityManager().add(
                    ToolCard.builder().name("test_tool").description("A test tool").build());
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of()));

            assertThat(harness.reactAgent.getSystemPromptBuilder().getSection(SectionName.TOOLS)).isPresent();
        });
    }

    private static void testBeforeModelCallRemovesToolsWhenAbilityManagerEmpty() throws Exception {
        withWorkspace("removes-tools", root -> {
            AgentHarness harness = harness(root, "cn");
            harness.reactAgent.getSystemPromptBuilder().addSection(
                    new PromptSection(SectionName.TOOLS, Map.of("cn", "tools"), 30));
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of()));

            assertThat(harness.reactAgent.getSystemPromptBuilder().hasSection(SectionName.TOOLS)).isFalse();
        });
    }

    private static void testUninitHandlesNoneBuilder() throws Exception {
        withWorkspace("uninit-none", root -> {
            ContextAssembleRail rail = new ContextAssembleRail();
            rail.uninit(harness(root, "cn").agent);
            assertThat(rail.getSystemPromptBuilder()).isNull();
            assertThat(rail.getAbilityManager()).isNull();
        });
    }

    private static void testBeforeModelCallWithChineseLanguage() throws Exception {
        withWorkspace("chinese-language", root -> {
            write(root, "README.md", "# Test");
            AgentHarness harness = harness(root, "cn");
            harness.reactAgent.getSystemPromptBuilder().setLanguage("cn");
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of()));

            assertThat(harness.reactAgent.getSystemPromptBuilder().getSection(SectionName.WORKSPACE))
                    .map(section -> section.render("cn"))
                    .hasValueSatisfying(content -> assertThat(content).contains("# 工作空间"));
        });
    }

    private static void testBeforeModelCallWithEnglishLanguage() throws Exception {
        withWorkspace("english-language", root -> {
            write(root, "README.md", "# Test");
            AgentHarness harness = harness(root, "en");
            harness.reactAgent.getSystemPromptBuilder().setLanguage("en");
            ContextAssembleRail rail = initializedRail(harness.agent);

            rail.beforeModelCall(new CallbackContext(harness.agent, Map.of()));

            assertThat(harness.reactAgent.getSystemPromptBuilder().getSection(SectionName.WORKSPACE))
                    .map(section -> section.render("en"))
                    .hasValueSatisfying(content -> assertThat(content).contains("# Workspace"));
        });
    }

    private static ContextAssembleRail initializedRail(DeepAgent agent) {
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.init(agent);
        return rail;
    }

    private static AgentHarness harness(Path root, String language) {
        Workspace workspace = root == null ? null : new Workspace(root.toString(), language);
        SysOperation sysOperation = root == null ? null : localSysOperation(root);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setWorkspace(workspace);
        config.setSysOperation(sysOperation);

        DeepAgent deepAgent = new DeepAgent(new AgentCard("test", "test", "test"));
        deepAgent.configure(config);
        ReActAgent reactAgent = new ReActAgent(new AgentCard("agent", "agent", "test"));
        reactAgent.getSystemPromptBuilder().setLanguage(language);
        deepAgent.setReactAgent(reactAgent, true);
        return new AgentHarness(deepAgent, reactAgent);
    }

    private static SysOperation localSysOperation(Path root) {
        Cwd.initCwd(root.toString(), root.toString(), root.toString(), null);
        return new SysOperation(new SysOperationCard(
                "test_context_rail_sysop_" + root.getFileName(),
                OperationMode.LOCAL,
                LocalWorkConfig.builder().build()
        ));
    }

    private static Path workspaceRoot(String prefix) throws IOException {
        Path root = Files.createTempDirectory(prefix);
        Cwd.initCwd(root.toString(), root.toString(), root.toString(), null);
        return root;
    }

    private static void withWorkspace(String prefix, WorkspaceAction action) throws Exception {
        Path root = workspaceRoot(prefix);
        try {
            action.run(root);
        } finally {
            Cwd.clear();
        }
    }

    private static void write(Path root, String relativePath, String content) throws IOException {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static String today() {
        return LocalDate.now(ZoneId.of(TIMEZONE)).format(DateTimeFormatter.ISO_DATE);
    }

    @FunctionalInterface
    private interface WorkspaceAction {
        void run(Path root) throws Exception;
    }

    private record AgentHarness(DeepAgent agent, ReActAgent reactAgent) {
    }
}
