# embedding

`com.openjiuwen.core.retrieval.embedding` 提供 embedding 模型统一抽象、本地哈希向量实现，以及面向通用 HTTP、OpenAI 兼容接口和 vLLM 多模态输入的远程 embedding 客户端。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`Embedding`](./embedding/Embedding.md) | embedding 能力统一接口。 |
| [`EmbeddingUtils`](./embedding/EmbeddingUtils.md) | embedding 解析辅助工具。 |
| [`HashEmbedding`](./embedding/HashEmbedding.md) | 基于 SHA-256 的本地确定性 embedding。 |
| [`APIEmbedding`](./embedding/APIEmbedding.md) | 通用 HTTP embedding 客户端。 |
| [`OpenAIEmbedding`](./embedding/OpenAIEmbedding.md) | OpenAI 兼容 embedding 客户端。 |
| [`VLLMEmbedding`](./embedding/VLLMEmbedding.md) | 支持多模态 `extra_body.messages` 的 vLLM embedding 客户端。 |

## 关键行为

- `Embedding` 统一定义查询向量化、批量文档向量化、维度查询与批次上限接口。
- `HashEmbedding` 适合测试或无远程模型时使用，输出稳定且不依赖网络。
- `APIEmbedding` 支持重试、批量并发、回调通知与多种响应格式解析。
- `OpenAIEmbedding` 在 `baseUrl` 尾部存在 `/embeddings` 时会自动规范化为基础地址，并支持 base64 float32 向量响应。
- `VLLMEmbedding` 会把 `MultimodalDocument` 转换为 `extra_body.messages` 结构，再复用 OpenAI 兼容请求路径执行向量化。

## 相关测试

- `APIEmbeddingTest`
- `EmbeddingUtilsTest`
- `HashEmbeddingTest`
- `OpenAIEmbeddingTest`
- `VLLMEmbeddingTest`
