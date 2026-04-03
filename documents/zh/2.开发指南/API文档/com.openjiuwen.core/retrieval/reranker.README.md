# reranker

`com.openjiuwen.core.retrieval.reranker` 提供检索结果重排抽象，以及词法重排、远程 `/rerank` 重排和基于 chat completion 的 yes/no 概率重排实现。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`Reranker`](./reranker/Reranker.md) | 重排器统一接口。 |
| [`LexicalReranker`](./reranker/LexicalReranker.md) | 基于 token overlap 的本地重排器。 |
| [`StandardReranker`](./reranker/StandardReranker.md) | 调用远程 `/rerank` 接口的标准重排器。 |
| [`ChatReranker`](./reranker/ChatReranker.md) | 基于 chat completion 与 logprobs 的重排器。 |

## 关键行为

- `Reranker` 同时覆盖“返回重排后的 `RetrievalResult` 列表”和“返回文档标识到分数的映射”两种接口风格。
- `LexicalReranker` 对 query 与候选文本分词后按 overlap 比例评分，适合无需远程模型的轻量重排。
- `StandardReranker` 会把候选统一转换为文本数组，并通过 `ApiRequestUtils.postJsonWithRetry(...)` 调用远端接口。
- `ChatReranker` 针对每个候选单独发起 chat 请求，通过 `yes` 与 `no` token 的概率比计算最终分数。

## 相关测试

- `ChatRerankerTest`
- `LexicalRerankerTest`
- `StandardRerankerTest`
