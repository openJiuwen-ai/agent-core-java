/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.rails.context_engineer.ContextAssembleRail;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContextAssembleRail.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.test_context_assemble_rail}.
 */
class TestContextAssembleRail {

    private DeepAgent createAgent(Workspace workspace, String language) {
        AgentCard card = AgentCard.builder()
                .id("test-agent")
                .name("test")
                .description("test")
                .build();
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        config.setWorkspace(workspace);
        agent.configure(config);
        agent.getSystemPromptBuilder().setLanguage(language);
        return agent;
    }

    private AgentCallbackContext createContext(DeepAgent agent) {
        return AgentCallbackContext.builder().agent(agent).build();
    }

    @Test
    @Tag("level0")
    @DisplayName("buildWorkspaceSection renders CN and EN content")
    void testBuildWorkspaceSection(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "# Test");
        Workspace workspace = new Workspace(tempDir.toString(), "cn");

        PromptSection cn = ContextAssembleRail.buildWorkspaceSection(workspace, "cn");
        assertNotNull(cn);
        assertTrue(cn.render("cn").contains("# 工作空间"));
        assertTrue(cn.render("cn").contains(tempDir.toAbsolutePath().normalize().toString()));

        PromptSection en = ContextAssembleRail.buildWorkspaceSection(workspace, "en");
        assertTrue(en.render("en").contains("# Workspace"));
        assertTrue(en.render("en").contains("Your working directory is"));
    }

    @Test
    @Tag("level0")
    void testBuildWorkspaceSectionReturnsNoneWhenWorkspaceIsNone() {
        assertNull(ContextAssembleRail.buildWorkspaceSection(null, "cn"));
    }

    @Test
    @Tag("level0")
    void testBuildContextSection(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("AGENT.md"), "# Agent Config\nreal body");
        Files.writeString(tempDir.resolve("SOUL.md"), "# Soul Content\nreal body");
        Path dailyMemory = tempDir.resolve("memory").resolve("daily_memory");
        Files.createDirectories(dailyMemory);
        Files.writeString(dailyMemory.resolve(LocalDate.now(ZoneId.of("Asia/Shanghai")) + ".md"), "# Today");

        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        PromptSection cn = ContextAssembleRail.buildContextSection(workspace, "cn", null, ZoneId.of("Asia/Shanghai"));
        assertNotNull(cn);
        assertEquals(80, cn.getPriority());
        assertTrue(cn.render("cn").contains("## AGENT.md - 智能体配置"));
        assertTrue(cn.render("cn").contains("## SOUL.md"));
        assertTrue(cn.render("cn").contains("## daily_memory/"));

        PromptSection en = ContextAssembleRail.buildContextSection(workspace, "en", null, ZoneId.of("Asia/Shanghai"));
        assertTrue(en.render("en").contains("## AGENT.md - Agent Configuration"));
        assertTrue(en.render("en").contains("already loaded into context"));
    }

    @Test
    @Tag("level0")
    void testBuildContextSectionReturnsNoneWhenWorkspaceIsNone() {
        assertNull(ContextAssembleRail.buildContextSection(null, "cn", null, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    @Tag("level0")
    void testBuildContextSectionSkipsEmptyDailyMemoryDir(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("AGENT.md"), "# Agent Config\nreal body");
        Files.createDirectories(tempDir.resolve("memory").resolve("daily_memory"));
        Workspace workspace = new Workspace(tempDir.toString(), "cn");

        PromptSection section = ContextAssembleRail.buildContextSection(workspace, "cn", null, ZoneId.of("Asia/Shanghai"));
        assertNotNull(section);
        assertTrue(section.render("cn").contains("# Agent Config"));
        assertFalse(section.render("cn").contains("## daily_memory/"));
    }

    @Test
    @Tag("level0")
    void testBuildContextSectionSkipsWhenTodayDailyMemoryMissing(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("AGENT.md"), "# Agent Config\nreal body");
        Path dailyMemory = tempDir.resolve("memory").resolve("daily_memory");
        Files.createDirectories(dailyMemory);
        Files.writeString(dailyMemory.resolve("2026-04-02.md"), "# Yesterday");
        Workspace workspace = new Workspace(tempDir.toString(), "cn");

        PromptSection section = ContextAssembleRail.buildContextSection(workspace, "cn", null, ZoneId.of("Asia/Shanghai"));
        assertNotNull(section);
        assertTrue(section.render("cn").contains("# Agent Config"));
        assertFalse(section.render("cn").contains("# Yesterday"));
        assertFalse(section.render("cn").contains("## daily_memory/"));
    }

    @Test
    @Tag("level0")
    void testBuildToolsContent() {
        AbilityManager manager = new AbilityManager();
        manager.add(ToolCard.builder().id("free_search").name("free_search").description("verbose desc").build());
        manager.add(ToolCard.builder().id("paid_search").name("paid_search").description("paid verbose desc").build());
        manager.add(ToolCard.builder().id("read_file").name("read_file").description("read").build());
        manager.add(ToolCard.builder().id("write_file").name("write_file").description("write").build());
        manager.add(ToolCard.builder().id("edit_file").name("edit_file").description("edit").build());
        manager.add(ToolCard.builder().id("bash").name("bash").description("shell").build());
        manager.add(ToolCard.builder().id("code").name("code").description("code").build());
        manager.add(ToolCard.builder().id("list_skill").name("list_skill").description("list").build());
        manager.add(ToolCard.builder().id("cron").name("cron_list_jobs").description("legacy").build());

        assertNull(ContextAssembleRail.buildToolsContent(null, "cn"));
        assertNull(ContextAssembleRail.buildToolsContent(new AbilityManager(), "cn"));

        String cn = ContextAssembleRail.buildToolsContent(manager, "cn");
        assertNotNull(cn);
        assertTrue(cn.contains("# 可用工具"));
        assertTrue(cn.indexOf("- paid_search:") < cn.indexOf("- free_search:"));
        assertTrue(cn.contains("- read_file: Read, write, and edit files") || cn.contains("文件读写编辑"));
        assertTrue(cn.contains("## bash 使用原则"));
        assertFalse(cn.contains("cron_list_jobs"));

        String en = ContextAssembleRail.buildToolsContent(manager, "en");
        assertNotNull(en);
        assertTrue(en.contains("# Available Tools"));
        assertTrue(en.contains("- paid_search: Paid web search (preferred when configured)"));
        assertTrue(en.contains("## bash Guidelines"));
    }

    @Test
    @Tag("level0")
    void testBuildContextSectionWithToolsContent(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        String toolsCn = "# 可用工具\n- MyTool: My desc.\n";
        String toolsEn = "# Available Tools\n- MyTool: My desc.\n";

        PromptSection cn = ContextAssembleRail.buildContextSection(workspace, "cn", toolsCn, ZoneId.of("Asia/Shanghai"));
        assertNotNull(cn);
        assertTrue(cn.render("cn").contains("# 可用工具"));
        assertTrue(cn.render("cn").contains("MyTool"));

        PromptSection en = ContextAssembleRail.buildContextSection(workspace, "en", toolsEn, ZoneId.of("Asia/Shanghai"));
        assertNotNull(en);
        assertTrue(en.render("en").contains("# Available Tools"));
        assertTrue(en.render("en").contains("MyTool"));
    }

    @Test
    @Tag("level0")
    void testBuildContextSectionWithoutTools(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("AGENT.md"), "# AGENT\nreal body");
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        PromptSection section = ContextAssembleRail.buildContextSection(workspace, "cn", null, ZoneId.of("Asia/Shanghai"));
        assertNotNull(section);
        assertTrue(section.render("cn").contains("## AGENT.md"));
        assertFalse(section.render("cn").contains("# 可用工具"));
        assertFalse(section.render("cn").contains("# Available Tools"));
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallInjectsSections(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("AGENT.md"), "# AGENT\nreal body");
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);

        rail.beforeModelCall(createContext(agent));

        assertTrue(agent.getSystemPromptBuilder().hasSection("workspace"));
        assertTrue(agent.getSystemPromptBuilder().hasSection("context"));
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallRemovesSectionsWhenWorkspaceIsNone(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.beforeModelCall(createContext(agent));
        assertTrue(agent.getSystemPromptBuilder().hasSection("workspace"));

        rail.setWorkspace(null);
        rail.beforeModelCall(createContext(agent));
        assertFalse(agent.getSystemPromptBuilder().hasSection("workspace"));
        assertFalse(agent.getSystemPromptBuilder().hasSection("context"));
    }

    @Test
    @Tag("level0")
    void testUninitRemovesSections(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("AGENT.md"), "# AGENT\nreal body");
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        agent.getSystemPromptBuilder().addSection(ContextAssembleRail.buildWorkspaceSection(workspace, "cn"));
        agent.getSystemPromptBuilder().addSection(ContextAssembleRail.buildContextSection(
                workspace, "cn", null, ZoneId.of("Asia/Shanghai")));

        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.uninit(agent);

        assertFalse(agent.getSystemPromptBuilder().hasSection("workspace"));
        assertFalse(agent.getSystemPromptBuilder().hasSection("context"));
    }

    @Test
    @Tag("level0")
    void testRailInitCapturesSystemPromptBuilder(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();

        rail.init(agent);

        assertSame(agent.getSystemPromptBuilder(), rail.getSystemPromptBuilder());
        assertSame(agent.getAbilityManager(), rail.getAbilityManager());
    }

    @Test
    @Tag("level0")
    void testRailInitWithMissingAttributes() {
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.init(new Object());
        assertNull(rail.getSystemPromptBuilder());
        assertNull(rail.getAbilityManager());
    }

    @Test
    @Tag("level0")
    void testRailPriority() {
        assertEquals(85, new ContextAssembleRail().getPriority());
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallReturnsEarlyWhenBuilderIsNone(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.uninit(agent);
        rail.beforeModelCall(createContext(agent));
        assertFalse(agent.getSystemPromptBuilder().hasSection("workspace"));
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallWithEmptyWorkspace(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.beforeModelCall(createContext(agent));
        assertTrue(agent.getSystemPromptBuilder().hasSection("workspace"));
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallWithOnlyReadme(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "# Test Project");
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.beforeModelCall(createContext(agent));
        assertTrue(agent.getSystemPromptBuilder().hasSection("workspace"));
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallAddsToolsSection(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        agent.getAbilityManager().add(ToolCard.builder().id("tool-1").name("test_tool").description("A test tool").build());
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.beforeModelCall(createContext(agent));
        assertTrue(agent.getSystemPromptBuilder().hasSection("tools"));
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallRemovesToolsWhenAbilityManagerEmpty(@TempDir Path tempDir) {
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.beforeModelCall(createContext(agent));
        assertFalse(agent.getSystemPromptBuilder().hasSection("tools"));
    }

    @Test
    @Tag("level0")
    void testUninitHandlesNoneBuilder() {
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.uninit(new Object());
        assertNull(rail.getSystemPromptBuilder());
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallWithChineseLanguage(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "# Test");
        Workspace workspace = new Workspace(tempDir.toString(), "cn");
        DeepAgent agent = createAgent(workspace, "cn");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.beforeModelCall(createContext(agent));
        assertTrue(agent.getSystemPromptBuilder().getSection("workspace").orElseThrow().render("cn").contains("# 工作空间"));
    }

    @Test
    @Tag("level0")
    void testBeforeModelCallWithEnglishLanguage(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "# Test");
        Workspace workspace = new Workspace(tempDir.toString(), "en");
        DeepAgent agent = createAgent(workspace, "en");
        ContextAssembleRail rail = new ContextAssembleRail();
        rail.setWorkspace(workspace);
        rail.init(agent);
        rail.beforeModelCall(createContext(agent));
        assertTrue(agent.getSystemPromptBuilder().getSection("workspace").orElseThrow().render("en").contains("# Workspace"));
    }
}
