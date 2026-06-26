/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's progressive-tool-rail helper behavior in
 * {@code openjiuwen/harness/prompts/sections/progressive_tool_rail.py}.
 */
class ProgressiveToolRailSectionTest {

    @Test
    void buildNavigationPromptFallsBackToEmptyEntry() {
        String prompt = ProgressiveToolRailSection.buildNavigationPrompt(List.of(), "en");

        assertTrue(prompt.contains("## Tool Navigation"));
        assertTrue(prompt.contains("(no navigation entries available)"));
    }

    @Test
    void buildNavigationPromptJoinsRenderedEntries() {
        String prompt = ProgressiveToolRailSection.buildNavigationPrompt(List.of("one", "", "two"), "en");

        assertTrue(prompt.contains("one\ntwo"));
    }

    @Test
    void buildProgressiveToolRulesPromptPreservesLoadToolsSequence() {
        String prompt = ProgressiveToolRailSection.buildProgressiveToolRulesPrompt("en");

        assertTrue(prompt.contains("call `search_tools` first"));
        assertTrue(prompt.contains("call `load_tools` immediately"));
        assertTrue(prompt.contains("navigate first, search second"));
    }

    @Test
    void buildNavigationSectionUsesToolNavigationNameAndPriority() {
        PromptSection section = ProgressiveToolRailSection.buildNavigationSection(List.of("entry"), "en");

        assertEquals(SectionName.TOOL_NAVIGATION, section.getName());
        assertEquals(70, section.getPriority());
        assertTrue(section.render("en").contains("entry"));
    }

    @Test
    void buildProgressiveToolRulesSectionUsesRulesNameAndPriority() {
        PromptSection section = ProgressiveToolRailSection.buildProgressiveToolRulesSection("en");

        assertEquals(SectionName.PROGRESSIVE_TOOL_RULES, section.getName());
        assertEquals(75, section.getPriority());
        assertTrue(section.render("en").contains("Progressive Tool Usage Rules"));
    }

    @Test
    void buildNavigationEntrySupportsEnglishAndChineseFormatting() {
        String en = ProgressiveToolRailSection.buildNavigationEntry("tool", "group", "ready", "summary", "en");
        String cn = ProgressiveToolRailSection.buildNavigationEntry("tool", "group", "ready", "summary", "cn");

        assertEquals("- tool [group, ready]: summary", en);
        assertEquals("- tool [group, ready]锛歿summary}", cn);
    }

    @Test
    void buildMultilingualSectionsPopulateBothLanguages() {
        PromptSection navigation = ProgressiveToolRailSection.buildMultilingualNavigationSection(List.of("cn-entry"), List.of("en-entry"));
        PromptSection rules = ProgressiveToolRailSection.buildMultilingualProgressiveToolRulesSection();

        assertTrue(navigation.getContent().containsKey("cn"));
        assertTrue(navigation.getContent().containsKey("en"));
        assertTrue(rules.getContent().get("en").contains("load third, execute last"));
    }
}
