/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AskUserMetadataProviderTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void askUserMetadataProviderMatchesPythonContract() {
        AskUserMetadataProvider provider = new AskUserMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> questions = castMap(castMap(schema.get("properties")).get("questions"));
        Map<String, Object> questionItem = castMap(questions.get("items"));
        Map<String, Object> options = castMap(castMap(questionItem.get("properties")).get("options"));
        Map<String, Object> optionItem = castMap(options.get("items"));

        assertThat(provider.getName()).isEqualTo("ask_user");
        assertThat(provider.getDescription("en")).contains("Supports 1-4 questions");
        assertThat(provider.getDescription("cn")).contains("preview");
        assertThat(castList(schema.get("required"))).containsExactly("questions");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("questions");
        assertThat(questions).containsEntry("type", "array")
                .containsEntry("minItems", 1)
                .containsEntry("maxItems", 4);
        assertThat(castMap(questionItem.get("properties")).keySet()).containsExactly(
                "header",
                "question",
                "options",
                "multi_select"
        );
        assertThat(castList(questionItem.get("required"))).containsExactly("header", "question", "options");
        assertThat(castMap(optionItem.get("properties")).keySet()).containsExactly("label", "description", "preview");
        assertThat(castList(optionItem.get("required"))).containsExactly("label", "description");
        assertThat(castMap(castMap(questionItem.get("properties")).get("multi_select")))
                .containsEntry("type", "boolean")
                .containsEntry("default", false);
    }

    @Test
    void validatePassesForAskUserProvider() {
        AskUserMetadataProvider provider = new AskUserMetadataProvider();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
