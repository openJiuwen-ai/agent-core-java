/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent.skill;

import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests/system_tests/agent/skill/test_skill_init.py}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent/skill/test_skill_lazy_init.py}.</p>
 */
class SkillInitMissingTest {
    @Test
    void configureCreatesSkillUtilAndLazyInitIsIdempotent() {
        TestableReActAgent agent = new TestableReActAgent();

        agent.configure(makeConfig("sys_1"));
        RecordingSkillUtil skillUtil = agent.createdSkillUtils().get(0);

        assertThat(agent.createdSkillUtils()).containsExactly(skillUtil);
        assertThat(skillUtil.constructedSysOperationId()).isEqualTo("sys_1");

        agent.lazyInitSkill();

        assertThat(agent.createdSkillUtils()).containsExactly(skillUtil);
        assertThat(skillUtil.currentSysOperationId()).isEqualTo("sys_1");
    }

    @Test
    void reconfigureUpdatesSysOperationIdWithoutRecreating() {
        TestableReActAgent agent = new TestableReActAgent();
        agent.configure(makeConfig("sys_old"));
        RecordingSkillUtil skillUtil = agent.createdSkillUtils().get(0);
        skillUtil.clearSetSysOperationIds();

        agent.configure(makeConfig("sys_new"));

        assertThat(agent.createdSkillUtils()).containsExactly(skillUtil);
        assertThat(skillUtil.setSysOperationIds()).containsExactly("sys_new");
        assertThat(skillUtil.currentSysOperationId()).isEqualTo("sys_new");
    }

    @Test
    void registerSkillRaisesWhenSysOperationIdMissing() {
        TestableReActAgent agent = new TestableReActAgent();
        agent.configure(makeConfig(null));

        assertThatThrownBy(() -> agent.registerSkill("/tmp/skills").toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sys_operation_id");

        assertThat(agent.createdSkillUtils()).isEmpty();
    }

    @Test
    void registerSkillTriggersLazyInitAndDelegates() {
        TestableReActAgent agent = new TestableReActAgent();
        agent.configureWithSuppressedSkillCreation(makeConfig("sys_2"));

        Boolean registered = agent.registerSkill("/tmp/skills").toCompletableFuture().join();

        RecordingSkillUtil skillUtil = agent.createdSkillUtils().get(0);
        assertThat(registered).isTrue();
        assertThat(skillUtil.constructedSysOperationId()).isEqualTo("sys_2");
        assertThat(skillUtil.registeredSkillPaths()).containsExactly(List.of("/tmp/skills"));
        assertThat(skillUtil.registeredAgents()).containsExactly(agent);
    }

    private static ReActAgentConfig makeConfig(String sysOperationId) {
        Map<String, Object> systemPrompt = new LinkedHashMap<>();
        systemPrompt.put("role", "system");
        systemPrompt.put("content", "hi");

        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(systemPrompt));
        config.setSysOperationId(sysOperationId);
        return config;
    }

    private static final class TestableReActAgent extends ReActAgent {
        private final List<RecordingSkillUtil> createdSkillUtils = new ArrayList<>();
        private boolean suppressSkillCreation;

        private TestableReActAgent() {
            super(new AgentCard("t", "t", "d"));
        }

        private void configureWithSuppressedSkillCreation(ReActAgentConfig config) {
            suppressSkillCreation = true;
            try {
                configure(config);
            } finally {
                suppressSkillCreation = false;
            }
        }

        private List<RecordingSkillUtil> createdSkillUtils() {
            return createdSkillUtils;
        }

        @Override
        protected SkillUtil createSkillUtil(String sysOperationId) {
            if (suppressSkillCreation) {
                return null;
            }
            RecordingSkillUtil skillUtil = new RecordingSkillUtil(sysOperationId);
            createdSkillUtils.add(skillUtil);
            return skillUtil;
        }
    }

    private static final class RecordingSkillUtil extends SkillUtil {
        private final String constructedSysOperationId;
        private final List<String> setSysOperationIds = new ArrayList<>();
        private final List<List<String>> registeredSkillPaths = new ArrayList<>();
        private final List<Object> registeredAgents = new ArrayList<>();
        private String currentSysOperationId;

        private RecordingSkillUtil(String sysOperationId) {
            super(sysOperationId);
            this.constructedSysOperationId = sysOperationId;
            this.currentSysOperationId = sysOperationId;
        }

        private String constructedSysOperationId() {
            return constructedSysOperationId;
        }

        private String currentSysOperationId() {
            return currentSysOperationId;
        }

        private List<String> setSysOperationIds() {
            return setSysOperationIds;
        }

        private void clearSetSysOperationIds() {
            setSysOperationIds.clear();
        }

        private List<List<String>> registeredSkillPaths() {
            return registeredSkillPaths;
        }

        private List<Object> registeredAgents() {
            return registeredAgents;
        }

        @Override
        public void setSysOperationId(String sysOperationId) {
            currentSysOperationId = sysOperationId;
            setSysOperationIds.add(sysOperationId);
        }

        @Override
        public boolean registerSkills(List<String> skillPaths, Object agent, String sessionId) throws IOException {
            List<String> resolvedPaths = skillPaths == null ? List.of() : List.copyOf(skillPaths);
            registeredSkillPaths.add(resolvedPaths);
            registeredAgents.add(agent);
            return true;
        }
    }
}
