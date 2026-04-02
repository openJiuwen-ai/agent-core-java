# com.openjiuwen.core.retrieval.retriever.AgenticRetriever

## 类 AgenticRetriever

```java
public class AgenticRetriever extends AbstractRetriever
```

`AgenticRetriever` 在基础检索器之上增加 LLM 驱动的事实抽取、查询重写和多轮结果融合能力。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgenticRetriever(Retriever retriever, BaseModelClient llmClient)` | 使用默认 `maxIter = 2` 创建实例。 |
| `public AgenticRetriever(Retriever retriever, BaseModelClient llmClient, int maxIter)` | 指定最大迭代轮数；传入非正数时会回退到 `2`。 |

## 公开方法

### `public boolean isGraphRetriever()`

返回底层 `retriever` 是否为 `GraphRetriever` 实例。

### `public String getDefaultMode()`

根据底层 `retriever.getIndexType()` 推导默认模式：`vector -> vector`、`bm25 -> sparse`，其他索引类型使用 `hybrid`。

### `public String getIndexType()`

委托给底层 `retriever.getIndexType()`。

### `public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options)`

执行代理式检索；当底层为图检索器时走图扩展路径，否则走通用多轮检索路径。

**参数：**

- `query`：原始问题。
- `topK`：最终返回条数，必须大于 `0`。
- `scoreThreshold`：向下透传给底层检索器的阈值。
- `mode`：显式模式；为 `null` 时使用 `getDefaultMode()`。
- `options`：附加配置；图模式会读取 `graph_expansion`。

**返回：**

- 经多轮读取、改写与融合后的结果列表。

**异常：**

- 构造阶段缺少 `retriever` 或 `llmClient` 时抛出 retrieval 领域异常。
- `topK <= 0` 时抛出 `RETRIEVAL_RETRIEVER_TOP_K_NOT_FOUND` 对应异常。

### `public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options)`

逐条调用 `retrieve(...)`，返回每个问题的代理式检索结果。

### `public void close()`

关闭底层 `retriever`，并吞掉关闭过程中的异常。

## 实现说明

- 图路径会先检索 chunk，再通过 LLM 抽取三元组、链接相关 triple/passages，最后使用 `FusionUtils.rrfFusionRetrieval(...)` 融合。
- 通用路径会在每轮检索后调用 LLM 读事实并尝试生成下一轮问题，最多执行 `maxIter` 轮。
- LLM 响应解析失败时，内部逻辑会回退为空结果或终止重写，不会向外抛出解析异常。
