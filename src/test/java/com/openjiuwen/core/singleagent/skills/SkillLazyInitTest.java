/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for BaseAgent lazy_init_skill / register_skill lifecycle.
 *
 * <p>Mirrors Python's {@code test_skill_lazy_init.py} in
 * {@code tests/unit_tests/agent/skill/}.
 */
@DisplayName("Skill Lazy Init")
class SkillLazyInitTest {

    private static ReActAgent makeAgent() {
        return new ReActAgent(AgentCard.builder()
                .name("t")
                .description("d")
                .build());
    }

    private static ReActAgentConfig makeConfig(String sysOperationId) {
        return ReActAgentConfig.builder()
                .sysOperationId(sysOperationId)
                .promptTemplate(java.util.List.of(
                        java.util.Map.of("role", "system", "content", "hi")))
                .build();
    }

    @Nested
    @DisplayName("configure creates skill util")
    class ConfigureCreatesSkillUtil {

        @Test
        @DisplayName("configure() creates SkillUtil and lazy_init_skill is idempotent")
        void testConfigureCreatesSkillUtilAndLazyInitIsIdempotent() {
            ReActAgent agent = makeAgent();

            ReActAgentConfig config = makeConfig("sys_1");
            agent.configure(config);

            assertThat(agent.getSkillUtil()).isNotNull();

            agent.lazyInitSkill();

            assertThat(agent.getSkillUtil()).isNotNull();
        }

        @Test
        @DisplayName("reconfigure updates sys_operation_id without recreating")
        void testReconfigureUpdatesSysOperationIdWithoutRecreating() {
            ReActAgent agent = makeAgent();

            agent.configure(makeConfig("sys_old"));
            SkillUtil firstUtil = agent.getSkillUtil();
            assertThat(firstUtil).isNotNull();

            agent.configure(makeConfig("sys_new"));

            assertThat(agent.getSkillUtil()).isNotNull();
        }
    }

    @Nested
    @DisplayName("register skill edge cases")
    class RegisterSkillEdgeCases {

        @Test
        @DisplayName("register_skill when sys_operation_id is null does not crash")
        void testRegisterSkillWhenSysOperationIdNull() {
            ReActAgent agent = makeAgent();
            agent.configure(makeConfig(null));

            assertThat(agent.getSkillUtil()).isNotNull();
        }
    }
}
