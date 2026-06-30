# LSP Examples Baseline

This directory mirrors the Python `examples/lsp` area with a Java baseline for:

- schema discovery
- workspace-bound `LspTool` invocation
- manager-based diagnostic wiring

Suggested verification:

```bash
mvn -Dtest=LspExampleSupportTest,HarnessLspManagerCompatibilityTest,HarnessLspToolCompatibilityTest test
```
