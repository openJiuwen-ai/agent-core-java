package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.LspExampleSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LspExampleSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExposeSchemaAndSupportedOperations() {
        Map<String, Object> schema = LspExampleSupport.buildToolSchema();
        List<String> operations = LspExampleSupport.supportedOperations();

        assertThat(schema.get("name")).isEqualTo("lsp");
        assertThat(operations).contains("goToDefinition", "findReferences", "outgoingCalls");
    }

    @Test
    void shouldBuildManagerAndRunDefinitionDemo() {
        var manager = LspExampleSupport.newManager(tempDir);
        Map<String, Object> result = LspExampleSupport.runDefinitionDemo(tempDir, "src/Main.java");

        assertThat(manager.getWorkspaceRoot()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
        assertThat(result).containsEntry("operation", "goToDefinition");
        assertThat(String.valueOf(result.get("file_path"))).contains("src/Main.java");
    }
}
