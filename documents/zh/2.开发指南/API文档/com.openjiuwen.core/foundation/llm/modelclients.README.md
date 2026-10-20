# modelclients

`com.openjiuwen.core.foundation.llm.modelclients` 收纳各 provider 客户端实现、工厂及默认注册逻辑。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`BaseModelClient`](modelclients/BaseModelClient.md) | 提供 provider 客户端的公共调用骨架，统一文本、流式与多模态生成接口。 |
| [`DashScopeModelClient`](modelclients/DashScopeModelClient.md) | 面向 DashScope provider 的客户端实现。 |
| [`DashScopeModelClientFactory`](modelclients/DashScopeModelClientFactory.md) | 创建 DashScope 客户端的默认工厂。 |
| [`DefaultModelClientFactories`](modelclients/DefaultModelClientFactories.md) | 集中注册内置 provider 工厂，供 `Model` 的 SPI 注册表使用。 |
| [`OpenAiCompatibleModelClient`](modelclients/OpenAiCompatibleModelClient.md) | 复用 OpenAI Compatible 接口的通用 HTTP 调用逻辑与响应解析流程。 |
| [`OpenAiModelClientFactory`](modelclients/OpenAiModelClientFactory.md) | 创建 OpenAI provider 客户端的默认工厂。 |
| [`OpenRouterModelClientFactory`](modelclients/OpenRouterModelClientFactory.md) | 创建 OpenRouter provider alias 对应客户端的工厂。 |
| [`SiliconFlowModelClientFactory`](modelclients/SiliconFlowModelClientFactory.md) | 创建 SiliconFlow provider 客户端的默认工厂。 |

## 说明

- 内置 provider 工厂通过 `DefaultModelClientFactories` 完成注册。
- `OpenAiCompatibleModelClient` 提供 OpenAI Compatible 请求的共用传输能力。
