/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.skill.test_skill_lazy_init}.
 */
class SkillLazyInitTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("configure creates SkillUtil and lazy init is idempotent")
    void testConfigureCreatesSkillUtilAndLazyInitIsIdempotent() {
        ReActAgent agent = newAgent();
        agent.configure(config("sys_1"));

        SkillUtil skillUtil = agent.getSkillUtil();
        assertNotNull(skillUtil);
        assertEquals("sys_1", skillUtil.getSkillManager().getSysOperationId());

        agent.configure(config("sys_1"));
        assertSame(skillUtil, agent.getSkillUtil());
        assertEquals("sys_1", agent.getSkillUtil().getSkillManager().getSysOperationId());
    }

    @Test
    @DisplayName("reconfigure updates sys operation id without recreating")
    void testReconfigureUpdatesSysOperationIdWithoutRecreating() {
        ReActAgent agent = newAgent();
        agent.configure(config("sys_old"));

        SkillUtil skillUtil = agent.getSkillUtil();
        assertNotNull(skillUtil);

        agent.configure(config("sys_new"));
        assertSame(skillUtil, agent.getSkillUtil());
        assertEquals("sys_new", agent.getSkillUtil().getSkillManager().getSysOperationId());
        assertEquals("sys_new", agent.getSkillUtil().getRemoteSkillUtil().getSysOperationId());
    }

    @Test
    @DisplayName("registerSkill requires sys_operation_id")
    void testRegisterSkillRaisesWhenSysOperationIdMissing() {
        ReActAgent agent = newAgent();
        agent.configure(config(null));

        assertThrows(IllegalStateException.class, () -> agent.registerSkill(tempDir.toString()));
    }

    @Test
    @DisplayName("registerSkill initializes and delegates to SkillUtil")
    void testRegisterSkillTriggersLazyInitAndDelegates() throws Exception {
        ReActAgent agent = newAgent();
        agent.configure(config("sys_2"));

        Path skillDir = tempDir.resolve("demo_skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: demo skill
                ---
                # Demo
                """);

        agent.registerSkill(skillDir.toString());

        SkillUtil skillUtil = agent.getSkillUtil();
        assertNotNull(skillUtil);
        assertEquals("sys_2", skillUtil.getSkillManager().getSysOperationId());
        assertEquals(List.of("demo_skill"), skillUtil.getSkillManager().getNames());
    }

    private ReActAgent newAgent() {
        return new ReActAgent(AgentCard.builder().id("t").name("t").description("d").build());
    }

    private ReActAgentConfig config(String sysOperationId) {
        ReActAgentConfig cfg = new ReActAgentConfig();
        cfg.setPromptTemplate(List.of(Map.of("role", "system", "content", "hi")));
        cfg.setSysOperationId(sysOperationId);
        return cfg;
    }
}
