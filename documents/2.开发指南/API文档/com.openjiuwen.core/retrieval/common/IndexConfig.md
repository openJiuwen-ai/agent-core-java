# com.openjiuwen.core.retrieval.common.IndexConfig

## 类 IndexConfig

```java
public class IndexConfig
```

索引配置对象，定义索引名称与索引模式。

## 说明

- `indexName` 不能为空白。
- `indexType` 默认值为 `"hybrid"`，仅允许 `hybrid`、`bm25`、`vector`。
- setter 会重新触发校验。
