/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's factory tests in
 * {@code tests/unit_tests/cli/test_agent_factory.py} and
 * {@code tests/unit_tests/harness/test_cli_default_skills.py}.
 */
class CliAgentFactoryTest {

    @Test
    void defaultSkillDirsReturnsExpectedPaths() {
        List<String> dirs = CliAgentFactory.defaultSkillDirs();

        assertThat(dirs).containsExactly(
                "~/.openjiuwen/workspace/skills",
                "~/.claude/skills",
                "~/.codex/skills",
                "~/.jiuwenclaw/workspace/skills");
    }

    @Test
    void defaultSkillDirsReturnsCopy() {
        List<String> dirs = CliAgentFactory.defaultSkillDirs();
        dirs.add("extra");

        assertThat(CliAgentFactory.DEFAULT_SKILL_DIRS).hasSize(4);
    }

    @Test
    void createAgentOnlyRequiresConfirmForFileMutations() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("api_key", "test-key");
        cfg.put("workspace", "build/openjiuwen-cli-test");

        CliAgentFactory.AgentBundle bundle = CliAgentFactory.createAgent(cfg);

        List<ConfirmInterruptRail> confirmRails = bundle.agent().getRails().stream()
                .filter(ConfirmInterruptRail.class::isInstance)
                .map(ConfirmInterruptRail.class::cast)
                .toList();
        assertThat(confirmRails).hasSize(1);
        assertThat(confirmRails.get(0).getTools()).containsExactlyInAnyOrder("write_file", "edit_file");
    }
}
