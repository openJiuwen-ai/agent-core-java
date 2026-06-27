/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.skills.ListSkillTool;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for skill rail behavior.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/test_skill_rail.py}.</p>
 */
class SkillUseRailPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/test_skill_rail.py";

    @TestFactory
    Collection<DynamicTest> pythonSkillRailCases() {
        return List.of(
                caseOf("test_skill_rail_all_mode_loads_skills_on_before_invoke",
                        SkillUseRailPythonParityTest::allModeLoadsSkillsOnBeforeInvoke),
                caseOf("test_skill_rail_all_mode_injects_skill_prompt",
                        SkillUseRailPythonParityTest::allModeInjectsSkillPrompt),
                caseOf("test_skill_rail_filters_enabled_and_disabled_skills",
                        SkillUseRailPythonParityTest::filtersEnabledAndDisabledSkills),
                caseOf("test_skill_rail_register_rail_auto_list_registers_list_skill_tool",
                        SkillUseRailPythonParityTest::registerRailAutoListRegistersListSkillTool),
                caseOf("test_auto_list_prompt_is_injected_without_preselecting_skills",
                        SkillUseRailPythonParityTest::autoListPromptIsInjectedWithoutPreselectingSkills),
                caseOf("test_list_skill_tool_reads_latest_skills_from_skill_rail",
                        SkillUseRailPythonParityTest::listSkillToolReadsLatestSkillsFromSkillRail),
                caseOf("test_list_skill_tool_returns_all_skills_when_query_empty",
                        SkillUseRailPythonParityTest::listSkillToolReturnsAllSkillsWhenQueryEmpty),
                caseOf("test_skill_rail_reuses_cached_skills_across_invokes",
                        SkillUseRailPythonParityTest::skillRailReusesCachedSkillsAcrossInvokes),
                caseOf("test_skill_rail_only_loads_new_skill_on_incremental_refresh",
                        SkillUseRailPythonParityTest::skillRailOnlyLoadsNewSkillOnIncrementalRefresh),
                caseOf("test_skill_rail_reload_updated_skill_by_update_at",
                        SkillUseRailPythonParityTest::skillRailReloadUpdatedSkillByUpdateAt),
                caseOf("test_skill_rail_skips_nonexistent_directories",
                        SkillUseRailPythonParityTest::skillRailSkipsNonexistentDirectories),
                caseOf("test_skill_rail_all_dirs_nonexistent_produces_empty_skills",
                        SkillUseRailPythonParityTest::skillRailAllDirsNonexistentProducesEmptySkills),
                caseOf("test_skill_rail_priority_dedup_first_dir_wins",
                        SkillUseRailPythonParityTest::skillRailPriorityDedupFirstDirWins),
                caseOf("test_skill_rail_multi_dir_with_missing_dirs",
                        SkillUseRailPythonParityTest::skillRailMultiDirWithMissingDirs)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void allModeLoadsSkillsOnBeforeInvoke() throws IOException {
        Path skillsRoot = tempDir("skill-rail-all-mode");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail rail = allModeRail(skillsRoot);

        rail.beforeInvoke(ctx());

        assertThat(skillNames(rail.getSkillsMeta())).containsExactly("invoice-parser", "xlsx-writer");
    }

    private static void allModeInjectsSkillPrompt() throws IOException {
        Path skillsRoot = tempDir("skill-rail-prompt");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail rail = allModeRail(skillsRoot);

        rail.beforeInvoke(ctx());
        CallbackContext ctx = ctx("language", "en");
        rail.beforeModelCall(ctx);

        String content = section(ctx).render("en");
        assertThat(content)
                .contains("invoice-parser")
                .contains("xlsx-writer")
                .contains("Parse invoice pdf files")
                .contains("Write xlsx reports")
                .doesNotContain("list_skill");
    }

    private static void filtersEnabledAndDisabledSkills() throws IOException {
        Path skillsRoot = tempDir("skill-rail-filter");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        writeSkill(skillsRoot, "legacy-skill", "Old skill");
        SkillUseRail rail = new SkillUseRail(
                skillsRoot.toString(),
                SkillUseRail.SKILL_MODE_ALL,
                true,
                true,
                List.of("invoice-parser,xlsx-writer,legacy-skill"),
                List.of("legacy-skill")
        );

        rail.beforeInvoke(ctx());

        assertThat(skillNames(rail.getSkillsMeta())).containsExactly("invoice-parser", "xlsx-writer");
    }

    private static void registerRailAutoListRegistersListSkillTool() throws IOException {
        Path skillsRoot = tempDir("skill-rail-register");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        DeepAgent agent = new DeepAgent();
        SkillUseRail rail = autoListRail(skillsRoot);

        agent.registerRail(rail).join();

        Set<String> toolNames = agent.getAbilityManager().getTools().keySet();
        assertThat(toolNames).contains("SkillTool", "ListSkillTool");
        assertThat(rail.getOwnedToolNames()).contains("SkillTool", "ListSkillTool");
        assertThat(rail.getOwnedToolIds()).contains("skill_tool", "list_skill");
    }

    private static void autoListPromptIsInjectedWithoutPreselectingSkills() throws IOException {
        Path skillsRoot = tempDir("skill-rail-auto-prompt");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        SkillUseRail rail = autoListRail(skillsRoot);

        rail.beforeInvoke(ctx());
        CallbackContext ctx = ctx("language", "en");
        rail.beforeModelCall(ctx);

        String content = section(ctx).render("en");
        assertThat(content)
                .contains("list_skill")
                .contains("read_file")
                .contains("code")
                .contains("bash")
                .doesNotContain("invoice-parser");
    }

    @SuppressWarnings("unchecked")
    private static void listSkillToolReadsLatestSkillsFromSkillRail() throws Exception {
        Path skillsRoot = tempDir("skill-rail-list-tool");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail rail = autoListRail(skillsRoot);
        rail.beforeInvoke(ctx());
        ListSkillTool tool = new ListSkillTool(rail::getSkillsMeta, (query, skills) -> List.of("xlsx-writer"));

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("query", "generate xlsx report"), Map.of());

        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(result.isSuccess()).isTrue();
        assertThat(data.get("mode")).isEqualTo("filtered");
        assertThat(data.get("selected_skill_names")).isEqualTo(List.of("xlsx-writer"));
        List<Map<String, Object>> skills = (List<Map<String, Object>>) data.get("skills");
        assertThat(skills).hasSize(1);
        assertThat(skills.getFirst().get("name")).isEqualTo("xlsx-writer");
    }

    @SuppressWarnings("unchecked")
    private static void listSkillToolReturnsAllSkillsWhenQueryEmpty() throws Exception {
        Path skillsRoot = tempDir("skill-rail-list-all");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail rail = autoListRail(skillsRoot);
        rail.beforeInvoke(ctx());
        ListSkillTool tool = new ListSkillTool(rail::getSkillsMeta);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(), Map.of());

        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> skills = (List<Map<String, Object>>) data.get("skills");
        assertThat(result.isSuccess()).isTrue();
        assertThat(data.get("mode")).isEqualTo("all");
        assertThat(skills.stream().map(item -> String.valueOf(item.get("name"))).sorted().toList())
                .containsExactly("invoice-parser", "xlsx-writer");
    }

    private static void skillRailReusesCachedSkillsAcrossInvokes() throws IOException {
        Path skillsRoot = tempDir("skill-rail-cache");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail rail = allModeRail(skillsRoot);

        rail.beforeInvoke(ctx());
        List<String> first = skillNames(rail.getSkillsMeta());
        rail.beforeInvoke(ctx());

        assertThat(skillNames(rail.getSkillsMeta())).containsExactlyElementsOf(first);
    }

    private static void skillRailOnlyLoadsNewSkillOnIncrementalRefresh() throws IOException {
        Path skillsRoot = tempDir("skill-rail-refresh");
        writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        SkillUseRail rail = allModeRail(skillsRoot);

        rail.beforeInvoke(ctx());
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        rail.beforeInvoke(ctx());

        assertThat(skillNames(rail.getSkillsMeta())).containsExactly("invoice-parser", "xlsx-writer");
    }

    private static void skillRailReloadUpdatedSkillByUpdateAt() throws Exception {
        Path skillsRoot = tempDir("skill-rail-update");
        Path skillMd = writeSkill(skillsRoot, "invoice-parser", "Parse invoice pdf files");
        writeSkill(skillsRoot, "xlsx-writer", "Write xlsx reports");
        SkillUseRail rail = allModeRail(skillsRoot);

        rail.beforeInvoke(ctx());
        Thread.sleep(1100L);
        Files.writeString(skillMd, skillText("Parse invoices after update"), StandardCharsets.UTF_8);
        rail.beforeInvoke(ctx());

        SkillDescriptor invoiceParser = rail.getSkillsMeta().stream()
                .filter(skill -> "invoice-parser".equals(skill.name()))
                .findFirst()
                .orElseThrow();
        assertThat(invoiceParser.description()).isEqualTo("Parse invoices after update");
    }

    private static void skillRailSkipsNonexistentDirectories() throws IOException {
        Path root = tempDir("skill-rail-missing");
        Path skillsRoot = Files.createDirectories(root.resolve("skills"));
        writeSkill(skillsRoot, "my-skill", "Real skill");

        SkillUseRail rail = allModeRail(root.resolve("does_not_exist")
                + ","
                + skillsRoot
                + ","
                + root.resolve("also_missing"));
        rail.beforeInvoke(ctx());

        assertThat(skillNames(rail.getSkillsMeta())).containsExactly("my-skill");
    }

    private static void skillRailAllDirsNonexistentProducesEmptySkills() throws IOException {
        Path root = tempDir("skill-rail-all-missing");
        SkillUseRail rail = allModeRail(root.resolve("missing_a") + "," + root.resolve("missing_b"));

        rail.beforeInvoke(ctx());

        assertThat(rail.getSkillsMeta()).isEmpty();
    }

    private static void skillRailPriorityDedupFirstDirWins() throws IOException {
        Path root = tempDir("skill-rail-dedup");
        Path highPrio = Files.createDirectories(root.resolve("high"));
        Path lowPrio = Files.createDirectories(root.resolve("low"));
        writeSkill(highPrio, "shared-skill", "High priority version");
        writeSkill(lowPrio, "shared-skill", "Low priority version");
        writeSkill(lowPrio, "unique-skill", "Only in low");
        SkillUseRail rail = allModeRail(highPrio + "," + lowPrio);

        rail.beforeInvoke(ctx());

        assertThat(skillNames(rail.getSkillsMeta())).containsExactly("shared-skill", "unique-skill");
        SkillDescriptor shared = rail.getSkillsMeta().stream()
                .filter(skill -> "shared-skill".equals(skill.name()))
                .findFirst()
                .orElseThrow();
        assertThat(shared.directory()).isEqualTo(highPrio.resolve("shared-skill").toAbsolutePath().normalize().toString());
        assertThat(shared.description()).isEqualTo("High priority version");
    }

    private static void skillRailMultiDirWithMissingDirs() throws IOException {
        Path root = tempDir("skill-rail-multi");
        Path existingA = Files.createDirectories(root.resolve("dir_a"));
        Path missingB = root.resolve("dir_b");
        Path existingC = Files.createDirectories(root.resolve("dir_c"));
        writeSkill(existingA, "skill-a", "From dir A");
        writeSkill(existingC, "skill-c", "From dir C");
        SkillUseRail rail = allModeRail(existingA + "," + missingB + "," + existingC);

        rail.beforeInvoke(ctx());

        assertThat(skillNames(rail.getSkillsMeta())).containsExactly("skill-a", "skill-c");
    }

    private static SkillUseRail allModeRail(Path skillsRoot) {
        return allModeRail(skillsRoot.toString());
    }

    private static SkillUseRail allModeRail(String skillsRoot) {
        return new SkillUseRail(skillsRoot, SkillUseRail.SKILL_MODE_ALL, true, false, null, null);
    }

    private static SkillUseRail autoListRail(Path skillsRoot) {
        return new SkillUseRail(skillsRoot.toString(), SkillUseRail.SKILL_MODE_AUTO_LIST, true, true, null, null);
    }

    private static CallbackContext ctx(Object... values) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return new CallbackContext(new DeepAgent(), map);
    }

    private static PromptSection section(CallbackContext ctx) {
        return (PromptSection) ctx.get("skills_section");
    }

    private static Path tempDir(String prefix) throws IOException {
        return Files.createTempDirectory(prefix + "-");
    }

    private static Path writeSkill(Path root, String name, String description) throws IOException {
        Path skillDir = Files.createDirectories(root.resolve(name));
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, skillText(description), StandardCharsets.UTF_8);
        return skillMd;
    }

    private static String skillText(String description) {
        return """
                ---
                description: %s
                ---

                # Skill
                """.formatted(description);
    }

    private static List<String> skillNames(List<SkillDescriptor> skills) {
        List<String> names = new ArrayList<>(skills.stream().map(SkillDescriptor::name).toList());
        names.sort(Comparator.naturalOrder());
        return names;
    }
}
