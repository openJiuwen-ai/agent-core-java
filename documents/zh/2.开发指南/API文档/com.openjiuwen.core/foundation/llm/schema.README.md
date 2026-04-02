# schema

`com.openjiuwen.core.foundation.llm.schema` 定义消息、请求配置、使用量统计与多模态响应对象。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`AssistantMessage`](schema/AssistantMessage.md) | 表示 assistant 角色的完整消息对象。 |
| [`AssistantMessageChunk`](schema/AssistantMessageChunk.md) | 表示 assistant 流式输出的消息片段。 |
| [`AudioGenerationResponse`](schema/AudioGenerationResponse.md) | 封装语音生成接口的响应数据。 |
| [`BaseMessage`](schema/BaseMessage.md) | 定义对话消息的公共字段与基础能力。 |
| [`BaseMessageChunk`](schema/BaseMessageChunk.md) | 定义消息片段的公共字段与合并基础。 |
| [`BaseModelInfo`](schema/BaseModelInfo.md) | 描述模型标识与基本元信息。 |
| [`GenerationResponse`](schema/GenerationResponse.md) | 封装文本或通用生成请求的响应数据。 |
| [`ImageGenerationResponse`](schema/ImageGenerationResponse.md) | 封装图像生成接口的响应数据。 |
| [`MergeUtils`](schema/MergeUtils.md) | 提供消息片段或其他可合并对象的拼接辅助逻辑。 |
| [`ModelClientConfig`](schema/ModelClientConfig.md) | 描述 provider、clientId、apiBase、apiKey 等客户端连接配置。 |
| [`ModelConfig`](schema/ModelConfig.md) | 组合 provider 与 model 信息的轻量 record 配置对象。 |
| [`ModelRequestConfig`](schema/ModelRequestConfig.md) | 描述 modelName、temperature、topP、maxTokens 等请求参数。 |
| [`ProviderType`](schema/ProviderType.md) | 声明内置模型 provider 类型枚举。 |
| [`SystemMessage`](schema/SystemMessage.md) | 表示 system 角色的提示消息。 |
| [`ToolCall`](schema/ToolCall.md) | 表示模型输出中的工具调用请求。 |
| [`ToolMessage`](schema/ToolMessage.md) | 表示 tool 角色返回的完整消息。 |
| [`ToolMessageChunk`](schema/ToolMessageChunk.md) | 表示 tool 角色流式输出的消息片段。 |
| [`UsageMetadata`](schema/UsageMetadata.md) | 描述 token 计数与用量统计信息。 |
| [`UserMessage`](schema/UserMessage.md) | 表示 user 角色的输入消息。 |
| [`VideoGenerationResponse`](schema/VideoGenerationResponse.md) | 封装视频生成接口的响应数据。 |

## 说明

- `ModelClientConfigTest` 覆盖 client 配置的 builder 与取值行为。
- `MergeUtilsTest` 覆盖消息块合并逻辑。
