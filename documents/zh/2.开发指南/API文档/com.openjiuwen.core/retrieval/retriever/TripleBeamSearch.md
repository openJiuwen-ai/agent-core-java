# com.openjiuwen.core.retrieval.retriever.TripleBeamSearch

## 类 TripleBeamSearch

```java
public class TripleBeamSearch
```

`TripleBeamSearch` 用于图检索场景，根据问题与候选三元组 embedding 的相似度执行 beam search 扩展。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public TripleBeamSearch(Retriever retriever)` | 使用默认 `numBeams = 10`、`numCandidatesPerBeam = 100`、`maxLength = 2`。 |
| `public TripleBeamSearch(Retriever retriever, int numBeams, int numCandidatesPerBeam, int maxLength)` | 显式指定 beam 搜索参数。 |

## 公开方法

### `public List<TripleBeam> beamSearch(String query, List<RetrievalResult> triples)`

根据 `query` 与初始 `triples` 生成若干条 `TripleBeam`。

**参数：**

- `query`：输入问题。
- `triples`：初始候选三元组结果；为空时直接返回空列表。

**返回：**

- 按 embedding 相似度排序保留的 beam 列表。

**异常：**

- 构造时 `maxLength < 1` 会抛出模式非法异常。
- 执行时若底层 `Retriever` 不可提供 `Embedding`，会抛出缺少 embedding 模型异常。

## 实现说明

- `beamSearch(...)` 会先对原始三元组做首轮 `topBeams(...)`，再扩展 `maxLength - 1` 轮。
- 候选扩展节点依赖三元组 `metadata.triple` 中的 JSON 字符串，并使用底层 `Retriever` 以 `vector` 模式搜索相邻三元组。
- 解析失败、候选为空或嵌入为空时会回退为空结果或保留现有 beam，而不是抛出解析异常。
