/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors focused behavior from
 * {@code openjiuwen/core/runner/resources_manager/prompt_manager.py}.
 */
class PromptManagerTest {

    @Test
    void addPromptRejectsNullIdAndTemplate() {
        PromptManager manager = new PromptManager();

        assertThatThrownBy(() -> manager.addPrompt(null, prompt("content")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("template_id is invalid, can not be None");

        assertThatThrownBy(() -> manager.addPrompt("prompt-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("template is invalid, can not be None");
    }

    @Test
    void addPromptStoresAndOverwritesLikePythonDictAssignment() {
        PromptManager manager = new PromptManager();
        PromptTemplate first = prompt("first");
        PromptTemplate second = prompt("second");

        manager.addPrompt("prompt-1", first);
        manager.addPrompt("prompt-1", second);

        assertThat(manager.getPrompt("prompt-1")).isSameAs(second);
    }

    @Test
    void addPromptsIgnoresNullListAndAddsEntriesInOrder() {
        PromptManager manager = new PromptManager();
        PromptTemplate first = prompt("first");
        PromptTemplate second = prompt("second");

        manager.addPrompts(null);
        assertThat(manager.getPrompt("missing")).isNull();

        manager.addPrompts(List.of(
                new PromptManager.PromptEntry("prompt-1", first),
                new PromptManager.PromptEntry("prompt-2", second)
        ));

        assertThat(manager.getPrompt("prompt-1")).isSameAs(first);
        assertThat(manager.getPrompt("prompt-2")).isSameAs(second);
    }

    @Test
    void removePromptReturnsRemovedTemplateOrNull() {
        PromptManager manager = new PromptManager();
        PromptTemplate template = prompt("content");
        manager.addPrompt("prompt-1", template);

        assertThat(manager.removePrompt("prompt-1")).isSameAs(template);
        assertThat(manager.removePrompt("prompt-1")).isNull();
        assertThat(manager.removePrompt(null)).isNull();
    }

    @Test
    void getPromptRejectsNullIdAndReturnsNullWhenMissing() {
        PromptManager manager = new PromptManager();

        assertThat(manager.getPrompt("missing")).isNull();
        assertThatThrownBy(() -> manager.getPrompt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("template_id is invalid, can not be None");
    }

    private static PromptTemplate prompt(String content) {
        return PromptTemplate.builder()
                .name("prompt")
                .content(content)
                .build();
    }
}
