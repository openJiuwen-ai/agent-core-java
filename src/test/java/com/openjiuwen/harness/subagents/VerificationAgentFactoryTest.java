/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.subagent.VerificationRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's verification subagent factory in
 * {@code openjiuwen/harness/subagents/verification_agent.py}.
 */
class VerificationAgentFactoryTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        "DEFAULT_VERIFICATION_AGENT_SYSTEM_PROMPT",
                        "VERIFICATION_AGENT_DESC",
                        "VERIFICATION_AGENT_SYSTEM_PROMPT_CN",
                        "VERIFICATION_AGENT_SYSTEM_PROMPT_EN",
                        "build_verification_agent_config",
                        "create_verification_agent"
                ),
                VerificationAgentFactory.exports()
        );
    }

    @Test
    void buildConfigUsesVerificationDefaults() {
        Object model = new Object();
        DeepAgentConfig.SubAgentConfig spec = VerificationAgentFactory.buildVerificationAgentConfig(model);

        assertEquals("verification_agent", spec.getName());
        assertEquals("cn", spec.getLanguage());
        assertSame(model, spec.getModel());
        assertEquals(40, spec.getMaxIterations());
        assertTrue(spec.getDescription().contains("对抗性验证专家"));
        assertTrue(spec.getSystemPrompt().contains("VERDICT: PASS"));
        assertTrue(spec.getSystemPrompt().contains("执行命令"));
        assertTrue(spec.getRails().stream().anyMatch(SysOperationRail.class::isInstance));
        assertTrue(spec.getRails().stream().anyMatch(VerificationRail.class::isInstance));
        assertEquals("verification_agent", spec.getFactoryName());
    }

    @Test
    void honorsEnglishLanguageAndCustomPrompt() {
        AgentCard card = new AgentCard("verify", "verify", "custom");
        DeepAgentConfig.SubAgentConfig spec = VerificationAgentFactory.buildVerificationAgentConfig(
                "model",
                card,
                "custom prompt",
                null,
                null,
                List.of(new VerificationRail()),
                List.of("pytest"),
                null,
                "workspace",
                null,
                "en",
                "normal",
                true,
                7
        );

        assertSame(card, spec.getCard());
        assertEquals("en", spec.getLanguage());
        assertEquals("custom prompt", spec.getSystemPrompt());
        assertEquals(1, spec.getRails().size());
        assertTrue(spec.isEnableTaskLoop());
        assertEquals(7, spec.getMaxIterations());
        assertTrue(VerificationAgentFactory.defaultDescription("en")
                .contains("Adversarial verification specialist"));
    }

    @Test
    void createVerificationAgentConfiguresDeepAgent() {
        DeepAgent agent = VerificationAgentFactory.createVerificationAgent("model");

        assertEquals("verification_agent", agent.getCard().getName());
        assertEquals(40, agent.deepConfig().getMaxIterations());
        assertTrue(agent.findRailsByType(VerificationRail.class).size() == 1);
        assertTrue(agent.findRailsByType(SysOperationRail.class).size() == 1);
        assertTrue(agent.deepConfig().getSystemPrompt().contains("VERDICT: FAIL"));
    }
}
