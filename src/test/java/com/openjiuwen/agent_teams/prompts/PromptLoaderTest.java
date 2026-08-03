/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests markdown template loading and caching.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/prompts/loader.py}.</p>
 */
class PromptLoaderTest {

    @BeforeEach
    void clearCache() {
        PromptLoader.clearCacheForTests();
    }

    @Test
    void loadTemplateUsesDefaultChineseLanguageAndCaches() {
        PromptTemplate first = PromptLoader.loadTemplate("leader_policy");
        PromptTemplate second = PromptLoader.loadTemplate("leader_policy");

        assertThat(first).isSameAs(second);
        assertThat(first.getName()).isEqualTo("leader_policy");
        assertThat((String) first.getContent()).isNotBlank();
    }

    @Test
    void loadTemplateUsesExplicitLanguageAsSeparateCacheKey() {
        PromptTemplate cn = PromptLoader.loadTemplate("leader_policy", "cn");
        PromptTemplate en = PromptLoader.loadTemplate("leader_policy", "en");

        assertThat(cn).isSameAs(PromptLoader.loadTemplate("leader_policy", "cn"));
        assertThat(en).isSameAs(PromptLoader.loadTemplate("leader_policy", "en"));
        assertThat(cn).isNotSameAs(en);
        assertThat(cn.getContent()).isNotEqualTo(en.getContent());
    }

    @Test
    void loadSharedTemplateReadsRootMarkdown() {
        PromptTemplate template = PromptLoader.loadSharedTemplate("system_prompt");

        assertThat(template.getName()).isEqualTo("system_prompt");
        assertThat((String) template.getContent()).isNotBlank();
        assertThat(template).isSameAs(PromptLoader.loadSharedTemplate("system_prompt"));
    }

    @Test
    void missingTemplateRaisesUncheckedIo() {
        assertThatThrownBy(() -> PromptLoader.loadTemplate("missing_template"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("missing_template.md");
    }
}
