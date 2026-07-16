
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.lsp.core.LSPServerManager;
import com.openjiuwen.harness.tools.lsp.LspInputs;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class HarnessLspToolCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void lspSchemaAndOperationMappingShouldExposeEightOperations() {
        Map<String, Object> schema = LspToolSupport.buildLspTool();
        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) schema.get("input_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> operation = (Map<String, Object>) properties.get("operation");
        @SuppressWarnings("unchecked")
        List<String> operationValues = ((List<?>) operation.get("enum")).stream().map(String::valueOf).toList();

        assertThat(schema.get("name")).isEqualTo("lsp");
        assertThat(operationValues).hasSize(8).contains("goToDefinition", "outgoingCalls");
        assertThat(LspToolSupport.operationToMethod(LspOperation.GO_TO_DEFINITION))
                .isEqualTo("textDocument/definition");
        assertThat(LspToolSupport.operationToMethod(LspOperation.OUTGOING_CALLS))
                .isEqualTo("callHierarchy/outgoingCalls");
    }

    @Test
    void lspInputsAndFlagsShouldMatchExpectedDefaults() {
        LspInputs.GoToDefinitionInput go = new LspInputs.GoToDefinitionInput("/tmp/file.py", 10, 5);
        LspInputs.FindReferencesInput refs = new LspInputs.FindReferencesInput("/tmp/file.py", 5, 10);
        LspInputs.WorkspaceSymbolInput ws = new LspInputs.WorkspaceSymbolInput("my_function");

        assertThat(go.operation()).isEqualTo(LspOperation.GO_TO_DEFINITION);
        assertThat(refs.includeDeclaration()).isTrue();
        assertThat(ws.operation()).isEqualTo(LspOperation.WORKSPACE_SYMBOL);
        assertThat(LspToolSupport.needsGitignoreFilter(LspOperation.FIND_REFERENCES)).isTrue();
        assertThat(LspToolSupport.needsGitignoreFilter(LspOperation.DOCUMENT_SYMBOL)).isFalse();
    }

    @Test
    void lspFormatterShouldFormatLocationsAndSymbols() {
        String location = LspToolSupport.formatLocation(Map.of("uri", "file:///path/with%20space/file.py", "range",
                Map.of("start", Map.of("line", 4, "character", 2))));
        String definition = LspToolSupport.formatGoToDefinition(
                Map.of("uri", "file:///path/to/file.py", "range", Map.of("start", Map.of("line", 0, "character", 0))));
        String refs = LspToolSupport.formatFindReferences(
                List.of(Map.of("uri", "file:///a.py", "range", Map.of("start", Map.of("line", 0, "character", 0))),
                        Map.of("uri", "file:///b.py", "range", Map.of("start", Map.of("line", 2, "character", 0)))));
        String docSymbols = LspToolSupport.formatDocumentSymbol(List.of(Map.of("name", "MyClass", "kind", 5, "children",
                List.of(Map.of("name", "my_method", "kind", 6, "children", List.of())))));

        assertThat(location).contains("/path/with space/file.py").contains(":5:3");
        assertThat(definition).contains("Defined in").contains("/path/to/file.py");
        assertThat(refs).contains("/a.py").contains("/b.py");
        assertThat(docSymbols).contains("Class: MyClass").contains("Method: my_method");
    }

    @Test
    void lspToolShouldResolveWorkspaceRelativePaths() {
        LspTool tool = new LspTool(tempDir.toString());

        ToolOutput output = tool.invoke(Map.of("operation", "goToDefinition", "file_path", "src/Main.java"));

        assertThat(output.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) output.getData();
        assertThat(String.valueOf(payload.get("file_path")))
                .isEqualTo(tempDir.resolve("src/Main.java").normalize().toString());
        assertThat(payload.get("method")).isEqualTo("textDocument/definition");
    }

    @Test
    void lspToolShouldDispatchRequestsToRegisteredServer() throws Exception {
        Path file = tempDir.resolve("src/Main.java");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "class Main {}");
        LSPServerManager manager = new LSPServerManager();
        HarnessLspManagerCompatibilityTest.FakeServer server =
            new HarnessLspManagerCompatibilityTest.FakeServer("jdtls");
        server.requestResult =
            Map.of("uri", file.toUri().toString(), "range", Map.of("start", Map.of("line", 0, "character", 6)));
        manager.registerServer(file.toString(), server);
        LspTool tool = new LspTool(tempDir.toString(), manager);

        ToolOutput output =
            tool.invoke(Map.of("operation", "goToDefinition", "file_path", "src/Main.java", "line", 1, "character", 7));

        assertThat(output.isSuccess()).isTrue();
        assertThat(server.requests).hasSize(1);
        assertThat(server.requests.get(0).method()).isEqualTo("textDocument/definition");
        Map<?, ?> position = (Map<?, ?>) server.requests.get(0).params().get("position");
        assertThat(position.get("line")).isEqualTo(0);
        assertThat(position.get("character")).isEqualTo(6);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) output.getData();
        assertThat(payload.get("raw_result")).isEqualTo(server.requestResult);
        assertThat(String.valueOf(payload.get("formatted"))).contains("Defined in").contains("Main.java:1:7");
    }
}
