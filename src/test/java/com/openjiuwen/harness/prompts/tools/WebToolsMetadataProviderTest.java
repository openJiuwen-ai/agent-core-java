/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class WebToolsMetadataProviderTest {

    @SuppressWarnings("unchecked")
    @Test
    void freeSearchProviderMatchesPythonSchema() {
        FreeSearchMetadataProvider provider = new FreeSearchMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(provider.getName()).isEqualTo("free_search");
        assertThat((List<String>) schema.get("required")).containsExactly("query");
        assertThat((Map<String, Object>) properties.get("max_results"))
                .containsEntry("type", "integer")
                .containsEntry("default", 8);
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    @Test
    void paidSearchProviderPreservesTimeoutBounds() {
        PaidSearchMetadataProvider provider = new PaidSearchMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(provider.getName()).isEqualTo("paid_search");
        assertThat((List<String>) schema.get("required")).containsExactly("query");
        assertThat((Map<String, Object>) properties.get("timeout_seconds"))
                .containsEntry("default", 180)
                .containsEntry("minimum", 30)
                .containsEntry("maximum", 300);
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fetchWebpageProviderPreservesClippingDefaults() {
        FetchWebpageMetadataProvider provider = new FetchWebpageMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(provider.getName()).isEqualTo("fetch_webpage");
        assertThat((List<String>) schema.get("required")).containsExactly("url");
        assertThat((Map<String, Object>) properties.get("max_chars"))
                .containsEntry("default", 20000)
                .containsEntry("description", "Maximum content characters. Set to 0 to disable clipping.");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
