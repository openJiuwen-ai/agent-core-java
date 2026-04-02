# schema

`com.openjiuwen.core.foundation.llm.schema` defines the message, config, usage, and multimodal response DTOs shared by the Java LLM layer.

## Core Types

| Type | Description |
| --- | --- |
| [`AssistantMessage`](schema/AssistantMessage.md) | See the type page for the Java API surface. |
| [`AssistantMessageChunk`](schema/AssistantMessageChunk.md) | See the type page for the Java API surface. |
| [`AudioGenerationResponse`](schema/AudioGenerationResponse.md) | See the type page for the Java API surface. |
| [`BaseMessage`](schema/BaseMessage.md) | Base message class for LLM conversation messages. |
| [`BaseMessageChunk`](schema/BaseMessageChunk.md) | See the type page for the Java API surface. |
| [`BaseModelInfo`](schema/BaseModelInfo.md) | Base model information — a simplified configuration used by higher-level components. |
| [`GenerationResponse`](schema/GenerationResponse.md) | Base generation response from LLM. |
| [`ImageGenerationResponse`](schema/ImageGenerationResponse.md) | See the type page for the Java API surface. |
| [`MergeUtils`](schema/MergeUtils.md) | Utility class for merging streaming message chunks and parser content. |
| [`ModelClientConfig`](schema/ModelClientConfig.md) | See the type page for the Java API surface. |
| [`ModelConfig`](schema/ModelConfig.md) | Model configuration combining provider info and model info. |
| [`ModelRequestConfig`](schema/ModelRequestConfig.md) | Model request configuration (per-request parameters). |
| [`ProviderType`](schema/ProviderType.md) | Model client provider type enumeration. |
| [`SystemMessage`](schema/SystemMessage.md) | See the type page for the Java API surface. |
| [`ToolCall`](schema/ToolCall.md) | Represents a tool call from LLM output. |
| [`ToolMessage`](schema/ToolMessage.md) | See the type page for the Java API surface. |
| [`ToolMessageChunk`](schema/ToolMessageChunk.md) | See the type page for the Java API surface. |
| [`UsageMetadata`](schema/UsageMetadata.md) | Usage metadata returned by LLM responses. |
| [`UserMessage`](schema/UserMessage.md) | See the type page for the Java API surface. |
| [`VideoGenerationResponse`](schema/VideoGenerationResponse.md) | See the type page for the Java API surface. |

## Notes

- `ModelClientConfigTest` covers provider resolution, default values, and required config fields.
- `MergeUtilsTest` covers recursive string/list/map merges and merge fallback behavior for incompatible values.
