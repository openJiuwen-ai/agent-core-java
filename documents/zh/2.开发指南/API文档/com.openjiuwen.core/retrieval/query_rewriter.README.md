# query_rewriter

`com.openjiuwen.core.retrieval.query_rewriter` 提供基于 LLM 的查询改写能力，支持检索结果增强、上下文感知改写、历史压缩、模板加载与 JSON/schema 修复。

## 类型

| 类型 | 说明 |
| --- | --- |
| [`QueryRewriter`](./query_rewriter/QueryRewriter.md) | 查询改写主入口，封装模板加载、LLM 调用、JSON 提取与输出修复。 |

## 关键行为

- `rewrite(String, List<RetrievalResult>)` 会把前 5 条检索结果文本拼接为上下文，要求模型返回包含 `standalone_query` 的 JSON。
- `rewrite(String)` 依赖 `ModelContext`，在历史消息过长时会先执行 `compress(...)`，再加载 `intention_completion` 模板完成改写。
- `loadTemplate(...)` 会从类路径 `prompts/{name}_{promptLang}.md` 读取提示词，并使用内部缓存避免重复加载。
- 当模型输出不是标准 JSON 时，会先抽取 JSON 片段，再做尾逗号修复与 schema 修复。

## 相关测试

- `QueryRewriterTest`
