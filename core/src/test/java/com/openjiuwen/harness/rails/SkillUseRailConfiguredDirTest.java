/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configured skill directories may live outside the workspace.
 */
class SkillUseRailConfiguredDirTest {

    @TempDir
    Path tempDir;

    @Test
    void initLoadsSkillsOutsideWorkspace() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path skillsRoot = tempDir.resolve("resources").resolve("skills");
        Files.createDirectories(workspace);
        writeSkill(skillsRoot, "financial_report_search",
                "模拟从互联网搜索指定企业最近一个季度的财报信息。");
        writeSkill(skillsRoot, "investment_value_analysis",
                "根据输入的公司名，分析并输出该公司的投资价值。");

        SkillUseRail rail = new SkillUseRail(List.of(skillsRoot.toString()), "all", List.of(), List.of());
        DeepAgent agent = newAgent(workspace, skillsRoot);
        rail.init(agent);
        rail.reloadSkills();

        try {
            assertThat(rail.getSkillsMeta().stream().map(SkillDescriptor::name).toList())
                    .contains("financial_report_search", "investment_value_analysis");
        } finally {
            rail.uninit(agent);
        }
    }

    private static DeepAgent newAgent(Path workspace, Path skillsRoot) {
        AgentCard card = AgentCard.builder()
                .id("skill_dir_agent")
                .name("skill_dir_agent")
                .description("test")
                .build();
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(workspace.toString())
                .language("cn")
                .skillDirectories(List.of(skillsRoot.toString()))
                .skillMode("all")
                .build();
        return new DeepAgent(card, config, new Workspace(workspace.toString(), "cn"));
    }

    private static void writeSkill(Path skillsRoot, String name, String description) throws Exception {
        Path skillDir = skillsRoot.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + description + "\n---\n# " + name + "\n");
    }
}
