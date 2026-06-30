# Harness Tools Baseline

The Java tools baseline currently exposes:

- `ToolOutput`
- `TodoItem`
- `TodoStatus`
- `TodoTool`
- `SessionTaskRow`
- `SessionToolkit`
- `TaskTool`
- `SessionsListTool`
- `SessionsCancelTool`
- `SwitchModeTool`
- `FilesystemTool`
- `MemoryTools`
- `ListSkillTool`
- `SkillTool`
- `CodeTool`
- `WebFetchWebpageTool`
- `WebFreeSearchTool`
- `WebPaidSearchTool`
- `ListMcpResourcesTool`
- `ReadMcpResourceTool`
- `AskUserTool`
- `SearchToolsTool`
- `LoadToolsTool`
- `CronTool`
- `BashTool`
- `PowerShellTool`
- `ImageOCRTool`
- `VisualQuestionAnsweringTool`
- `AudioTranscriptionTool`
- `AudioQuestionAnsweringTool`
- `AudioMetadataTool`
- `LspTool`
- `BrowserAgentRuntime`
- `BrowserActionController`
- `HarnessCli`
- `CLIOptions`
- `SessionStore`

This first layer gives Java a local todo/session toolkit, a minimal subagent delegation surface,
basic mode switching, filesystem/memory/skill utilities, code/web/mcp helpers, meta tools, shell tooling, multimodal helpers, LSP helpers, and a simple CLI-facing runner surface.
