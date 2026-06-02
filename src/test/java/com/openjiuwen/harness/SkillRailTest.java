/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.tools.ListSkillTool;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for SkillUseRail.
 * <p>
 * Mirrors Python's {@code test_skill_rail} in
 * {@code tests.unit_tests.harness.test_skill_rail}.
 */
@Tag("unit-test")
class SkillRailTest {

    @TempDir
    Path tmpPath;

    private Path writeSkill(Path root, String name, String description) throws Exception {
        Path skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\n"
                        + "description: " + description + "\n"
                        + "---\n\n"
                        + "# " + name + "\n");
        return skillDir;
    }

    private static List<String> sortedSkillNames(List<Skill> skills) {
        List<String> names = new ArrayList<>();
        for (Skill skill : skills) {
            names.add(skill.getName());
        }
        Collections.sort(names);
        return names;
    }

    @Test
    @DisplayName("SkillUseRail should auto-load skills in before_invoke without explicit prepare()")
    void testSkillRailAllModeLoadsSkillsOnBeforeInvoke() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");

        SkillUseRail skillRail = new SkillUseRail(List.of(skillsRoot.toString()),
                SkillUseRail.SKILL_MODE_ALL, true, true, null, null);

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertEquals(List.of("invoice-parser", "xlsx-writer"), sortedSkillNames(skillRail.getLoadedSkills()));
        assertEquals(List.of("invoice-parser", "xlsx-writer"),
                sortedSkillNames(skillRail.getSkillsMeta().stream().map(Skill.class::cast).toList()));
    }

    @Test
    @DisplayName("All mode should add skills section to builder before model call")
    void testSkillRailAllModeInjectsSkillPrompt() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SystemPromptBuilder builder = new SystemPromptBuilder();
        SkillUseRail skillRail = new SkillUseRail(List.of(skillsRoot.toString()),
                SkillUseRail.SKILL_MODE_ALL, true, true, null, null);

        skillRail.init(new PromptAgent(builder));
        skillRail.beforeModelCall(AgentCallbackContext.builder().agent(new PromptAgent(builder)).build());

        String content = builder.build();
        assertTrue(content.contains("invoice-parser"));
        assertTrue(content.contains("xlsx-writer"));
        assertTrue(content.contains("Parse invoice pdf files"));
        assertTrue(content.contains("Write xlsx reports"));
        assertFalse(content.contains("list_skill 查看可用技能"));
    }

    @Test
    @DisplayName("SkillUseRail should respect enabled_skills and disabled_skills")
    void testSkillRailFiltersEnabledAndDisabledSkills() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        writeSkill(skillsRoot, "legacy-skill", "Old skill");
        SkillUseRail skillRail = new SkillUseRail(List.of(skillsRoot.toString()),
                SkillUseRail.SKILL_MODE_ALL, true, true,
                Set.of("invoice-parser", "xlsx-writer", "legacy-skill"), Set.of("legacy-skill"));

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertEquals(List.of("invoice-parser", "xlsx-writer"), sortedSkillNames(skillRail.getLoadedSkills()));
    }

    @Test
    @DisplayName("auto_list mode should register list_skill tool through agent.register_rail()")
    void testSkillRailRegisterRailAutoListRegistersListSkillTool() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail skillRail = new SkillUseRail(List.of(skillsRoot.toString()),
                SkillUseRail.SKILL_MODE_AUTO_LIST, true, true, null, null);

        skillRail.init(new PromptAgent(new SystemPromptBuilder()));

        assertTrue(skillRail.getOwnedToolNames().contains("list_skill"));
        assertTrue(skillRail.getOwnedToolNames().contains("read_file"));
        assertTrue(skillRail.getOwnedToolNames().contains("code"));
        assertTrue(skillRail.getOwnedToolNames().contains("bash"));
    }

    @Test
    @DisplayName("auto_list mode should add guide prompt to builder without pre-expanding skills")
    void testAutoListPromptIsInjectedWithoutPreselectingSkills() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        SystemPromptBuilder builder = new SystemPromptBuilder();
        SkillUseRail skillRail = new SkillUseRail(List.of(skillsRoot.toString()),
                SkillUseRail.SKILL_MODE_AUTO_LIST, true, true, null, null);

        skillRail.init(new PromptAgent(builder));
        skillRail.beforeModelCall(AgentCallbackContext.builder().agent(new PromptAgent(builder)).build());

        String content = builder.build();
        assertTrue(content.contains("list_skill"));
        assertTrue(content.contains("read_file"));
        assertTrue(content.contains("code"));
        assertTrue(content.contains("bash"));
        assertFalse(content.contains("invoice-parser: Parse invoice pdf files"));
    }

    @Test
    @DisplayName("ListSkillTool should read latest skills via get_skills instead of fixed snapshot")
    void testListSkillToolReadsLatestSkillsFromSkillRail() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail skillRail = new SkillUseRail(List.of(skillsRoot.toString()),
                SkillUseRail.SKILL_MODE_AUTO_LIST, true, true, null, null);
        skillRail.beforeInvoke(AgentCallbackContext.builder().build());
        ListSkillTool tool = new ListSkillTool(skillRail::getLoadedSkills,
                new DummyModel("{\"skills\": [\"xlsx-writer\"]}"));

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("query", "generate xlsx report"), Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(result.isSuccess());
        assertEquals("filtered", data.get("mode"));
        assertEquals(List.of("xlsx-writer"), data.get("selected_skill_names"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills = (List<Map<String, Object>>) data.get("skills");
        assertEquals("xlsx-writer", skills.get(0).get("name"));
    }

    @Test
    @DisplayName("ListSkillTool should return all skills when query is empty")
    void testListSkillToolReturnsAllSkillsWhenQueryEmpty() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail skillRail = new SkillUseRail(List.of(skillsRoot.toString()),
                SkillUseRail.SKILL_MODE_AUTO_LIST, true, true, null, null);
        skillRail.beforeInvoke(AgentCallbackContext.builder().build());
        ListSkillTool tool = new ListSkillTool(skillRail::getLoadedSkills);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(), Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills = (List<Map<String, Object>>) data.get("skills");
        assertTrue(result.isSuccess());
        assertEquals("all", data.get("mode"));
        assertEquals(List.of("invoice-parser", "xlsx-writer"),
                skills.stream().map(item -> String.valueOf(item.get("name"))).sorted().toList());
    }

    @Test
    @DisplayName("SkillUseRail should reuse cached skills across invokes when no skill is changed")
    void testSkillRailReusesCachedSkillsAcrossInvokes() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        TrackingSkillUseRail skillRail = new TrackingSkillUseRail(List.of(skillsRoot.toString()));

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());
        assertEquals(List.of("invoice-parser", "xlsx-writer"), skillRail.sortedLoadCalls());
        skillRail.loadCalls.clear();
        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertTrue(skillRail.loadCalls.isEmpty());
    }

    @Test
    @DisplayName("SkillUseRail should load only newly added skills on later invokes")
    void testSkillRailOnlyLoadsNewSkillOnIncrementalRefresh() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        TrackingSkillUseRail skillRail = new TrackingSkillUseRail(List.of(skillsRoot.toString()));

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());
        assertEquals(List.of("invoice-parser"), skillRail.loadCalls);
        skillRail.loadCalls.clear();
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertEquals(List.of("xlsx-writer"), skillRail.loadCalls);
    }

    @Test
    @DisplayName("SkillUseRail should reload only updated skills when SKILL.md update_at changes")
    void testSkillRailReloadUpdatedSkillByUpdateAt() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        Path invoiceSkill = writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        TrackingSkillUseRail skillRail = new TrackingSkillUseRail(List.of(skillsRoot.toString()));

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());
        skillRail.loadCalls.clear();
        Files.setLastModifiedTime(invoiceSkill.resolve("SKILL.md"),
                FileTime.from(Instant.now().plusSeconds(5)));
        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertEquals(List.of("invoice-parser"), skillRail.loadCalls);
    }

    @Test
    @DisplayName("SkillUseRail should silently skip directories that do not exist")
    void testSkillRailSkipsNonexistentDirectories() throws Exception {
        Path skillsRoot = tmpPath.resolve("skills");
        writeSkill(skillsRoot, "my-skill", "Real skill");
        Path nonexistent = tmpPath.resolve("does_not_exist");
        Path anotherNonexistent = tmpPath.resolve("also_missing");
        SkillUseRail skillRail = new SkillUseRail(List.of(nonexistent.toString(), skillsRoot.toString(),
                anotherNonexistent.toString()), SkillUseRail.SKILL_MODE_ALL, true, false, null, null);

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertEquals(List.of("my-skill"), sortedSkillNames(skillRail.getLoadedSkills()));
    }

    @Test
    @DisplayName("SkillUseRail should produce empty skills when all directories are missing")
    void testSkillRailAllDirsNonexistentProducesEmptySkills() {
        Path nonexistentA = tmpPath.resolve("missing_a");
        Path nonexistentB = tmpPath.resolve("missing_b");
        SkillUseRail skillRail = new SkillUseRail(List.of(nonexistentA.toString(), nonexistentB.toString()),
                SkillUseRail.SKILL_MODE_ALL, true, false, null, null);

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertTrue(skillRail.getLoadedSkills().isEmpty());
    }

    @Test
    @DisplayName("When multiple dirs contain a skill with the same name, the first dir wins")
    void testSkillRailPriorityDedupFirstDirWins() throws Exception {
        Path highPrio = tmpPath.resolve("high");
        Path lowPrio = tmpPath.resolve("low");
        writeSkill(highPrio, "shared-skill", "High priority version");
        writeSkill(lowPrio, "shared-skill", "Low priority version");
        writeSkill(lowPrio, "unique-skill", "Only in low");
        SkillUseRail skillRail = new SkillUseRail(List.of(highPrio.toString(), lowPrio.toString()),
                SkillUseRail.SKILL_MODE_ALL, true, false, null, null);

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertEquals(List.of("shared-skill", "unique-skill"), sortedSkillNames(skillRail.getLoadedSkills()));
        Skill shared = skillRail.getLoadedSkills().stream()
                .filter(skill -> "shared-skill".equals(skill.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("High priority version", shared.getDescription());
        assertTrue(shared.getDirectory().contains(highPrio.toString()));
    }

    @Test
    @DisplayName("SkillUseRail loads skills from existing dirs and skips missing ones")
    void testSkillRailMultiDirWithMissingDirs() throws Exception {
        Path existingA = tmpPath.resolve("dir_a");
        Path missingB = tmpPath.resolve("dir_b");
        Path existingC = tmpPath.resolve("dir_c");
        writeSkill(existingA, "skill-a", "From dir A");
        writeSkill(existingC, "skill-c", "From dir C");
        SkillUseRail skillRail = new SkillUseRail(List.of(existingA.toString(), missingB.toString(),
                existingC.toString()), SkillUseRail.SKILL_MODE_ALL, true, false, null, null);

        skillRail.beforeInvoke(AgentCallbackContext.builder().build());

        assertEquals(List.of("skill-a", "skill-c"), sortedSkillNames(skillRail.getLoadedSkills()));
    }

    private static final class PromptAgent {
        private final SystemPromptBuilder builder;

        private PromptAgent(SystemPromptBuilder builder) {
            this.builder = builder;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return builder;
        }
    }

    private static final class DummyModel {
        private final String content;

        private DummyModel(String content) {
            this.content = content;
        }

        public DummyResponse invoke(Map<String, Object> ignored) {
            return new DummyResponse(content);
        }
    }

    private record DummyResponse(String content) {
        public String getContent() {
            return content;
        }
    }

    private static final class TrackingSkillUseRail extends SkillUseRail {
        private final List<String> loadCalls = new ArrayList<>();

        private TrackingSkillUseRail(List<String> skillsDir) {
            super(skillsDir, SkillUseRail.SKILL_MODE_ALL, true, false, null, null);
        }

        @Override
        protected Skill loadSkill(Path skillDir, long updateAt) {
            loadCalls.add(skillDir.getFileName().toString());
            return super.loadSkill(skillDir, updateAt);
        }

        private List<String> sortedLoadCalls() {
            List<String> result = new ArrayList<>(loadCalls);
            Collections.sort(result);
            return result;
        }
    }
}
