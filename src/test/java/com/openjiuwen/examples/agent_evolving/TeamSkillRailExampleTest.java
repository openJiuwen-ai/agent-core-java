/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_evolving;

import com.openjiuwen.agent_teams.spawn.SpawnContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamSkillRailExampleTest {

    @Test
    void prepareWorkspaceCreatesSkillsDirectory(@TempDir Path tempDir) throws Exception {
        Path workspace = TeamSkillRailExample.prepareWorkspace(tempDir.resolve("workspace").toString());

        assertTrue(Files.isDirectory(workspace));
        assertTrue(Files.isDirectory(workspace.resolve("skills")));
    }

    @Test
    void buildSessionIdUsesPrefixAndUuidSuffix() {
        String sessionId = TeamSkillRailExample.buildSessionId("team_skill_evolve");

        assertTrue(sessionId.startsWith("team_skill_evolve_"));
        assertEquals("team_skill_evolve_".length() + 32, sessionId.length());
    }

    @Test
    void writeTeamSkillCreatesTeamSkillSkillMd(@TempDir Path tempDir) throws Exception {
        Path skillDir = TeamSkillRailExample.writeTeamSkill(tempDir, "research-team");

        String content = Files.readString(skillDir.resolve("SKILL.md"));
        assertTrue(content.contains("name: research-team"));
        assertTrue(content.contains("kind: team-skill"));
        assertTrue(content.contains("build_team"));
        assertTrue(content.contains("spawn_member"));
    }

    @Test
    void parseArgsUsesExplicitWorkspaceQueryIntentAndApprovalFlag() {
        TeamSkillRailExample.ParsedArgs args = TeamSkillRailExample.parseArgs(
                new String[] {
                        "--workspace", "work",
                        "--query", "hello",
                        "--user-intent", "add reviewer",
                        "--approve-patch"
                });

        assertEquals("work", args.workspace());
        assertEquals("hello", args.query());
        assertEquals("add reviewer", args.userIntent());
        assertTrue(args.approvePatch());
    }

    @Test
    void leaderTeamToolsContextProvidesLeaderToolsAndResetsSession(@TempDir Path tempDir) throws Exception {
        String sessionId = "team_skill_rail_test";

        try (TeamSkillCreateRailExample.LeaderTeamContext context =
                     TeamSkillRailExample.leaderTeamToolsContext(tempDir, sessionId, "team", "leader", "cn")) {
            assertEquals(sessionId, SpawnContext.getSessionId());
            assertNotNull(context.backend());
            assertTrue(context.tools().stream()
                    .anyMatch(tool -> "build_team".equals(tool.getCard().getName())));
            assertTrue(context.tools().stream()
                    .anyMatch(tool -> "spawn_member".equals(tool.getCard().getName())));
            assertEquals(context.tools().size(), context.toolCards().size());
        }

        assertEquals("", SpawnContext.getSessionId());
    }
}
