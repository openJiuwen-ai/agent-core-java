/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.prompts.DeepAgentPromptBuilder;
import com.openjiuwen.harness.prompts.sections.IdentitySection;
import com.openjiuwen.harness.prompts.sections.SafetySection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.test_security_rail}.
 */
class SecurityRailTest {

    @Test
    void testInitSetsSystemPromptBuilderReference() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder();
        com.openjiuwen.harness.rails.SecurityRail rail = new com.openjiuwen.harness.rails.SecurityRail();
        AgentStub agent = new AgentStub(builder);

        rail.init(agent);

        assertSame(builder, rail.getSystemPromptBuilder());
    }

    @Test
    void testUninitRemovesSafetySection() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder();
        builder.addSection(SafetySection.build());
        com.openjiuwen.harness.rails.SecurityRail rail = new com.openjiuwen.harness.rails.SecurityRail();
        AgentStub agent = new AgentStub(builder);
        rail.init(agent);

        assertTrue(builder.getSection(SectionName.SAFETY).isPresent());

        rail.uninit(agent);

        assertTrue(builder.getSection(SectionName.SAFETY).isEmpty());
        assertNull(rail.getSystemPromptBuilder());
    }

    @Test
    void testBeforeModelCallInjectsSafetySection() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder(
                "en",
                DeepAgentPromptBuilder.PromptMode.FULL);
        builder.addSection(IdentitySection.build());
        com.openjiuwen.harness.rails.SecurityRail rail = new com.openjiuwen.harness.rails.SecurityRail();
        AgentStub agent = new AgentStub(builder);
        rail.init(agent);

        rail.beforeModelCall(callbackContext(agent));

        assertTrue(builder.getSection(SectionName.SAFETY).isPresent());
        assertTrue(builder.getSection(SectionName.SAFETY).orElseThrow().render("en").contains("# Safety"));
        assertTrue(builder.build().contains("# Safety"));
    }

    @Test
    void testBeforeModelCallSkipsWhenBuilderMissing() {
        com.openjiuwen.harness.rails.SecurityRail rail = new com.openjiuwen.harness.rails.SecurityRail();
        Object agent = new Object();
        rail.init(agent);

        rail.beforeModelCall(callbackContext(agent));

        assertNull(rail.getSystemPromptBuilder());
    }

    @Test
    void testLanguageReadFromBuilderAfterInit() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder(
                "en",
                DeepAgentPromptBuilder.PromptMode.FULL);
        com.openjiuwen.harness.rails.SecurityRail rail = new com.openjiuwen.harness.rails.SecurityRail();

        rail.init(new AgentStub(builder));

        assertEquals("en", ((DeepAgentPromptBuilder) rail.getSystemPromptBuilder()).getLanguage());
    }

    @Test
    void testLanguageUpdateOnBuilderReflectedImmediately() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder(
                "cn",
                DeepAgentPromptBuilder.PromptMode.FULL);
        com.openjiuwen.harness.rails.SecurityRail rail = new com.openjiuwen.harness.rails.SecurityRail();
        rail.init(new AgentStub(builder));

        builder.setLanguage("en");

        assertEquals("en", ((DeepAgentPromptBuilder) rail.getSystemPromptBuilder()).getLanguage());
    }

    @Test
    void testAllRailsConsistentViaSharedBuilder() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder(
                "cn",
                DeepAgentPromptBuilder.PromptMode.FULL);
        com.openjiuwen.harness.rails.SecurityRail railA = new com.openjiuwen.harness.rails.SecurityRail();
        com.openjiuwen.harness.rails.SecurityRail railB = new com.openjiuwen.harness.rails.SecurityRail();
        railA.init(new AgentStub(builder));
        railB.init(new AgentStub(builder));

        builder.setLanguage("en");

        assertEquals(
                ((DeepAgentPromptBuilder) railA.getSystemPromptBuilder()).getLanguage(),
                ((DeepAgentPromptBuilder) railB.getSystemPromptBuilder()).getLanguage());
        assertEquals("en", ((DeepAgentPromptBuilder) railA.getSystemPromptBuilder()).getLanguage());
    }

    @Test
    void testBeforeModelCallUsesUpdatedBuilderLanguage() {
        DeepAgentPromptBuilder builder = new DeepAgentPromptBuilder(
                "cn",
                DeepAgentPromptBuilder.PromptMode.FULL);
        com.openjiuwen.harness.rails.SecurityRail rail = new com.openjiuwen.harness.rails.SecurityRail();
        AgentStub agent = new AgentStub(builder);
        rail.init(agent);

        builder.setLanguage("en");
        rail.beforeModelCall(callbackContext(agent));

        assertTrue(builder.getSection(SectionName.SAFETY).isPresent());
        assertTrue(builder.getSection(SectionName.SAFETY).orElseThrow().render("en").contains("# Safety"));
        assertTrue(builder.build().contains("# Safety"));
    }

    private static AgentCallbackContext callbackContext(Object agent) {
        return AgentCallbackContext.builder()
                .agent(agent)
                .build();
    }

    private static final class AgentStub {
        private final DeepAgentPromptBuilder systemPromptBuilder;

        private AgentStub(DeepAgentPromptBuilder systemPromptBuilder) {
            this.systemPromptBuilder = systemPromptBuilder;
        }

        public DeepAgentPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }
    }
}
