# com.openjiuwen.core.retrieval.query_rewriter.QueryRewriter

## 类 QueryRewriter

```java
public class QueryRewriter
```

查询改写主入口，封装模板加载、LLM 调用、JSON 提取、schema 修复与上下文压缩逻辑。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public QueryRewriter(BaseModelClient llmClient)` | 使用默认 `compressRange = 20` 与 `promptLang = "zh"` 创建实例。 |
| `public QueryRewriter(BaseModelClient llmClient, ModelContext context, int compressRange, String promptLang)` | 指定上下文、压缩阈值与提示词语言。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String rewrite(String query, List<RetrievalResult> results)` | 基于检索结果上下文生成改写查询。 |
| `public Map<String, Object> compress(List<BaseMessage> messages)` | 压缩上下文消息。 |
| `public Map<String, Object> rewrite(String query)` | 基于 `ModelContext` 执行上下文感知改写。 |
| `public String loadTemplate(String promptBase)` | 加载并缓存提示词模板。 |
| `public String msgToText(List<BaseMessage> messages)` | 将消息列表转换为 `role: content` 文本。 |

## 说明

- `rewrite(String, List<RetrievalResult>)` 失败时会回退到本地逻辑：无检索结果则返回原查询，否则在查询后追加第一条结果文本。
- `rewrite(String)` 要求同时提供 `llmClient` 与 `ModelContext`。
- 测试确认：该类能修复带尾逗号的 JSON，并对 `typo` 字段执行 schema 修复。
