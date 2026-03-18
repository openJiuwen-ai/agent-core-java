package com.openjiuwen.core.memory.prompt;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
        String result = PromptApplier.getInstance().apply(
                "ut_prompt_template",
                Map.of("name", "Alice", "place", "Wonderland")
        );

        assertEquals("Hello Alice, welcome to Wonderland!", result);
    }

    @Test
    void applySupportsEmptyVariables() {
        String result = PromptApplier.getInstance().apply("ut_plain_template", Map.of());

        assertEquals("Simple template without variables", result);
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
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PromptApplier.getInstance().getTemplate("does_not_exist")
        );

        assertTrue(error.getMessage().contains("Prompt file not found"));
    }
}
