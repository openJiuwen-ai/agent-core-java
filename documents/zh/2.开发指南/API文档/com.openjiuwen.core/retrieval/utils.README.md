# utils

`com.openjiuwen.core.retrieval.utils` 提供配置读写、检索结果融合、通用去重与带重试的 HTTP JSON POST 工具。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`ApiRequestUtils`](./utils/ApiRequestUtils.md) | 发送带重试逻辑的 HTTP JSON POST 请求。 |
| [`CommonUtils`](./utils/CommonUtils.md) | 通用去重工具。 |
| [`ConfigManager`](./utils/ConfigManager.md) | `KnowledgeBaseConfig` 的加载、保存与更新管理器。 |
| [`FusionUtils`](./utils/FusionUtils.md) | RRF 与加权融合工具。 |

## 关键行为

- `ApiRequestUtils` 提供同步与异步版本的 `postJsonWithRetry(...)`，并允许通过 `StatusCodeCallback` 自定义状态码重试策略。
- `CommonUtils.deduplicate(...)` 会按调用方提供的键函数保留首次出现元素。
- `ConfigManager` 支持从 JSON、YAML、YML 文件读取知识库配置，也可以把当前配置写回文件。
- `FusionUtils` 既能融合 `RetrievalResult`，也能融合 `SearchResult`，并按文本内容去重。

## 相关测试

- `FusionUtilsTest`
- `ConfigManagerTest`
