
package com.openjiuwen.core.memory.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PromptApplierTest {
    @AfterEach
    void clearCache() {
        PromptApplier.getInstance().clearCache();
    }

    @Test
    void singletonInitializationReturnsSameInstance() {
        assertSame(PromptApplier.getInstance(), PromptApplier.getInstance());
    }

    @Test
    void applySubstitutesVariables() {
        String result =
            PromptApplier.getInstance().apply("ut_prompt_template", Map.of("name", "Alice", "place", "Wonderland"));

        assertEquals("Hello Alice, welcome to Wonderland!", result);
    }

    @Test
    void applySupportsEmptyVariables() {
        String result = PromptApplier.getInstance().apply("ut_plain_template", Map.of());

        assertEquals("Simple template without variables", result);
    }

    @Test
    void memoryAnalysisPromptContainsForbiddenVariablesContract() {
        String template =
            String.valueOf(PromptApplier.getInstance().getTemplate("memory_analysis_prompt").getContent());

        assertTrue(template.contains("禁止记忆变量保护设置"));
        assertTrue(template.contains("仅严禁提取明确列出的禁止记忆变量"));
        assertTrue(template.contains("{{forbidden_variables}}"));
    }

    @Test
    void productionPromptResourcesKeepPythonContracts() {
        String fragment =
            String.valueOf(PromptApplier.getInstance().getTemplate("fragment_memory_prompt").getContent());
        String update = String.valueOf(PromptApplier.getInstance().getTemplate("memory_update_check").getContent());

        assertTrue(fragment.contains("用户本人画像信息(user_profile)"));
        assertTrue(fragment.contains("情景记忆信息(episodic_memory)"));
        assertTrue(fragment.contains("语义记忆信息(semantic_memory)"));
        assertTrue(update.contains("按正序遍历未丢弃的新信息"));
        assertTrue(update.contains("**丢弃**所有与当前新信息冗余或冲突的信息"));
    }

    @Test
    void getTemplateCachesUntilClearCache() {
        PromptApplier applier = PromptApplier.getInstance();

        PromptTemplate first = applier.getTemplate("ut_prompt_template");
        PromptTemplate second = applier.getTemplate("ut_prompt_template");
        assertSame(first, second);

        applier.clearCache();

        PromptTemplate third = applier.getTemplate("ut_prompt_template");
        assertNotSame(first, third);
    }

    @Test
    void getTemplateReturnsPromptTemplate() {
        PromptTemplate template = PromptApplier.getInstance().getTemplate("ut_prompt_template");

        assertTrue(template instanceof PromptTemplate);
    }

    @Test
    void missingTemplateThrowsHelpfulError() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PromptApplier.getInstance().getTemplate("does_not_exist"));

        assertTrue(error.getMessage().contains("Prompt file not found"));
    }
}
