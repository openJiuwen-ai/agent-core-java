# llm

`com.openjiuwen.core.foundation.llm` 提供统一的 LLM 调用入口，串联 provider 客户端、输出解析器与请求 / 响应 schema。

## 模块

| 模块 | 说明 |
| --- | --- |
| [`model_clients`](llm/model_clients.README.md) | 收纳各 provider 客户端实现、工厂及默认注册逻辑。 |
| [`output_parsers`](llm/output_parsers.README.md) | 将模型文本输出解析为 JSON 或 Markdown 结构结果。 |
| [`schema`](llm/schema.README.md) | 定义消息、请求配置、使用量统计与多模态响应对象。 |

## 核心类型

| 类型 | 说明 |
| --- | --- |
| [`InferenceAffinityModel`](llm/InferenceAffinityModel.md) | 统一 InferenceAffinity 会话调用入口，向 `InferenceAffinityModelClient` 透传 session 与缓存共享参数。 |
| [`Model`](llm/Model.md) | 统一 LLM 调用入口，按 `clientProvider` 选择 `BaseModelClient` 并转发同步、流式与多模态方法。 |
| [`ModelInvokeOptions`](llm/ModelInvokeOptions.md) | 描述单次 `Model.invoke` / `Model.stream` 的可选参数，包括正式的请求级 transport headers。 |

## 说明

- `ModelFactoryRegistrationTest` 覆盖 provider 工厂注册与选择流程。
- `LlmConnectionExample` 演示基础连通性调用方式。
- `ModelInvokeOptions.requestHeaders` 是请求级数据，不进入模型业务参数；客户端必须实际发送非空 headers 或明确失败，不能静默忽略。
