/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill Creator tester.
 *
 * <p>Mirrors Python's {@code test_skill_creator_mock.py} in
 * {@code tests/unit_tests/agent/skill/}.
 *
 * <p>Real LLM tests are skipped unless RUN_REAL_LLM_TESTS=1.
 */
@DisplayName("Skill Creator")
class SkillCreatorMockTest {

    static class MockFs {
        private final List<String> directories = new ArrayList<>();
        private final Map<String, String> files = new LinkedHashMap<>();

        void addDirectory(String dirName) {
            directories.add(dirName);
        }

        void addFile(String filePath, String fileContents) {
            files.put(filePath, fileContents);
        }
    }

    static class MockReActAgent extends ReActAgent {
        private final MockFs fs = new MockFs();

        MockReActAgent() {
            super(AgentCard.builder().build());
        }

        void invoke() {
            fs.addDirectory("skill_name");
            fs.addFile(
                    "skill_name/SKILL.md",
                    "---\nname: skill_name\ndescription: sample skill\n---\n# Skill Body\n"
            );
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            invoke();
            return Map.of("output", "mock skill created");
        }
    }

    @Test
    @DisplayName("skill creation with real LLM")
    void testSkillCreationRealLlm(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_REAL_LLM_TESTS")),
                "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable."
        );

        SkillCreator skillCreator = new SkillCreator();
        skillCreator.createAgent().join();
        skillCreator.generate("Create a skeleton skill directory nammed 'skill_name'", tempDir).join();

        Path skillDir = tempDir.resolve("skill_name");
        assertThat(Files.isDirectory(skillDir)).isTrue();

        Path skillFile = skillDir.resolve("SKILL.md");
        assertThat(skillFile).exists();
        assertThat(Files.readString(skillFile))
                .containsPattern("(?s)^---.*name: skill_name.*---.*$")
                .containsPattern("(?s)^---.*description: .*---.*$");
    }

    @Test
    @DisplayName("skill creation with mock LLM")
    void testSkillCreationMockLlm() {
        SkillCreator skillCreator = new SkillCreator();
        MockReActAgent mockAgent = new MockReActAgent();
        skillCreator.setAgent(mockAgent);

        assertThat(skillCreator.getAgent()).isSameAs(mockAgent);
        mockAgent.invoke();

        assertThat(mockAgent.fs.directories).contains("skill_name");
        assertThat(mockAgent.fs.files.keySet()).contains("skill_name/SKILL.md");

        String skillFileContents = mockAgent.fs.files.get("skill_name/SKILL.md");
        assertThat(skillFileContents)
                .containsPattern("(?s)^---.*name: skill_name.*---.*$")
                .containsPattern("(?s)^---.*description: .*---.*$");
    }
}
