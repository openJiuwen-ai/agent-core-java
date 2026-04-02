# model_clients

`com.openjiuwen.core.foundation.llm.model_clients` contains the provider adapters, factory registrations, and the shared OpenAI-compatible transport implementation.

## Core Types

| Type | Description |
| --- | --- |
| [`BaseModelClient`](model_clients/BaseModelClient.md) | LLM Model Client abstract base class. |
| [`DashScopeModelClient`](model_clients/DashScopeModelClient.md) | Alibaba Cloud DashScope Model Client. |
| [`DashScopeModelClientFactory`](model_clients/DashScopeModelClientFactory.md) | Default factory for the DashScope provider. |
| [`DefaultModelClientFactories`](model_clients/DefaultModelClientFactories.md) | Registers the built-in OpenAI-compatible model client factories. |
| [`InferenceAffinityModelClient`](model_clients/InferenceAffinityModelClient.md) | Inference Affinity (vLLM-style) client with cache sharing and release support. |
| [`InferenceAffinityModelClientFactory`](model_clients/InferenceAffinityModelClientFactory.md) | Factory for InferenceAffinity model clients. |
| [`OpenAiCompatibleModelClient`](model_clients/OpenAiCompatibleModelClient.md) | Basic OpenAI-compatible HTTP client used by the built-in providers. |
| [`OpenAiModelClientFactory`](model_clients/OpenAiModelClientFactory.md) | Default factory for the OpenAI provider. |
| [`OpenRouterModelClientFactory`](model_clients/OpenRouterModelClientFactory.md) | Default factory for the OpenRouter provider alias. |
| [`SiliconFlowModelClientFactory`](model_clients/SiliconFlowModelClientFactory.md) | Default factory for the SiliconFlow provider. |

## Notes

- `ModelFactoryRegistrationTest` covers the default provider registry used by the factory classes in this package.
