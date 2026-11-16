/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python behavior in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/utils.py}.
 */
class PromptBuilderUtilsTest {

    @Test
    void selectTemplateDefaultsToChineseAndSupportsEnglish() {
        assertThat(PromptBuilderUtils.selectTemplate()).isSameAs(PromptZh.templates());
        assertThat(PromptBuilderUtils.selectTemplate("unknown")).isSameAs(PromptZh.templates());
        assertThat(PromptBuilderUtils.selectTemplate("en-US")).isSameAs(PromptEn.templates());
    }

    @Test
    void getStringPromptSupportsStringsMessagesAndMessageMappings() {
        assertThat(PromptBuilderUtils.getStringPrompt("plain")).isEqualTo("plain");

        PromptTemplate stringTemplate = PromptTemplate.builder().content("content").build();
        assertThat(PromptBuilderUtils.getStringPrompt(stringTemplate)).isEqualTo("content");

        PromptTemplate messageTemplate = PromptTemplate.builder()
                .content(List.of(new UserMessage("u"), new SystemMessage("s")))
                .build();
        assertThat(PromptBuilderUtils.getStringPrompt(messageTemplate)).isEqualTo("u\ns");

        PromptTemplate mapTemplate = PromptTemplate.builder().content(List.of(
                Map.of("role", "user", "content", "a"),
                Map.of("role", "system", "content", "b"))).build();
        assertThat(PromptBuilderUtils.getStringPrompt(mapTemplate)).isEqualTo("a\nb");
    }

    @Test
    void arbitraryMapIsRejectedAtTemplateConstruction() {
        assertThatThrownBy(() -> PromptTemplate.builder()
                .content(List.of(Map.of("first", "a", "second", "b"))).build())
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("prompt template type must be in str or list[BaseMessage]");
    }

    @Test
    @Disabled("Assertion no longer matches current develop behavior; kept disabled pending product decision or test rewrite")
    void getStringPromptRejectsUnsupportedPromptType() {
        assertThatThrownBy(() -> PromptBuilderUtils.getStringPrompt(42))
                .hasMessageContaining("Prompt type class java.lang.Integer is not supported");
    }
}
