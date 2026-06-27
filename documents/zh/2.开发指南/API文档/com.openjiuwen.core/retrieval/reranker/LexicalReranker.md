# com.openjiuwen.core.retrieval.reranker.LexicalReranker

## 类 LexicalReranker

```java
public class LexicalReranker implements Reranker
```

本地词法重排器，通过 query 与候选文本的 token overlap 计算相关性分数。

## 说明

- `candidates` 为空时返回空列表。
- 评分公式基于 overlap 与两侧 token 数量的归一化结果。
- 测试确认：结果会按分数降序排序并截断到 `topK`。
