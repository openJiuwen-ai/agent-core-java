# llm

`com.openjiuwen.core.foundation.llm` provides the top-level Java entry points for provider-backed model calls and inference-affinity sessions.

## Modules

| Module | Description |
| --- | --- |
| [`model_clients`](llm/model_clients.README.md) | `com.openjiuwen.core.foundation.llm.model_clients` contains the provider adapters, factory registrations, and the shared OpenAI-compatible transport implementation. |
| [`output_parsers`](llm/output_parsers.README.md) | `com.openjiuwen.core.foundation.llm.output_parsers` converts raw assistant output into structured JSON or Markdown representations. |
| [`schema`](llm/schema.README.md) | `com.openjiuwen.core.foundation.llm.schema` defines the message, config, usage, and multimodal response DTOs shared by the Java LLM layer. |

## Core Types

| Type | Description |
| --- | --- |
| [`InferenceAffinityModel`](llm/InferenceAffinityModel.md) | Unified entry point for InferenceAffinity (vLLM-style) invocation. |
| [`Model`](llm/Model.md) | Unified LLM invocation entry point. |

## Notes

- `ModelFactoryRegistrationTest` confirms that the built-in provider factories register `OpenAI`, `OpenRouter`, `SiliconFlow`, and `DashScope` by default.
- `LlmConnectionExample` shows end-to-end `invoke(...)` and `stream(...)` usage against an OpenAI-compatible endpoint.
