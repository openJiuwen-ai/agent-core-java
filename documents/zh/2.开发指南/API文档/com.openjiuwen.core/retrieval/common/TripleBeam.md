# com.openjiuwen.core.retrieval.common.TripleBeam

## 类 TripleBeam

```java
public class TripleBeam implements Iterable<RetrievalResult>
```

三元组 beam 搜索状态容器，保存一条 beam 中的结果列表与累计分数。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public RetrievalResult get(int index)` | 返回指定位置的结果。 |
| `public int size()` | 返回当前 beam 大小。 |
| `public boolean contains(RetrievalResult triple)` | 按 `text` 判断是否已包含某条结果。 |
| `public List<RetrievalResult> getTriples()` | 返回 beam 内结果副本。 |
| `public double getScore()` | 返回累计分数。 |
