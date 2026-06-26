/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.IdentitySection;
import com.openjiuwen.harness.prompts.sections.SafetySection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.CallbackContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's security rail tests in
 * {@code tests/unit_tests/harness/test_security_rail.py}.
 */
class SecurityRailPythonParityTest {

    @Test
    void initSetsSystemPromptBuilderReference() {
        SystemPromptBuilder builder = new SystemPromptBuilder();
        SecurityRail rail = new SecurityRail();
        DeepAgent agent = agentWithBuilder(builder);

        rail.init(agent);

        assertSame(builder, rail.getSystemPromptBuilder());
    }

    @Test
    void uninitRemovesSafetySection() {
        SystemPromptBuilder builder = new SystemPromptBuilder();
        builder.addSection(SafetySection.buildSafetySection("cn"));
        SecurityRail rail = new SecurityRail();
        DeepAgent agent = agentWithBuilder(builder);
        rail.init(agent);

        assertTrue(builder.getSection(SectionName.SAFETY).isPresent());

        rail.uninit(agent);

        assertFalse(builder.getSection(SectionName.SAFETY).isPresent());
        assertNull(rail.getSystemPromptBuilder());
    }

    @Test
    void beforeModelCallInjectsSafetySection() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en");
        builder.addSection(IdentitySection.buildIdentitySection("en"));
        SecurityRail rail = new SecurityRail();
        DeepAgent agent = agentWithBuilder(builder);
        rail.init(agent);
        CallbackContext ctx = new CallbackContext(agent, new LinkedHashMap<>());

        rail.beforeModelCall(ctx);

        PromptSection section = builder.getSection(SectionName.SAFETY).orElse(null);
        assertNotNull(section);
        assertTrue(section.render("en").contains("# Safety"));
        assertTrue(builder.build().contains("# Safety"));
        assertSame(section, ctx.get("safety_section"));
    }

    @Test
    void beforeModelCallSkipsWhenBuilderMissing() {
        SecurityRail rail = new SecurityRail();
        CallbackContext ctx = new CallbackContext(new DeepAgent(), new LinkedHashMap<>());

        rail.beforeModelCall(ctx);

        assertNull(rail.getSystemPromptBuilder());
        assertNull(ctx.get("safety_section"));
    }

    @Test
    void languageReadFromBuilderAfterInit() {
        SystemPromptBuilder builder = new SystemPromptBuilder("en");
        SecurityRail rail = new SecurityRail();

        rail.init(agentWithBuilder(builder));

        assertSame(builder, rail.getSystemPromptBuilder());
        assertTrue("en".equals(rail.getSystemPromptBuilder().getLanguage()));
    }

    @Test
    void languageUpdateOnBuilderReflectedImmediately() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        SecurityRail rail = new SecurityRail();
        rail.init(agentWithBuilder(builder));

        builder.setLanguage("en");

        assertTrue("en".equals(rail.getSystemPromptBuilder().getLanguage()));
    }

    @Test
    void allRailsConsistentViaSharedBuilder() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        SecurityRail railA = new SecurityRail();
        SecurityRail railB = new SecurityRail();
        railA.init(agentWithBuilder(builder));
        railB.init(agentWithBuilder(builder));

        builder.setLanguage("en");

        assertTrue("en".equals(railA.getSystemPromptBuilder().getLanguage()));
        assertSame(railA.getSystemPromptBuilder(), railB.getSystemPromptBuilder());
    }

    @Test
    void beforeModelCallUsesUpdatedBuilderLanguage() {
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        SecurityRail rail = new SecurityRail();
        DeepAgent agent = agentWithBuilder(builder);
        rail.init(agent);

        builder.setLanguage("en");
        rail.beforeModelCall(new CallbackContext(agent, new LinkedHashMap<>()));

        PromptSection section = builder.getSection(SectionName.SAFETY).orElse(null);
        assertNotNull(section);
        assertTrue(section.render("en").contains("# Safety"));
    }

    private static DeepAgent agentWithBuilder(SystemPromptBuilder builder) {
        DeepAgent agent = new DeepAgent();
        agent.setReactAgent(new AgentWithBuilder(builder), true);
        return agent;
    }

    private static final class AgentWithBuilder {
        private final SystemPromptBuilder systemPromptBuilder;

        private AgentWithBuilder(SystemPromptBuilder systemPromptBuilder) {
            this.systemPromptBuilder = systemPromptBuilder;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }
    }
}
