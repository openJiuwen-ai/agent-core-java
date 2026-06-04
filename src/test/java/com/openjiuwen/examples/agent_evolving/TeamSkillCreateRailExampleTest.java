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

class TeamSkillCreateRailExampleTest {

    @Test
    void prepareWorkspaceCreatesSkillsDirectory(@TempDir Path tempDir) throws Exception {
        Path workspace = TeamSkillCreateRailExample.prepareWorkspace(tempDir.resolve("workspace").toString());

        assertTrue(Files.isDirectory(workspace));
        assertTrue(Files.isDirectory(workspace.resolve("skills")));
    }

    @Test
    void buildSessionIdUsesPrefixAndUuidSuffix() {
        String sessionId = TeamSkillCreateRailExample.buildSessionId("team_skill_create");

        assertTrue(sessionId.startsWith("team_skill_create_"));
        assertEquals("team_skill_create_".length() + 32, sessionId.length());
    }

    @Test
    void parseArgsUsesExplicitWorkspaceAndQuery() {
        TeamSkillCreateRailExample.ParsedArgs args = TeamSkillCreateRailExample.parseArgs(
                new String[] {"--workspace", "work", "--query", "hello"}
        );

        assertEquals("work", args.workspace());
        assertEquals("hello", args.query());
    }

    @Test
    void leaderTeamToolsContextProvidesLeaderToolsAndResetsSession(@TempDir Path tempDir) throws Exception {
        String sessionId = "team_skill_create_test";

        try (TeamSkillCreateRailExample.LeaderTeamContext context =
                     TeamSkillCreateRailExample.leaderTeamToolsContext(tempDir, sessionId, "team", "leader", "cn")) {
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
