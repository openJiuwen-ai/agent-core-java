/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LspToolMetadataProviderTest {

    @SuppressWarnings("unchecked")
    @Test
    void preservesPythonDescriptionAndSchema() {
        LspToolMetadataProvider provider = new LspToolMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(provider.getName()).isEqualTo("lsp");
        assertThat(provider.getDescription("cn")).contains("Language Server Protocol");
        assertThat(provider.getDescription("en")).contains("workspaceSymbol");
        assertThat((List<String>) schema.get("required")).containsExactly("operation", "file_path");
        assertThat(properties.keySet()).containsExactly(
                "operation",
                "file_path",
                "line",
                "character",
                "query",
                "include_declaration"
        );
        assertThat((Map<String, Object>) properties.get("operation"))
                .containsEntry("type", "string");
        assertThat((List<String>) ((Map<String, Object>) properties.get("operation")).get("enum"))
                .containsExactly(
                        "goToDefinition",
                        "findReferences",
                        "documentSymbol",
                        "workspaceSymbol",
                        "goToImplementation",
                        "prepareCallHierarchy",
                        "incomingCalls",
                        "outgoingCalls"
                );
    }

    @Test
    void validatePassesForLspProvider() {
        LspToolMetadataProvider provider = new LspToolMetadataProvider();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
