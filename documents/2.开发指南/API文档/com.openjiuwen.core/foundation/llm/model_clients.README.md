# model_clients

`com.openjiuwen.core.foundation.llm.model_clients` 收纳各 provider 客户端实现、工厂及默认注册逻辑。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`BaseModelClient`](model_clients/BaseModelClient.md) | 提供 provider 客户端的公共调用骨架，统一文本、流式与多模态生成接口。 |
| [`DashScopeModelClient`](model_clients/DashScopeModelClient.md) | 面向 DashScope provider 的客户端实现。 |
| [`DashScopeModelClientFactory`](model_clients/DashScopeModelClientFactory.md) | 创建 DashScope 客户端的默认工厂。 |
| [`DefaultModelClientFactories`](model_clients/DefaultModelClientFactories.md) | 集中注册内置 provider 工厂，供 `Model` 的 SPI 注册表使用。 |
| [`InferenceAffinityModelClient`](model_clients/InferenceAffinityModelClient.md) | 实现 InferenceAffinity 风格的对话客户端，支持 cache sharing 与 release 能力。 |
| [`InferenceAffinityModelClientFactory`](model_clients/InferenceAffinityModelClientFactory.md) | 创建 `InferenceAffinityModelClient` 实例的工厂。 |
| [`OpenAiCompatibleModelClient`](model_clients/OpenAiCompatibleModelClient.md) | 复用 OpenAI Compatible 接口的通用 HTTP 调用逻辑与响应解析流程。 |
| [`OpenAiModelClientFactory`](model_clients/OpenAiModelClientFactory.md) | 创建 OpenAI provider 客户端的默认工厂。 |
| [`OpenRouterModelClientFactory`](model_clients/OpenRouterModelClientFactory.md) | 创建 OpenRouter provider alias 对应客户端的工厂。 |
| [`SiliconFlowModelClientFactory`](model_clients/SiliconFlowModelClientFactory.md) | 创建 SiliconFlow provider 客户端的默认工厂。 |

## 说明

- 内置 provider 工厂通过 `DefaultModelClientFactories` 完成注册。
- `OpenAiCompatibleModelClient` 提供 OpenAI Compatible 请求的共用传输能力。
