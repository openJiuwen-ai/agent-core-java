package com.openjiuwen.harness.harness_config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessConfigTest {

    @Test
    void defaultsMatchPythonSchema() {
        HarnessConfig config = new HarnessConfig();

        assertThat(config.getSchemaVersion()).isEqualTo("harness_config.v0.1");
        assertThat(config.getLanguage()).isEqualTo("cn");
        assertThat(config.getExtraFields()).isEmpty();
    }

    @Test
    void toYamlUsesAliasesAndWritesFile() throws Exception {
        HarnessConfig config = HarnessConfig.builder()
                .name("demo")
                .workspace(HarnessConfig.WorkspaceSchema.builder().build())
                .resources(HarnessConfig.ResourcesSchema.builder()
                        .tools(List.of(HarnessConfig.ToolResourceSchema.builder()
                                .type("entry_point")
                                .name("demo")
                                .className("com.demo.Tool")
                                .module("demo.module")
                                .build()))
                        .mcps(List.of(HarnessConfig.McpResourceSchema.builder()
                                .command("python")
                                .args(List.of("-m", "demo"))
                                .env(Map.of("A", "B"))
                                .build()))
                        .build())
                .build();

        Path output = Files.createTempFile("harness-config", ".yaml");
        String yaml = config.toYaml(output);

        assertThat(yaml).contains("schema_version: harness_config.v0.1");
        assertThat(yaml).contains("root_path: ./");
        assertThat(yaml).contains("class: com.demo.Tool");
        assertThat(yaml).doesNotContain("className:");
        assertThat(Files.readString(output)).isEqualTo(yaml);
    }
}
