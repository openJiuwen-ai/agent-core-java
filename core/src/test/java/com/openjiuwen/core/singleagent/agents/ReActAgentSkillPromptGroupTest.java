/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.operator.toolgroup.GroupMemberRef;
import com.openjiuwen.core.operator.toolgroup.GroupMemberType;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReActAgentSkillPromptGroupTest {
    @TempDir
    private Path tempDir;

    @Test
    void noExplicitGroups_rendersAllSkillsForLegacyBehavior() throws IOException {
        ReActAgent agent = agentWithSkills();
        MemorySession session = new MemorySession("no-group-session");

        agent.updateSkillPromptBuilderSection("System", session);

        assertThat(agent.getPromptBuilder().build())
                .contains("Skill name: CodingSkill")
                .contains("Skill name: GroupedSkill")
                .contains("Skill name: SearchSkill");
    }

    @Test
    void activeGroupSkill_rendersGroupedAndUngroupedSkills() throws IOException {
        ReActAgent agent = agentWithSkills();
        registerPromptGroups(agent);
        MemorySession activeSession = new MemorySession("active-group-session");
        MemorySession inactiveSession = new MemorySession("inactive-group-session");
        agent.activateToolGroup("development", activeSession);

        agent.updateSkillPromptBuilderSection("System", activeSession);

        assertThat(agent.getPromptBuilder().build())
                .contains("Skill name: GroupedSkill")
                .contains("Skill name: SearchSkill")
                .doesNotContain("Skill name: CodingSkill");

        agent.updateSkillPromptBuilderSection("System", inactiveSession);

        assertThat(agent.getPromptBuilder().build())
                .contains("Skill name: SearchSkill")
                .doesNotContain("Skill name: GroupedSkill")
                .doesNotContain("Skill name: CodingSkill");
    }

    @Test
    void directSkillActivation_keepsGroupedSkillVisible() throws IOException {
        ReActAgent agent = agentWithSkills();
        registerPromptGroups(agent);
        MemorySession session = new MemorySession("direct-skill-session");
        agent.activateSkill("GroupedSkill", session);

        agent.updateSkillPromptBuilderSection("System", session);

        assertThat(agent.getPromptBuilder().build())
                .contains("Skill name: GroupedSkill")
                .contains("Skill name: SearchSkill")
                .doesNotContain("Skill name: CodingSkill");
    }

    private ReActAgent agentWithSkills() throws IOException {
        Path codingSkill = createSkill("CodingSkill", "Coding skill");
        Path groupedSkill = createSkill("GroupedSkill", "Grouped skill");
        Path searchSkill = createSkill("SearchSkill", "Search skill");
        String sysOperationId = "react-skill-prompt-" + UUID.randomUUID();
        Runner.resourceMgr().addSysOperation(new SysOperationCard(
                sysOperationId,
                OperationMode.LOCAL,
                LocalWorkConfig.builder().sandboxRoot(List.of(tempDir.toString())).build()));
        try {
            ReActAgent agent = new ReActAgent(new AgentCard(
                    "skill-prompt-agent", "skill-prompt-agent", "test"));
            ReActAgentConfig config = new ReActAgentConfig();
            config.setSysOperationId(sysOperationId);
            config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
            config.setMaxIterations(1);
            agent.configure(config);
            agent.registerSkill(
                    List.of(codingSkill.toString(), groupedSkill.toString(), searchSkill.toString()),
                    true)
                    .toCompletableFuture()
                    .join();
            return agent;
        } finally {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        }
    }

    private Path createSkill(String skillName, String description) throws IOException {
        Path skillDirectory = tempDir.resolve(skillName);
        Files.createDirectories(skillDirectory);
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
                ---
                name: %s
                description: %s
                ---
                Skill body.
                """.formatted(skillName, description));
        return skillDirectory;
    }

    private static void registerPromptGroups(ReActAgent agent) {
        agent.registerToolGroup(new ToolGroup(
                "development",
                "Development capabilities",
                List.of(new GroupMemberRef(GroupMemberType.SKILL, "GroupedSkill"))));
        agent.registerToolGroup(new ToolGroup(
                "coding",
                "Coding capabilities",
                List.of(new GroupMemberRef(GroupMemberType.SKILL, "CodingSkill"))));
    }

    private static final class MemorySession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public java.util.Iterator<Object> streamIterator() {
            return List.<Object>of().iterator();
        }
    }
}
